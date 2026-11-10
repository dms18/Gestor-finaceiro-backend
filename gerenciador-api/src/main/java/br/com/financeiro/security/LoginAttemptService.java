package br.com.financeiro.security;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limita tentativas de login para dificultar força bruta sem deixar um terceiro trancar a conta alheia.
 *
 * <p>Dois contadores independentes, ambos em memória (uma instância):
 * <ul>
 *   <li><b>por e-mail + IP</b>: após {@value #MAX_FALHAS} falhas, aquele IP fica bloqueado para
 *       aquele e-mail por {@value #BLOQUEIO_MINUTOS} minutos. Um atacante em outro IP não bloqueia o
 *       dono da conta;</li>
 *   <li><b>por e-mail (todos os IPs)</b>: após {@value #MAX_FALHAS_POR_EMAIL} falhas, o e-mail é bloqueado
 *       por {@value #BLOQUEIO_MINUTOS} minutos. Barra força bruta distribuída; negar o login do dono
 *       passa a exigir um volume de requisições bem maior.</li>
 * </ul>
 * Vale também para e-mails inexistentes, para não revelar quais contas existem.
 */
@ApplicationScoped
public class LoginAttemptService {

    static final int MAX_FALHAS = 5;
    static final int MAX_FALHAS_POR_EMAIL = 25;
    static final long BLOQUEIO_MINUTOS = 5;
    private static final int MAX_CHAVES = 10_000;

    private static final class Registro {
        int falhas;
        Instant primeiraFalha = Instant.now();
        Instant bloqueadoAte;
    }

    private final ConcurrentHashMap<String, Registro> porEmailEIp = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Registro> porEmail = new ConcurrentHashMap<>();

    /** @return segundos restantes de bloqueio, ou 0 se o login pode ser tentado. */
    public long segundosBloqueado(String email, String ip) {
        return Math.max(
                restante(porEmailEIp, chaveEmailIp(email, ip)),
                restante(porEmail, chaveEmail(email)));
    }

    public void registrarFalha(String email, String ip) {
        if (porEmailEIp.size() > MAX_CHAVES || porEmail.size() > MAX_CHAVES) {
            limparExpirados(porEmailEIp);
            limparExpirados(porEmail);
        }
        falhar(porEmailEIp, chaveEmailIp(email, ip), MAX_FALHAS);
        falhar(porEmail, chaveEmail(email), MAX_FALHAS_POR_EMAIL);
    }

    /** Login correto: o dono provou quem é, então zera os contadores daquele e-mail. */
    public void registrarSucesso(String email, String ip) {
        porEmailEIp.remove(chaveEmailIp(email, ip));
        porEmail.remove(chaveEmail(email));
    }

    /** Redefinição de senha concluída: libera o e-mail em todos os IPs. */
    public void liberarEmail(String email) {
        String prefixo = chaveEmail(email) + "|";
        porEmailEIp.keySet().removeIf(k -> k.startsWith(prefixo));
        porEmail.remove(chaveEmail(email));
    }

    private static long restante(ConcurrentHashMap<String, Registro> mapa, String chave) {
        Registro r = mapa.get(chave);
        if (r == null) {
            return 0;
        }
        synchronized (r) {
            if (r.bloqueadoAte == null) {
                return 0;
            }
            long restante = Duration.between(Instant.now(), r.bloqueadoAte).getSeconds();
            if (restante <= 0) {
                mapa.remove(chave, r);
                return 0;
            }
            return restante;
        }
    }

    private static void falhar(ConcurrentHashMap<String, Registro> mapa, String chave, int limite) {
        Registro r = mapa.computeIfAbsent(chave, k -> new Registro());
        synchronized (r) {
            Instant agora = Instant.now();
            if (Duration.between(r.primeiraFalha, agora).toMinutes() >= BLOQUEIO_MINUTOS) {
                r.falhas = 0;
                r.primeiraFalha = agora;
            }
            r.falhas++;
            if (r.falhas >= limite) {
                r.bloqueadoAte = agora.plus(Duration.ofMinutes(BLOQUEIO_MINUTOS));
            }
        }
    }

    private static void limparExpirados(ConcurrentHashMap<String, Registro> mapa) {
        Instant limite = Instant.now().minus(Duration.ofMinutes(BLOQUEIO_MINUTOS));
        mapa.entrySet().removeIf(e -> e.getValue().primeiraFalha.isBefore(limite)
                && (e.getValue().bloqueadoAte == null || e.getValue().bloqueadoAte.isBefore(Instant.now())));
    }

    private static String chaveEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String chaveEmailIp(String email, String ip) {
        return chaveEmail(email) + "|" + (ip == null ? "" : ip);
    }
}
