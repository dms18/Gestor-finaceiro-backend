package br.com.financeiro.resource;

import br.com.financeiro.dto.ResumoFinanceiroDTO;
import br.com.financeiro.dto.TransacaoDTO;
import br.com.financeiro.entity.*;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Path("/transacoes")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class TransacaoResource {

    @Inject
    UserPrincipal userPrincipal;

    @GET
    public Response listar(@QueryParam("tipoPessoa") String tipoPessoa,
                          @QueryParam("mes") Integer mes,
                          @QueryParam("ano") Integer ano,
                          @QueryParam("inicio") java.time.LocalDate inicio,
                          @QueryParam("fim") java.time.LocalDate fim) {
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
                .map(TransacaoResource::toDTO)
                .collect(Collectors.toList());

        return Response.ok(dtos).build();
    }

    @GET
    @Path("/resumo")
    public Response resumo(@QueryParam("tipoPessoa") String tipoPessoa,
                          @QueryParam("mes") Integer mes,
                          @QueryParam("ano") Integer ano,
                          @QueryParam("inicio") java.time.LocalDate inicio,
                          @QueryParam("fim") java.time.LocalDate fim) {
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

        // Saldo atual: saldo inicial das contas + movimentações até hoje. Lançamentos futuros
        // não contam. Se o período pedido terminou antes de hoje, vale o fim desse período.
        java.time.LocalDate corte = java.time.LocalDate.now();
        // Intervalo livre (dia específico ou período): inicio/fim têm precedência sobre mes/ano.
        boolean intervalo = inicio != null || fim != null;
        if (intervalo) {
            if (inicio != null && fim != null && fim.isBefore(inicio)) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("{\"message\":\"A data final deve ser igual ou posterior à inicial\"}")
                        .build();
            }
            if (fim != null && fim.isBefore(corte)) {
                corte = fim;
            }
        } else if (mes != null && ano != null) {
            java.time.LocalDate fimDoPeriodo = java.time.YearMonth.of(ano, mes).atEndOfMonth();
            if (fimDoPeriodo.isBefore(corte)) {
                corte = fimDoPeriodo;
            }
        }
        final java.time.LocalDate corteSaldo = corte;
        List<Transacao> acumuladas = transacoes.stream()
                .filter(t -> !t.data.isAfter(corteSaldo))
                .collect(Collectors.toList());

        if (intervalo) {
            transacoes = transacoes.stream()
                    .filter(t -> (inicio == null || !t.data.isBefore(inicio))
                            && (fim == null || !t.data.isAfter(fim)))
                    .collect(Collectors.toList());
        } else if (mes != null && ano != null) {
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

        List<Conta> contas = Conta.list("usuario.id", usuarioId);

        BigDecimal saldoInicial = BigDecimal.ZERO;
        for (Conta conta : contas) {
            if (tipoPessoa == null || conta.tipoPessoa.toString().equals(tipoPessoa)) {
                saldoInicial = saldoInicial.add(conta.saldo);
            }
        }
        BigDecimal movimentoAcumulado = BigDecimal.ZERO;
        for (Transacao t : acumuladas) {
            BigDecimal efeito = t.tipo == Transacao.TipoTransacao.RECEITA ? t.valor : t.valor.negate();
            movimentoAcumulado = movimentoAcumulado.add(efeito);
            if (t.conta == null) {
                // entra no saldo mas não pertence a nenhuma conta: o app avisa o usuário
                resumo.semConta = resumo.semConta.add(efeito);
                resumo.semContaQtd++;
            }
        }
        resumo.saldo = saldoInicial.add(movimentoAcumulado);

        for (Conta conta : contas) {
            BigDecimal receitas = BigDecimal.ZERO;
            BigDecimal despesas = BigDecimal.ZERO;
            BigDecimal saldoAtualConta = conta.saldo;

            for (Transacao t : acumuladas) {
                if (t.conta != null && t.conta.id.equals(conta.id)) {
                    saldoAtualConta = t.tipo == Transacao.TipoTransacao.RECEITA
                            ? saldoAtualConta.add(t.valor)
                            : saldoAtualConta.subtract(t.valor);
                }
            }

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
                    conta.id, conta.nome, conta.saldo, receitas, despesas, saldoAtualConta
            ));
        }

        return Response.ok(resumo).build();
    }

    private static boolean dataPlausivel(java.time.LocalDate data) {
        java.time.LocalDate hoje = java.time.LocalDate.now();
        return data != null
                && !data.isBefore(java.time.LocalDate.of(2000, 1, 1))
                && !data.isAfter(hoje.plusYears(10));
    }

    @POST
    @Transactional
    public Response criar(@Valid TransacaoDTO dto) {
        if (!dataPlausivel(dto.data)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"message\":\"Data fora do intervalo permitido (a partir de 2000 e até 10 anos à frente)\"}")
                    .build();
        }

        Long usuarioId = userPrincipal.getUserId();
        if (usuarioId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Usuario usuario = Usuario.findById(usuarioId);
        if (usuario == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Categoria categoria = null;
        if (dto.categoriaId != null) {
            categoria = Categoria.findById(dto.categoriaId);
            if (categoria == null || !categoria.usuario.id.equals(usuarioId)) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("{\"message\":\"Categoria inválida\"}")
                        .build();
            }
        }
        Conta conta = null;
        if (dto.contaId != null) {
            conta = Conta.findById(dto.contaId);
            if (conta == null || !conta.usuario.id.equals(usuarioId)) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("{\"message\":\"Conta inválida\"}")
                        .build();
            }
        }

        if (!Boolean.TRUE.equals(dto.parcelado)) {
            Transacao transacao = nova(dto, usuario, categoria, conta, dto.valor, dto.data);
            transacao.persist();
            return Response.status(Response.Status.CREATED).entity(toDTO(transacao)).build();
        }

        return criarParceladas(dto, usuario, categoria, conta);
    }

    /**
     * Parcelamento: "valor" é o montante total. Gera uma transação por mês a partir da data
     * informada; o valor é dividido em centavos e a diferença do arredondamento vai na última
     * parcela, de modo que a soma das parcelas é sempre igual ao montante.
     */
    private Response criarParceladas(TransacaoDTO dto, Usuario usuario, Categoria categoria, Conta conta) {
        Integer n = dto.numeroParcelas;
        if (n == null) {
            return erro("Informe a quantidade de parcelas");
        }
        if (!dataPlausivel(dto.data.plusMonths(n - 1L))) {
            return erro("A última parcela ficaria fora do intervalo de datas permitido");
        }
        BigDecimal parcela = dto.valor.divide(BigDecimal.valueOf(n), 2, RoundingMode.DOWN);
        if (parcela.compareTo(new BigDecimal("0.01")) < 0) {
            return erro("Montante muito baixo para " + n + " parcelas (mínimo de R$ 0,01 por parcela)");
        }
        BigDecimal ultima = dto.valor.subtract(parcela.multiply(BigDecimal.valueOf(n - 1L)));

        String grupo = UUID.randomUUID().toString();
        List<TransacaoDTO> criadas = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            // plusMonths a partir da data original evita "derrapar" o dia (31/01 -> 28/02 -> 28/03)
            Transacao t = nova(dto, usuario, categoria, conta, i == n ? ultima : parcela, dto.data.plusMonths(i - 1L));
            t.grupoParcelas = grupo;
            t.parcelaNumero = i;
            t.totalParcelas = n;
            t.persist();
            criadas.add(toDTO(t));
        }
        return Response.status(Response.Status.CREATED).entity(criadas).build();
    }

    private static Transacao nova(TransacaoDTO dto, Usuario usuario, Categoria categoria, Conta conta,
                                  BigDecimal valor, LocalDate data) {
        Transacao t = new Transacao();
        t.descricao = dto.descricao;
        t.valor = valor;
        t.data = data;
        t.tipo = Transacao.TipoTransacao.valueOf(dto.tipo);
        t.tipoPessoa = Transacao.TipoPessoa.valueOf(dto.tipoPessoa);
        t.usuario = usuario;
        t.categoria = categoria;
        t.conta = conta;
        return t;
    }

    private static Response erro(String mensagem) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity("{\"message\":\"" + mensagem + "\"}")
                .build();
    }

    static TransacaoDTO toDTO(Transacao t) {
        TransacaoDTO dto = new TransacaoDTO(t.id, t.descricao, t.valor, t.data, t.tipo.toString(),
                t.tipoPessoa.toString(), t.categoria != null ? t.categoria.id : null,
                t.conta != null ? t.conta.id : null, t.usuario.id);
        dto.grupoParcelas = t.grupoParcelas;
        dto.parcelaNumero = t.parcelaNumero;
        dto.totalParcelas = t.totalParcelas;
        return dto;
    }

    @PUT
    @Transactional
    @Path("/{id}")
    public Response atualizar(@PathParam("id") Long id, @Valid TransacaoDTO dto) {
        if (!dataPlausivel(dto.data)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"message\":\"Data fora do intervalo permitido (a partir de 2000 e até 10 anos à frente)\"}")
                    .build();
        }

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

        if (dto.categoriaId != null) {
            Categoria categoria = Categoria.findById(dto.categoriaId);
            if (categoria == null || !categoria.usuario.id.equals(usuarioId)) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("{\"message\":\"Categoria inválida\"}")
                        .build();
            }
            transacao.categoria = categoria;
        }
        if (dto.contaId != null) {
            Conta conta = Conta.findById(dto.contaId);
            if (conta == null || !conta.usuario.id.equals(usuarioId)) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("{\"message\":\"Conta inválida\"}")
                        .build();
            }
            transacao.conta = conta;
        }

        transacao.persist();

        return Response.ok(toDTO(transacao)).build();
    }

    @DELETE
    @Transactional
    @Path("/{id}")
    public Response deletar(@PathParam("id") Long id, @QueryParam("escopo") String escopo) {
        Long usuarioId = userPrincipal.getUserId();
        if (usuarioId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Transacao transacao = Transacao.findById(id);
        if (transacao == null || !transacao.usuario.id.equals(usuarioId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Parcelas: UMA (padrão), PROXIMAS (esta e as seguintes) ou TODAS do mesmo parcelamento.
        String modo = escopo == null ? "UMA" : escopo.toUpperCase();
        if (!modo.equals("UMA") && !modo.equals("PROXIMAS") && !modo.equals("TODAS")) {
            return erro("Escopo de exclusão inválido");
        }
        if (transacao.grupoParcelas == null || modo.equals("UMA")) {
            transacao.delete();
        } else if (modo.equals("TODAS")) {
            Transacao.delete("usuario.id = ?1 and grupoParcelas = ?2", usuarioId, transacao.grupoParcelas);
        } else {
            Transacao.delete("usuario.id = ?1 and grupoParcelas = ?2 and parcelaNumero >= ?3",
                    usuarioId, transacao.grupoParcelas, transacao.parcelaNumero);
        }
        return Response.noContent().build();
    }
}
