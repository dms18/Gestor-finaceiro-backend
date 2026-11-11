package br.com.financeiro.service;

import br.com.financeiro.dto.AdminInfraDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Mede o uso do servidor e dos containers.
 *
 * <p>Servidor: lê /proc (meminfo, loadavg, uptime, stat) e o disco da raiz. Dentro de um container Linux,
 * esses arquivos refletem o servidor inteiro (não o limite do container).
 *
 * <p>Containers: consulta o "porteiro" do Docker (nginx somente-leitura, ver deploy/dockerstats), que só
 * repassa a lista e as estatísticas. A API nunca recebe o socket do Docker.
 */
@ApplicationScoped
public class InfraService {

    private static final Logger LOG = Logger.getLogger(InfraService.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(8);

    @ConfigProperty(name = "docker.stats.url")
    Optional<String> dockerStatsUrl;

    @Inject
    ObjectMapper mapper;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public AdminInfraDTO coletar() {
        AdminInfraDTO dto = new AdminInfraDTO();
        coletarVps(dto.vps);
        coletarContainers(dto);
        return dto;
    }

    // ---------------------------------------------------------------- servidor

    private void coletarVps(AdminInfraDTO.Vps vps) {
        long[] cpu1 = lerCpu();
        try {
            lerMemoria(vps);
            lerLoad(vps);
            lerUptime(vps);
        } catch (Exception e) {
            LOG.debug("Não foi possível ler /proc", e);
        }
        File raiz = new File("/");
        long total = raiz.getTotalSpace();
        if (total > 0) {
            vps.discoTotalBytes = total;
            vps.discoUsadoBytes = total - raiz.getFreeSpace();
        }
        vps.cpus = contarCpus();
        // segunda amostra de CPU depois de uma janela curta
        if (cpu1 != null) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            long[] cpu2 = lerCpu();
            if (cpu2 != null && cpu2[1] > cpu1[1]) {
                double ocioso = cpu2[0] - cpu1[0];
                double total2 = cpu2[1] - cpu1[1];
                vps.cpuPercentual = arredondar(100.0 * (1.0 - ocioso / total2));
            }
        }
    }

    /** @return {ocioso, total} em "jiffies" da linha "cpu " de /proc/stat, ou null se indisponível. */
    private long[] lerCpu() {
        try {
            for (String linha : Files.readAllLines(Path.of("/proc/stat"))) {
                if (linha.startsWith("cpu ")) {
                    String[] p = linha.trim().split("\\s+");
                    long total = 0;
                    for (int i = 1; i < p.length; i++) {
                        total += Long.parseLong(p[i]);
                    }
                    long ocioso = Long.parseLong(p[4]) + (p.length > 5 ? Long.parseLong(p[5]) : 0); // idle + iowait
                    return new long[] {ocioso, total};
                }
            }
        } catch (Exception e) {
            // fora de Linux
        }
        return null;
    }

    private Integer contarCpus() {
        try {
            int n = 0;
            for (String linha : Files.readAllLines(Path.of("/proc/stat"))) {
                if (linha.matches("cpu\\d+ .*")) {
                    n++;
                }
            }
            return n > 0 ? n : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void lerMemoria(AdminInfraDTO.Vps vps) throws Exception {
        long total = 0;
        long disponivel = 0;
        long swapTotal = 0;
        long swapLivre = 0;
        boolean achou = false;
        for (String linha : Files.readAllLines(Path.of("/proc/meminfo"))) {
            String[] p = linha.split("\\s+");
            if (p.length < 2) {
                continue;
            }
            long kb = Long.parseLong(p[1]);
            switch (p[0]) {
                case "MemTotal:" -> { total = kb * 1024; achou = true; }
                case "MemAvailable:" -> disponivel = kb * 1024;
                case "SwapTotal:" -> swapTotal = kb * 1024;
                case "SwapFree:" -> swapLivre = kb * 1024;
                default -> { }
            }
        }
        if (achou) {
            vps.memoriaTotalBytes = total;
            vps.memoriaUsadaBytes = total - disponivel;
            vps.swapTotalBytes = swapTotal;
            vps.swapUsadaBytes = swapTotal - swapLivre;
        }
    }

    private void lerLoad(AdminInfraDTO.Vps vps) throws Exception {
        String[] p = Files.readString(Path.of("/proc/loadavg")).trim().split("\\s+");
        vps.load1 = Double.parseDouble(p[0]);
        vps.load5 = Double.parseDouble(p[1]);
        vps.load15 = Double.parseDouble(p[2]);
    }

    private void lerUptime(AdminInfraDTO.Vps vps) throws Exception {
        String[] p = Files.readString(Path.of("/proc/uptime")).trim().split("\\s+");
        vps.uptimeSegundos = (long) Double.parseDouble(p[0]);
    }

    // -------------------------------------------------------------- containers

    private void coletarContainers(AdminInfraDTO dto) {
        String base = dockerStatsUrl.map(String::trim).orElse("");
        if (base.isEmpty()) {
            dto.containersErro = "Consulta ao Docker não configurada neste ambiente.";
            return;
        }
        try {
            JsonNode lista = obter(base + "/containers/json");
            List<CompletableFuture<AdminInfraDTO.Conteiner>> pendentes = new ArrayList<>();
            for (JsonNode c : lista) {
                pendentes.add(CompletableFuture.supplyAsync(() -> montarContainer(base, c)));
            }
            List<AdminInfraDTO.Conteiner> resultado = new ArrayList<>();
            for (CompletableFuture<AdminInfraDTO.Conteiner> f : pendentes) {
                resultado.add(f.get(TIMEOUT.toSeconds() + 2, TimeUnit.SECONDS));
            }
            resultado.sort(Comparator.comparing(c -> c.nome == null ? "" : c.nome));
            dto.containers = resultado;
            dto.containersDisponiveis = true;
        } catch (Exception e) {
            LOG.warn("Falha ao consultar as estatísticas dos containers: " + e);
            dto.containersErro = "Não foi possível consultar o Docker.";
        }
    }

    private AdminInfraDTO.Conteiner montarContainer(String base, JsonNode c) {
        AdminInfraDTO.Conteiner r = new AdminInfraDTO.Conteiner();
        String id = c.path("Id").asText();
        r.id = id.length() > 12 ? id.substring(0, 12) : id;
        String servico = c.path("Labels").path("com.docker.compose.service").asText("");
        String nome = c.path("Names").isArray() && c.path("Names").size() > 0 ? c.path("Names").get(0).asText() : id;
        r.nome = servico.isEmpty() ? nome.replaceFirst("^/", "") : servico;
        r.imagem = c.path("Image").asText(null);
        r.estado = c.path("State").asText(null);
        r.situacao = c.path("Status").asText(null);
        try {
            preencherEstatisticas(r, obter(base + "/containers/" + id + "/stats?stream=false"));
        } catch (Exception e) {
            LOG.debug("Sem estatísticas para " + r.nome, e);
        }
        return r;
    }

    private void preencherEstatisticas(AdminInfraDTO.Conteiner r, JsonNode s) {
        // CPU: variação do uso do container sobre a variação do uso do servidor, vezes o número de CPUs
        JsonNode cpu = s.path("cpu_stats");
        JsonNode pre = s.path("precpu_stats");
        double dCont = cpu.path("cpu_usage").path("total_usage").asDouble() - pre.path("cpu_usage").path("total_usage").asDouble();
        double dSist = cpu.path("system_cpu_usage").asDouble() - pre.path("system_cpu_usage").asDouble();
        int online = cpu.path("online_cpus").asInt(1);
        if (dSist > 0 && dCont >= 0) {
            r.cpuPercentual = arredondar(dCont / dSist * online * 100.0);
        }

        // Memória: uso sem o cache de arquivos (como faz o "docker stats")
        JsonNode mem = s.path("memory_stats");
        if (mem.has("usage")) {
            long uso = mem.path("usage").asLong();
            JsonNode st = mem.path("stats");
            long cache = st.has("inactive_file") ? st.path("inactive_file").asLong() : st.path("cache").asLong();
            r.memoriaUsadaBytes = Math.max(0, uso - cache);
            if (mem.has("limit")) {
                r.memoriaLimiteBytes = mem.path("limit").asLong();
            }
        }

        // Rede: soma de todas as interfaces
        JsonNode redes = s.path("networks");
        if (redes.isObject()) {
            long rx = 0;
            long tx = 0;
            for (JsonNode n : redes) {
                rx += n.path("rx_bytes").asLong();
                tx += n.path("tx_bytes").asLong();
            }
            r.redeRxBytes = rx;
            r.redeTxBytes = tx;
        }

        // Disco (leitura/escrita acumuladas)
        JsonNode io = s.path("blkio_stats").path("io_service_bytes_recursive");
        if (io.isArray()) {
            long leitura = 0;
            long escrita = 0;
            for (JsonNode e : io) {
                String op = e.path("op").asText("").toLowerCase();
                if (op.equals("read")) {
                    leitura += e.path("value").asLong();
                } else if (op.equals("write")) {
                    escrita += e.path("value").asLong();
                }
            }
            r.discoLeituraBytes = leitura;
            r.discoEscritaBytes = escrita;
        }
    }

    private JsonNode obter(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + resp.statusCode() + " em " + url);
        }
        return mapper.readTree(resp.body());
    }

    private static double arredondar(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
