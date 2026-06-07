package br.com.financeiro.resource;

import br.com.financeiro.dto.ResumoFinanceiroDTO;
import br.com.financeiro.dto.TransacaoDTO;
import br.com.financeiro.entity.*;
import br.com.financeiro.security.UserPrincipal;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;

@Path("/transacoes")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Transactional
public class TransacaoResource {

    @Inject
    UserPrincipal userPrincipal;

    @GET
    public Response listar(@QueryParam("tipoPessoa") String tipoPessoa,
                          @QueryParam("mes") Integer mes,
                          @QueryParam("ano") Integer ano) {
        Long usuarioId = userPrincipal.getUserId();
        if (usuarioId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        List<Transacao> transacoes = Transacao.list("usuario.id", usuarioId);

        if (tipoPessoa != null) {
            transacoes = transacoes.stream()
                    .filter(t -> t.tipoPessoa.toString().equals(tipoPessoa))
                    .collect(Collectors.toList());
        }

        if (mes != null && ano != null) {
            transacoes = transacoes.stream()
                    .filter(t -> t.data.getMonthValue() == mes && t.data.getYear() == ano)
                    .collect(Collectors.toList());
        }

        List<TransacaoDTO> dtos = transacoes.stream()
                .map(t -> new TransacaoDTO(t.id, t.descricao, t.valor, t.data, t.tipo.toString(),
                        t.tipoPessoa.toString(), t.categoria != null ? t.categoria.id : null,
                        t.conta != null ? t.conta.id : null, t.usuario.id))
                .collect(Collectors.toList());

        return Response.ok(dtos).build();
    }

    @GET
    @Path("/resumo")
    public Response resumo(@QueryParam("tipoPessoa") String tipoPessoa,
                          @QueryParam("mes") Integer mes,
                          @QueryParam("ano") Integer ano) {
        Long usuarioId = userPrincipal.getUserId();
        if (usuarioId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        ResumoFinanceiroDTO resumo = new ResumoFinanceiroDTO();

        List<Transacao> transacoes = Transacao.list("usuario.id", usuarioId);

        if (tipoPessoa != null) {
            transacoes = transacoes.stream()
                    .filter(t -> t.tipoPessoa.toString().equals(tipoPessoa))
                    .collect(Collectors.toList());
        }

        if (mes != null && ano != null) {
            transacoes = transacoes.stream()
                    .filter(t -> t.data.getMonthValue() == mes && t.data.getYear() == ano)
                    .collect(Collectors.toList());
        }

        for (Transacao t : transacoes) {
            if (t.tipo == Transacao.TipoTransacao.RECEITA) {
                resumo.totalReceitas = resumo.totalReceitas.add(t.valor);
            } else {
                resumo.totalDespesas = resumo.totalDespesas.add(t.valor);
            }
        }

        resumo.saldo = resumo.totalReceitas.subtract(resumo.totalDespesas);

        List<Conta> contas = Conta.list("usuario.id", usuarioId);
        for (Conta conta : contas) {
            BigDecimal receitas = BigDecimal.ZERO;
            BigDecimal despesas = BigDecimal.ZERO;

            for (Transacao t : transacoes) {
                if (t.conta != null && t.conta.id.equals(conta.id)) {
                    if (t.tipo == Transacao.TipoTransacao.RECEITA) {
                        receitas = receitas.add(t.valor);
                    } else {
                        despesas = despesas.add(t.valor);
                    }
                }
            }

            resumo.porConta.add(new ResumoFinanceiroDTO.ResumoPorConta(
                    conta.id, conta.nome, conta.saldo, receitas, despesas
            ));
        }

        return Response.ok(resumo).build();
    }

    @POST
    public Response criar(TransacaoDTO dto) {
        Long usuarioId = userPrincipal.getUserId();
        if (usuarioId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Usuario usuario = Usuario.findById(usuarioId);
        if (usuario == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Transacao transacao = new Transacao();
        transacao.descricao = dto.descricao;
        transacao.valor = dto.valor;
        transacao.data = dto.data;
        transacao.tipo = Transacao.TipoTransacao.valueOf(dto.tipo);
        transacao.tipoPessoa = Transacao.TipoPessoa.valueOf(dto.tipoPessoa);
        transacao.usuario = usuario;

        if (dto.categoriaId != null) {
            transacao.categoria = Categoria.findById(dto.categoriaId);
        }
        if (dto.contaId != null) {
            transacao.conta = Conta.findById(dto.contaId);
        }

        transacao.persist();

        TransacaoDTO response = new TransacaoDTO(transacao.id, transacao.descricao, transacao.valor,
                transacao.data, transacao.tipo.toString(), transacao.tipoPessoa.toString(),
                transacao.categoria != null ? transacao.categoria.id : null,
                transacao.conta != null ? transacao.conta.id : null, transacao.usuario.id);
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    @PUT
    @Path("/{id}")
    public Response atualizar(@PathParam("id") Long id, TransacaoDTO dto) {
        Long usuarioId = userPrincipal.getUserId();
        if (usuarioId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Transacao transacao = Transacao.findById(id);
        if (transacao == null || !transacao.usuario.id.equals(usuarioId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        transacao.descricao = dto.descricao;
        transacao.valor = dto.valor;
        transacao.data = dto.data;
        transacao.tipo = Transacao.TipoTransacao.valueOf(dto.tipo);
        transacao.persist();

        TransacaoDTO response = new TransacaoDTO(transacao.id, transacao.descricao, transacao.valor,
                transacao.data, transacao.tipo.toString(), transacao.tipoPessoa.toString(),
                transacao.categoria != null ? transacao.categoria.id : null,
                transacao.conta != null ? transacao.conta.id : null, transacao.usuario.id);
        return Response.ok(response).build();
    }

    @DELETE
    @Path("/{id}")
    public Response deletar(@PathParam("id") Long id) {
        Long usuarioId = userPrincipal.getUserId();
        if (usuarioId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Transacao transacao = Transacao.findById(id);
        if (transacao == null || !transacao.usuario.id.equals(usuarioId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        transacao.delete();
        return Response.noContent().build();
    }
}
