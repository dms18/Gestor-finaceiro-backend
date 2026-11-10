package br.com.financeiro.resource;

import br.com.financeiro.dto.ReservaSazonalDTO;
import br.com.financeiro.dto.SazonalidadeDTO;
import br.com.financeiro.entity.Conta;
import br.com.financeiro.entity.Sazonalidade;
import br.com.financeiro.entity.Transacao;
import br.com.financeiro.entity.Usuario;
import br.com.financeiro.security.UserPrincipal;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Path("/sazonalidade")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class SazonalidadeResource {

    @Inject
    UserPrincipal userPrincipal;

    @GET
    public Response listar() {
        Long usuarioId = userPrincipal.getUserId();
        if (usuarioId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        List<Sazonalidade> sazonalidades = Sazonalidade.list("usuario.id", usuarioId);
        List<SazonalidadeDTO> dtos = sazonalidades.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return Response.ok(dtos).build();
    }

    @GET
    @Path("/alertas-ativos")
    public Response alertasAtivos() {
        Long usuarioId = userPrincipal.getUserId();
        if (usuarioId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        int mesAtual = YearMonth.now().getMonthValue();

        List<Sazonalidade> sazonalidades = Sazonalidade.list("usuario.id", usuarioId);
        List<SazonalidadeDTO> alertas = sazonalidades.stream()
                .filter(s -> s.estaAtivaNoMes(mesAtual))
                .map(this::toDTO)
                .collect(Collectors.toList());

        return Response.ok(alertas).build();
    }

    /**
     * Reservas de caixa cuja data-alvo já foi alcançada e cujo período ainda não terminou:
     * compara o saldo atual da conta de débito com o caixa necessário.
     */
    @GET
    @Path("/reservas")
    public Response reservas() {
        Long usuarioId = userPrincipal.getUserId();
        if (usuarioId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        LocalDate hoje = LocalDate.now();
        List<Sazonalidade> sazonalidades = Sazonalidade.list("usuario.id", usuarioId);
        List<ReservaSazonalDTO> reservas = sazonalidades.stream()
                .filter(s -> s.reservaAtiva(hoje))
                .map(s -> toReserva(s, hoje))
                .sorted(Comparator.comparing((ReservaSazonalDTO r) -> r.dataAlvo))
                .collect(Collectors.toList());

        return Response.ok(reservas).build();
    }

    @POST
    @Transactional
    public Response criar(@Valid SazonalidadeDTO dto) {
        Long usuarioId = userPrincipal.getUserId();
        if (usuarioId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Usuario usuario = Usuario.findById(usuarioId);
        if (usuario == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Sazonalidade sazonalidade = new Sazonalidade();
        sazonalidade.usuario = usuario;
        Response erro = aplicar(sazonalidade, dto, usuarioId);
        if (erro != null) {
            return erro;
        }
        sazonalidade.persist();

        return Response.status(Response.Status.CREATED).entity(toDTO(sazonalidade)).build();
    }

    @PUT
    @Transactional
    @Path("/{id}")
    public Response atualizar(@PathParam("id") Long id, @Valid SazonalidadeDTO dto) {
        Long usuarioId = userPrincipal.getUserId();
        if (usuarioId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Sazonalidade sazonalidade = Sazonalidade.findById(id);
        if (sazonalidade == null || !sazonalidade.usuario.id.equals(usuarioId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Response erro = aplicar(sazonalidade, dto, usuarioId);
        if (erro != null) {
            return erro;
        }
        sazonalidade.persist();

        return Response.ok(toDTO(sazonalidade)).build();
    }

    @DELETE
    @Transactional
    @Path("/{id}")
    public Response deletar(@PathParam("id") Long id) {
        Long usuarioId = userPrincipal.getUserId();
        if (usuarioId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Sazonalidade sazonalidade = Sazonalidade.findById(id);
        if (sazonalidade == null || !sazonalidade.usuario.id.equals(usuarioId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        sazonalidade.delete();
        return Response.noContent().build();
    }

    /**
     * Copia os campos do DTO para a entidade. Devolve uma resposta de erro (400) se a reserva de
     * caixa estiver incompleta ou a conta não for do usuário; null se estiver tudo certo.
     */
    private Response aplicar(Sazonalidade s, SazonalidadeDTO dto, Long usuarioId) {
        int informados = (dto.dataAlvo != null ? 1 : 0)
                + (dto.valorNecessario != null ? 1 : 0)
                + (dto.contaDebitoId != null ? 1 : 0);
        if (informados != 0 && informados != 3) {
            return erro("Informe data, caixa necessário e conta para débito juntos (ou deixe os três em branco)");
        }

        Conta conta = null;
        if (dto.contaDebitoId != null) {
            conta = Conta.findById(dto.contaDebitoId);
            // conta de outro usuário é tratada como inexistente (evita enumerar contas alheias)
            if (conta == null || !conta.usuario.id.equals(usuarioId)) {
                return erro("Conta para débito inválida");
            }
        }

        s.nome = dto.nome;
        s.mesInicio = dto.mesInicio;
        s.mesFim = dto.mesFim;
        s.descricao = dto.descricao;
        s.nivelImpacto = Sazonalidade.NivelImpacto.valueOf(dto.nivelImpacto);
        s.dataAlvo = dto.dataAlvo;
        s.valorNecessario = dto.valorNecessario;
        s.contaDebito = conta;
        return null;
    }

    private static Response erro(String mensagem) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity("{\"message\":\"" + mensagem + "\"}")
                .build();
    }

    private SazonalidadeDTO toDTO(Sazonalidade s) {
        SazonalidadeDTO dto = new SazonalidadeDTO(s.id, s.nome, s.mesInicio, s.mesFim, s.descricao,
                s.nivelImpacto.toString(), s.usuario.id);
        dto.dataAlvo = s.dataAlvo;
        dto.valorNecessario = s.valorNecessario;
        if (s.contaDebito != null) {
            dto.contaDebitoId = s.contaDebito.id;
            dto.contaDebitoNome = s.contaDebito.nome;
        }
        return dto;
    }

    private ReservaSazonalDTO toReserva(Sazonalidade s, LocalDate hoje) {
        BigDecimal saldo = saldoAtual(s.contaDebito, hoje);
        BigDecimal diferenca = saldo.subtract(s.valorNecessario);

        ReservaSazonalDTO r = new ReservaSazonalDTO();
        r.sazonalidadeId = s.id;
        r.nome = s.nome;
        r.nivelImpacto = s.nivelImpacto.toString();
        r.dataAlvo = s.dataAlvo;
        r.fimDoPeriodo = s.fimDoPeriodo();
        r.valorNecessario = s.valorNecessario;
        r.contaId = s.contaDebito.id;
        r.contaNome = s.contaDebito.nome;
        r.saldoConta = saldo;
        r.diferenca = diferenca;
        r.suficiente = diferenca.signum() >= 0;
        // valorNecessario é sempre > 0 (validado no DTO), então a divisão é segura
        BigDecimal cobertura = saldo.max(BigDecimal.ZERO)
                .multiply(BigDecimal.valueOf(100))
                .divide(s.valorNecessario, 0, RoundingMode.DOWN);
        r.percentualCobertura = cobertura.min(BigDecimal.valueOf(100)).intValue();
        return r;
    }

    /** Saldo inicial da conta + receitas − despesas lançadas nela até hoje (futuros não contam). */
    private BigDecimal saldoAtual(Conta conta, LocalDate hoje) {
        BigDecimal saldo = conta.saldo;
        List<Transacao> movimentos = Transacao.list("conta.id = ?1 and data <= ?2", conta.id, hoje);
        for (Transacao t : movimentos) {
            saldo = t.tipo == Transacao.TipoTransacao.RECEITA ? saldo.add(t.valor) : saldo.subtract(t.valor);
        }
        return saldo;
    }
}
