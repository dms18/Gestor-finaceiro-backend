package br.com.financeiro.resource;

import br.com.financeiro.dto.AdminResumoDTO;
import br.com.financeiro.entity.Categoria;
import br.com.financeiro.entity.Conta;
import br.com.financeiro.entity.Investimento;
import br.com.financeiro.entity.Sazonalidade;
import br.com.financeiro.entity.Transacao;
import br.com.financeiro.entity.Usuario;
import br.com.financeiro.security.UserPrincipal;
import br.com.financeiro.service.InfraService;
import io.vertx.core.http.HttpServerRequest;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Painel do administrador. A permissão é conferida NO SERVIDOR a cada chamada, lendo a flag ic_adm do
 * banco (não vale confiar no que o app mostra nem em dados do token).
 *
 * <p>Só a versão web pode usar: o app instalado (WebView do Capacitor) chama a API a partir da origem
 * {@code http://localhost}, e requisições com essa origem são recusadas. É uma segunda trava; a primeira
 * é o próprio app não exibir o painel.
 */
@Path("/admin")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AdminResource {

    /** Origens do app nativo (Capacitor Android/iOS). */
    private static final Set<String> ORIGENS_APP = Set.of(
            "http://localhost", "https://localhost", "capacitor://localhost", "ionic://localhost");

    private static final int DIAS_GRAFICO = 30;
    private static final int USUARIOS_RECENTES = 20;

    @Inject
    UserPrincipal userPrincipal;

    @Inject
    InfraService infraService;

    @Context
    HttpServerRequest httpRequest;

    @GET
    @Path("/resumo")
    public Response resumo() {
        Response negado = negarSeNaoAdministrador();
        return negado != null ? negado : Response.ok(montarResumo()).build();
    }

    /** Uso do servidor (VPS) e de cada container. */
    @GET
    @Path("/infra")
    public Response infra() {
        Response negado = negarSeNaoAdministrador();
        return negado != null ? negado : Response.ok(infraService.coletar()).build();
    }

    /** null = pode seguir; senão a resposta de erro (401 sem login, 403 se não é admin ou veio do app). */
    private Response negarSeNaoAdministrador() {
        Long id = userPrincipal.getUserId();
        if (id == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        String origem = httpRequest == null ? null : httpRequest.getHeader("Origin");
        boolean doApp = origem != null && ORIGENS_APP.contains(origem.toLowerCase());
        Usuario solicitante = Usuario.findById(id);
        if (doApp || solicitante == null || !Boolean.TRUE.equals(solicitante.icAdm)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("{\"message\":\"Acesso restrito\"}")
                    .build();
        }
        return null;
    }

    private AdminResumoDTO montarResumo() {
        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime h7 = agora.minusDays(7);
        LocalDateTime h30 = agora.minusDays(DIAS_GRAFICO);

        AdminResumoDTO r = new AdminResumoDTO();

        r.usuarios.total = Usuario.count();
        r.usuarios.ativos = Usuario.count("ativo", true);
        r.usuarios.inativos = r.usuarios.total - r.usuarios.ativos;
        r.usuarios.novos7Dias = Usuario.count("criadoEm >= ?1", h7);
        r.usuarios.novos30Dias = Usuario.count("criadoEm >= ?1", h30);
        r.usuarios.aceitaramTermos = Usuario.count("aceitouTermos", true);
        r.usuarios.porPerfil = new LinkedHashMap<>();
        for (Usuario.TipoPerfil p : Usuario.TipoPerfil.values()) {
            r.usuarios.porPerfil.put(p.name(), Usuario.count("perfil", p));
        }

        r.atividade.transacoes = Transacao.count();
        r.atividade.transacoes30Dias = Transacao.count("criadaEm >= ?1", h30);
        r.atividade.contas = Conta.count();
        r.atividade.categorias = Categoria.count();
        r.atividade.sazonalidades = Sazonalidade.count();
        r.atividade.investimentos = Investimento.count();
        r.atividade.investimentosPorStatus = new LinkedHashMap<>();
        for (Investimento.StatusInvestimento s : Investimento.StatusInvestimento.values()) {
            r.atividade.investimentosPorStatus.put(s.name(), Investimento.count("status", s));
        }
        r.atividade.usuariosAtivos30Dias = Transacao.getEntityManager()
                .createQuery("select count(distinct t.usuario.id) from Transacao t where t.criadaEm >= ?1", Long.class)
                .setParameter(1, h30)
                .getSingleResult();

        // cadastros por dia: todos os dias da janela aparecem, mesmo com zero
        LocalDate hoje = agora.toLocalDate();
        Map<LocalDate, Long> porDia = new TreeMap<>();
        for (int i = DIAS_GRAFICO - 1; i >= 0; i--) {
            porDia.put(hoje.minusDays(i), 0L);
        }
        List<Usuario> cadastrados = Usuario.list("criadoEm >= ?1", h30);
        for (Usuario u : cadastrados) {
            if (u.criadoEm != null) {
                porDia.computeIfPresent(u.criadoEm.toLocalDate(), (dia, qtd) -> qtd + 1);
            }
        }
        r.cadastrosPorDia = new ArrayList<>();
        porDia.forEach((dia, qtd) -> r.cadastrosPorDia.add(new AdminResumoDTO.CadastroDia(dia.toString(), qtd)));

        r.usuariosRecentes = new ArrayList<>();
        List<Usuario> ultimos = Usuario.find("order by id desc").page(0, USUARIOS_RECENTES).list();
        for (Usuario u : ultimos) {
            AdminResumoDTO.UsuarioRecente ur = new AdminResumoDTO.UsuarioRecente();
            ur.id = u.id;
            ur.nome = u.nome;
            ur.email = u.email;
            ur.perfil = u.perfil == null ? null : u.perfil.name();
            ur.ativo = u.ativo;
            ur.admin = Boolean.TRUE.equals(u.icAdm);
            ur.criadoEm = u.criadoEm;
            ur.transacoes = Transacao.count("usuario.id", u.id);
            r.usuariosRecentes.add(ur);
        }
        return r;
    }
}
