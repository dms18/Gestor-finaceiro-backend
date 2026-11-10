package br.com.financeiro.resource;

import static br.com.financeiro.support.ApiTestSupport.como;
import static br.com.financeiro.support.ApiTestSupport.criarConta;
import static br.com.financeiro.support.ApiTestSupport.criarTransacao;
import static br.com.financeiro.support.ApiTestSupport.novoUsuario;
import static br.com.financeiro.support.ApiTestSupport.transacao;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import br.com.financeiro.support.ApiTestSupport.Sessao;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
class TransacaoResourceTest {

    private static final LocalDate HOJE = LocalDate.now();

    // ---------------------------------------------------------------- à vista

    @Test
    void criaTransacaoAvulsaESemDadosDeParcela() {
        Sessao s = novoUsuario();
        long conta = criarConta(s, "Principal", "PF", 0);

        como(s).body(transacao("Venda", 150.75, HOJE, "RECEITA", "PF", conta))
                .post("/transacoes")
                .then().statusCode(201)
                .body("id", notNullValue())
                .body("valor", equalTo(150.75f))
                .body("tipo", equalTo("RECEITA"))
                .body("contaId", equalTo((int) conta))
                .body("grupoParcelas", nullValue())
                .body("parcelaNumero", nullValue());
    }

    @Test
    void validacoesDeCampos() {
        Sessao s = novoUsuario();

        como(s).body(transacao("Sem valor", 0, HOJE, "DESPESA", "PF", null)).post("/transacoes").then().statusCode(400);
        como(s).body(transacao("Negativa", -5, HOJE, "DESPESA", "PF", null)).post("/transacoes").then().statusCode(400);
        como(s).body(transacao("", 10, HOJE, "DESPESA", "PF", null)).post("/transacoes").then().statusCode(400);
        como(s).body(transacao("Centavos demais", 1.234, HOJE, "DESPESA", "PF", null)).post("/transacoes").then().statusCode(400);
    }

    @Test
    void recusaDatasForaDoIntervaloPermitido() {
        Sessao s = novoUsuario();

        como(s).body(transacao("Antiga", 10, LocalDate.of(1999, 12, 31), "DESPESA", "PF", null))
                .post("/transacoes").then().statusCode(400).body("message", containsString("Data fora do intervalo"));
        como(s).body(transacao("Distante", 10, HOJE.plusYears(11), "DESPESA", "PF", null))
                .post("/transacoes").then().statusCode(400);
    }

    @Test
    void listaSomenteAsTransacoesDoUsuarioEFiltraPorPessoa() {
        Sessao a = novoUsuario();
        Sessao b = novoUsuario();
        criarTransacao(a, "RECEITA", 10, HOJE, null);
        como(a).body(transacao("PJ", 20, HOJE, "RECEITA", "PJ", null)).post("/transacoes").then().statusCode(201);

        como(b).get("/transacoes").then().statusCode(200).body("$", empty());
        como(a).get("/transacoes").then().body("$", hasSize(2));
        como(a).get("/transacoes?tipoPessoa=PJ").then().body("$", hasSize(1)).body("[0].descricao", equalTo("PJ"));
    }

    @Test
    void naoUsaContaOuCategoriaDeOutroUsuario() {
        Sessao dono = novoUsuario();
        Sessao intruso = novoUsuario();
        long contaAlheia = criarConta(dono, "Alheia", "PF", 0);

        como(intruso).body(transacao("Invasão", 10, HOJE, "RECEITA", "PF", contaAlheia))
                .post("/transacoes").then().statusCode(400).body("message", equalTo("Conta inválida"));

        Map<String, Object> comCategoria = transacao("Invasão", 10, HOJE, "RECEITA", "PF", null);
        comCategoria.put("categoriaId", 999999);
        como(intruso).body(comCategoria).post("/transacoes").then().statusCode(400).body("message", equalTo("Categoria inválida"));
    }

    @Test
    void atualizaAPropriaTransacao() {
        Sessao s = novoUsuario();
        long id = criarTransacao(s, "RECEITA", 10, HOJE, null);

        como(s).body(transacao("Corrigida", 99.9, HOJE, "RECEITA", "PF", null)).put("/transacoes/" + id)
                .then().statusCode(200).body("descricao", equalTo("Corrigida")).body("valor", equalTo(99.9f));
    }

    @Test
    void outroUsuarioNaoEditaNemExcluiTransacaoAlheia_recebe404() {
        Sessao dono = novoUsuario();
        Sessao intruso = novoUsuario();
        long id = criarTransacao(dono, "RECEITA", 10, HOJE, null);

        como(intruso).body(transacao("X", 1, HOJE, "RECEITA", "PF", null)).put("/transacoes/" + id).then().statusCode(404);
        como(intruso).delete("/transacoes/" + id).then().statusCode(404);

        como(dono).get("/transacoes").then().body("$", hasSize(1));
    }

    @Test
    void excluiAPropriaTransacao() {
        Sessao s = novoUsuario();
        long id = criarTransacao(s, "RECEITA", 10, HOJE, null);

        como(s).delete("/transacoes/" + id).then().statusCode(204);
        como(s).get("/transacoes").then().body("$", empty());
    }

    // -------------------------------------------------------------- parcelado

    private static Map<String, Object> parcelada(Object montante, int parcelas, LocalDate primeira, Long conta) {
        Map<String, Object> corpo = transacao("Notebook", montante, primeira, "DESPESA", "PF", conta);
        corpo.put("parcelado", true);
        corpo.put("numeroParcelas", parcelas);
        return corpo;
    }

    private static List<Map<String, Object>> parcelas(Response r) {
        return r.jsonPath().getList("$");
    }

    private static BigDecimal soma(List<Map<String, Object>> lista) {
        return lista.stream().map(p -> new BigDecimal(p.get("valor").toString())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void divideOMontanteEmParcelasMensaisEGrudaOsCentavosNaUltima() {
        Sessao s = novoUsuario();

        Response r = como(s).body(parcelada(100.00, 3, LocalDate.of(2027, 1, 31), null)).post("/transacoes");
        r.then().statusCode(201).body("$", hasSize(3));
        List<Map<String, Object>> p = parcelas(r);

        assertEquals(0, new BigDecimal("33.33").compareTo(new BigDecimal(p.get(0).get("valor").toString())));
        assertEquals(0, new BigDecimal("33.33").compareTo(new BigDecimal(p.get(1).get("valor").toString())));
        assertEquals(0, new BigDecimal("33.34").compareTo(new BigDecimal(p.get(2).get("valor").toString())));
        assertEquals(0, new BigDecimal("100.00").compareTo(soma(p)), "a soma das parcelas deve fechar com o montante");

        // fim de mês: 31/01 -> 28/02 -> 31/03 (não "derrapa" para o dia 28)
        assertEquals("2027-01-31", p.get(0).get("data"));
        assertEquals("2027-02-28", p.get(1).get("data"));
        assertEquals("2027-03-31", p.get(2).get("data"));
    }

    @Test
    void parcelasCompartilhamGrupoENumeracao() {
        Sessao s = novoUsuario();

        List<Map<String, Object>> p = parcelas(como(s).body(parcelada(600, 6, HOJE, null)).post("/transacoes"));

        assertEquals(6, p.size());
        String grupo = (String) p.get(0).get("grupoParcelas");
        for (int i = 0; i < 6; i++) {
            assertEquals(grupo, p.get(i).get("grupoParcelas"));
            assertEquals(i + 1, p.get(i).get("parcelaNumero"));
            assertEquals(6, p.get(i).get("totalParcelas"));
        }
    }

    @Test
    void parcelasSempreSomamOMontante() {
        Sessao s = novoUsuario();
        Object[][] casos = {{10, 3}, {0.10, 3}, {999.99, 7}, {5000, 12}, {1, 60}, {0.60, 60}};

        for (Object[] caso : casos) {
            List<Map<String, Object>> p = parcelas(como(s).body(parcelada(caso[0], (Integer) caso[1], HOJE, null)).post("/transacoes"));
            assertEquals(0, new BigDecimal(caso[0].toString()).compareTo(soma(p)), caso[0] + " em " + caso[1] + "x");
        }
    }

    @Test
    void receitaTambemPodeSerParcelada() {
        Sessao s = novoUsuario();
        Map<String, Object> corpo = transacao("Venda a prazo", 1200, HOJE, "RECEITA", "PJ", null);
        corpo.put("parcelado", true);
        corpo.put("numeroParcelas", 4);

        como(s).body(corpo).post("/transacoes").then().statusCode(201).body("$", hasSize(4)).body("[0].tipo", equalTo("RECEITA"));
    }

    @Test
    void parcelasHerdamContaECategoria() {
        Sessao s = novoUsuario();
        long conta = criarConta(s, "Cartão", "PF", 0);

        como(s).body(parcelada(300, 3, HOJE, conta)).post("/transacoes")
                .then().body("[0].contaId", equalTo((int) conta)).body("[2].contaId", equalTo((int) conta));
    }

    @Test
    void listarMostraNumeroETotalDeParcelas() {
        Sessao s = novoUsuario();
        como(s).body(parcelada(300, 3, HOJE, null)).post("/transacoes").then().statusCode(201);

        como(s).get("/transacoes").then().body("$", hasSize(3)).body("parcelaNumero", org.hamcrest.Matchers.containsInAnyOrder(1, 2, 3))
                .body("totalParcelas", org.hamcrest.Matchers.everyItem(equalTo(3)));
    }

    @Test
    void recusaQuantidadesInvalidasDeParcelas() {
        Sessao s = novoUsuario();

        como(s).body(parcelada(100, 1, HOJE, null)).post("/transacoes").then().statusCode(400).body("message", containsString("mínimo 2"));
        como(s).body(parcelada(100, 0, HOJE, null)).post("/transacoes").then().statusCode(400);
        como(s).body(parcelada(1000, 61, HOJE, null)).post("/transacoes").then().statusCode(400).body("message", containsString("máximo 60"));

        Map<String, Object> semQuantidade = transacao("x", 100, HOJE, "DESPESA", "PF", null);
        semQuantidade.put("parcelado", true);
        como(s).body(semQuantidade).post("/transacoes").then().statusCode(400).body("message", equalTo("Informe a quantidade de parcelas"));

        como(s).get("/transacoes").then().body("$", empty()); // nada foi gravado pela metade
    }

    @Test
    void recusaMontanteQueDariaMenosDeUmCentavoPorParcela() {
        como(novoUsuario()).body(parcelada(0.05, 12, HOJE, null)).post("/transacoes")
                .then().statusCode(400).body("message", containsString("Montante muito baixo"));
    }

    @Test
    void recusaParcelamentoCujaUltimaParcelaPassariaDoLimiteDeDatas() {
        como(novoUsuario()).body(parcelada(600, 60, HOJE.plusYears(6), null)).post("/transacoes")
                .then().statusCode(400).body("message", containsString("última parcela"));
    }

    @Test
    void parceladoFalsoSeComportaComoTransacaoAvulsa() {
        Map<String, Object> corpo = transacao("x", 100, HOJE, "DESPESA", "PF", null);
        corpo.put("parcelado", false);
        corpo.put("numeroParcelas", 5);

        como(novoUsuario()).body(corpo).post("/transacoes").then().statusCode(201).body("grupoParcelas", nullValue());
    }

    @Test
    void conteudoInvalidoNaoCriaParcelaNenhuma() {
        Sessao s = novoUsuario();
        Map<String, Object> corpo = parcelada(100, 3, HOJE, null);
        corpo.put("contaId", 999999); // conta inexistente

        como(s).body(corpo).post("/transacoes").then().statusCode(400);
        como(s).get("/transacoes").then().body("$", empty());
    }

    @Test
    void editarUmaParcelaNaoAlteraAsOutras() {
        Sessao s = novoUsuario();
        List<Map<String, Object>> p = parcelas(como(s).body(parcelada(300, 3, HOJE, null)).post("/transacoes"));
        Number id2 = (Number) p.get(1).get("id");

        como(s).body(transacao("Notebook", 500, HOJE.plusMonths(1), "DESPESA", "PF", null)).put("/transacoes/" + id2)
                .then().statusCode(200).body("valor", equalTo(500)).body("parcelaNumero", equalTo(2));

        List<Map<String, Object>> depois = como(s).get("/transacoes").jsonPath().getList("$");
        long com100 = depois.stream().filter(t -> new BigDecimal(t.get("valor").toString()).compareTo(new BigDecimal("100")) == 0).count();
        assertEquals(2, com100, "as outras duas parcelas continuam com R$ 100,00");
    }

    // ------------------------------------------------------- exclusão parcelas

    private static List<Number> ids(List<Map<String, Object>> p) {
        return p.stream().map(t -> (Number) t.get("id")).toList();
    }

    @Test
    void excluirSoUmaParcelaEOPadrao() {
        Sessao s = novoUsuario();
        List<Number> ids = ids(parcelas(como(s).body(parcelada(400, 4, HOJE, null)).post("/transacoes")));

        como(s).delete("/transacoes/" + ids.get(1)).then().statusCode(204);

        como(s).get("/transacoes").then().body("$", hasSize(3));
    }

    @Test
    void escopoUMAApagaSoEssaParcela() {
        Sessao s = novoUsuario();
        List<Number> ids = ids(parcelas(como(s).body(parcelada(400, 4, HOJE, null)).post("/transacoes")));

        como(s).delete("/transacoes/" + ids.get(1) + "?escopo=UMA").then().statusCode(204);

        como(s).get("/transacoes").then().body("$", hasSize(3));
    }

    @Test
    void escopoPROXIMASApagaEstaEAsSeguintes() {
        Sessao s = novoUsuario();
        List<Number> ids = ids(parcelas(como(s).body(parcelada(400, 4, HOJE, null)).post("/transacoes")));

        como(s).delete("/transacoes/" + ids.get(1) + "?escopo=PROXIMAS").then().statusCode(204);

        como(s).get("/transacoes").then().body("$", hasSize(1)).body("[0].parcelaNumero", equalTo(1));
    }

    @Test
    void escopoTODASApagaOParcelamentoInteiroMasNaoOutrasTransacoes() {
        Sessao s = novoUsuario();
        criarTransacao(s, "RECEITA", 10, HOJE, null);
        List<Number> ids = ids(parcelas(como(s).body(parcelada(400, 4, HOJE, null)).post("/transacoes")));

        como(s).delete("/transacoes/" + ids.get(3) + "?escopo=TODAS").then().statusCode(204);

        como(s).get("/transacoes").then().body("$", hasSize(1)).body("[0].descricao", equalTo("Lançamento"));
    }

    @Test
    void escopoTODASNaoMexeEmParcelamentosDistintos() {
        Sessao s = novoUsuario();
        List<Number> a = ids(parcelas(como(s).body(parcelada(300, 3, HOJE, null)).post("/transacoes")));
        parcelas(como(s).body(parcelada(200, 2, HOJE, null)).post("/transacoes"));

        como(s).delete("/transacoes/" + a.get(0) + "?escopo=TODAS").then().statusCode(204);

        como(s).get("/transacoes").then().body("$", hasSize(2));
    }

    @Test
    void escopoSoImportaParaParcelas_transacaoAvulsaComTODASApagaSoEla() {
        Sessao s = novoUsuario();
        long avulsa = criarTransacao(s, "RECEITA", 10, HOJE, null);
        criarTransacao(s, "RECEITA", 20, HOJE, null);

        como(s).delete("/transacoes/" + avulsa + "?escopo=TODAS").then().statusCode(204);

        como(s).get("/transacoes").then().body("$", hasSize(1));
    }

    @Test
    void escopoInvalidoERecusadoENadaEApagado() {
        Sessao s = novoUsuario();
        List<Number> ids = ids(parcelas(como(s).body(parcelada(300, 3, HOJE, null)).post("/transacoes")));

        como(s).delete("/transacoes/" + ids.get(0) + "?escopo=XYZ").then().statusCode(400);

        como(s).get("/transacoes").then().body("$", hasSize(3));
    }

    @Test
    void outroUsuarioNaoApagaParcelamentoAlheioNemComEscopoTODAS() {
        Sessao dono = novoUsuario();
        Sessao intruso = novoUsuario();
        List<Number> ids = ids(parcelas(como(dono).body(parcelada(300, 3, HOJE, null)).post("/transacoes")));

        como(intruso).delete("/transacoes/" + ids.get(0) + "?escopo=TODAS").then().statusCode(404);

        como(dono).get("/transacoes").then().body("$", hasSize(3));
    }
}
