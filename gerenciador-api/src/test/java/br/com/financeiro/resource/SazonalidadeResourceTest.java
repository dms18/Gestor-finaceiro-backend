package br.com.financeiro.resource;

import static br.com.financeiro.support.ApiTestSupport.como;
import static br.com.financeiro.support.ApiTestSupport.criarConta;
import static br.com.financeiro.support.ApiTestSupport.criarTransacao;
import static br.com.financeiro.support.ApiTestSupport.novoUsuario;
import static br.com.financeiro.support.ApiTestSupport.numero;
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
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SazonalidadeResourceTest {

    private static final LocalDate HOJE = LocalDate.now();

    /** Período de 3 meses que contém o mês atual (mês anterior, atual e seguinte), mesmo na virada do ano. */
    private static Map<String, Object> periodoAtual(String nome) {
        int mes = HOJE.getMonthValue();
        Map<String, Object> p = new HashMap<>();
        p.put("nome", nome);
        p.put("mesInicio", mes == 1 ? 12 : mes - 1);
        p.put("mesFim", mes == 12 ? 1 : mes + 1);
        p.put("nivelImpacto", "ALTO");
        return p;
    }

    private static Map<String, Object> comReserva(Map<String, Object> periodo, LocalDate data, Object valor, Long conta) {
        Map<String, Object> p = new HashMap<>(periodo);
        p.put("dataAlvo", data.toString());
        p.put("valorNecessario", valor);
        p.put("contaDebitoId", conta);
        return p;
    }

    private static long criar(Sessao s, Map<String, Object> corpo) {
        return como(s).body(corpo).post("/sazonalidade").then().statusCode(201).extract().<Number>path("id").longValue();
    }

    // ------------------------------------------------------------ cadastro

    @Test
    void periodoSemReservaContinuaFuncionando() {
        Sessao s = novoUsuario();

        como(s).body(periodoAtual("Verão")).post("/sazonalidade")
                .then().statusCode(201)
                .body("dataAlvo", nullValue()).body("valorNecessario", nullValue()).body("contaDebitoId", nullValue());
    }

    @Test
    void periodoComReservaDevolveContaDeDebito() {
        Sessao s = novoUsuario();
        long conta = criarConta(s, "Reserva", "PF", 0);

        como(s).body(comReserva(periodoAtual("Natal"), HOJE, 2000, conta)).post("/sazonalidade")
                .then().statusCode(201)
                .body("dataAlvo", equalTo(HOJE.toString()))
                .body("valorNecessario", equalTo(2000))
                .body("contaDebitoId", equalTo((int) conta))
                .body("contaDebitoNome", equalTo("Reserva"));
    }

    @Test
    void reservaIncompletaERecusada() {
        Sessao s = novoUsuario();
        long conta = criarConta(s, "Reserva", "PF", 0);
        String msg = "juntos";

        Map<String, Object> soData = new HashMap<>(periodoAtual("A")); soData.put("dataAlvo", HOJE.toString());
        Map<String, Object> soValor = new HashMap<>(periodoAtual("B")); soValor.put("valorNecessario", 100);
        Map<String, Object> soConta = new HashMap<>(periodoAtual("C")); soConta.put("contaDebitoId", conta);
        Map<String, Object> semConta = comReserva(periodoAtual("D"), HOJE, 100, null);

        for (Map<String, Object> parcial : java.util.List.of(soData, soValor, soConta, semConta)) {
            como(s).body(parcial).post("/sazonalidade").then().statusCode(400).body("message", containsString(msg));
        }
        como(s).get("/sazonalidade").then().body("$", empty());
    }

    @Test
    void valorNecessarioPrecisaSerPositivoEComDuasCasas() {
        Sessao s = novoUsuario();
        long conta = criarConta(s, "R", "PF", 0);

        como(s).body(comReserva(periodoAtual("A"), HOJE, 0, conta)).post("/sazonalidade").then().statusCode(400);
        como(s).body(comReserva(periodoAtual("B"), HOJE, -5, conta)).post("/sazonalidade").then().statusCode(400);
        como(s).body(comReserva(periodoAtual("C"), HOJE, 10.123, conta)).post("/sazonalidade").then().statusCode(400);
    }

    @Test
    void contaInexistenteOuDeOutroUsuarioERecusada_semRevelarQueExiste() {
        Sessao dono = novoUsuario();
        Sessao intruso = novoUsuario();
        long contaAlheia = criarConta(dono, "Alheia", "PF", 0);

        como(intruso).body(comReserva(periodoAtual("X"), HOJE, 100, contaAlheia)).post("/sazonalidade")
                .then().statusCode(400).body("message", equalTo("Conta para débito inválida"));
        como(intruso).body(comReserva(periodoAtual("Y"), HOJE, 100, 987654L)).post("/sazonalidade")
                .then().statusCode(400).body("message", equalTo("Conta para débito inválida"));
    }

    @Test
    void naoTrocaParaContaDeOutroUsuarioNaAtualizacao() {
        Sessao dono = novoUsuario();
        Sessao intruso = novoUsuario();
        long contaAlheia = criarConta(dono, "Alheia", "PF", 0);
        long contaPropria = criarConta(intruso, "Própria", "PF", 0);
        long id = criar(intruso, comReserva(periodoAtual("P"), HOJE, 100, contaPropria));

        como(intruso).body(comReserva(periodoAtual("P"), HOJE, 100, contaAlheia)).put("/sazonalidade/" + id)
                .then().statusCode(400);
    }

    @Test
    void outroUsuarioNaoEditaNemExcluiPeriodoAlheio() {
        Sessao dono = novoUsuario();
        Sessao intruso = novoUsuario();
        long id = criar(dono, periodoAtual("Meu"));

        como(intruso).body(periodoAtual("Invadido")).put("/sazonalidade/" + id).then().statusCode(404);
        como(intruso).delete("/sazonalidade/" + id).then().statusCode(404);
        como(dono).get("/sazonalidade").then().body("[0].nome", equalTo("Meu"));
    }

    @Test
    void removerAReservaNaAtualizacaoLimpaOsTresCampos() {
        Sessao s = novoUsuario();
        long conta = criarConta(s, "R", "PF", 0);
        long id = criar(s, comReserva(periodoAtual("P"), HOJE.minusDays(1), 100, conta));

        como(s).body(periodoAtual("P")).put("/sazonalidade/" + id)
                .then().statusCode(200)
                .body("dataAlvo", nullValue()).body("valorNecessario", nullValue()).body("contaDebitoId", nullValue());
        como(s).get("/sazonalidade/reservas").then().body("$", empty());
    }

    // ------------------------------------------------------------ /reservas

    @Test
    void reservaComDataAlcancadaCompara_saldoInsuficiente() {
        Sessao s = novoUsuario();
        long conta = criarConta(s, "Reserva", "PF", 1000);
        criarTransacao(s, "RECEITA", 150, HOJE, conta);
        criar(s, comReserva(periodoAtual("Alta temporada"), HOJE.minusDays(5), 2000, conta));

        Response r = como(s).get("/sazonalidade/reservas");
        r.then().statusCode(200).body("$", hasSize(1))
                .body("[0].nome", equalTo("Alta temporada"))
                .body("[0].contaNome", equalTo("Reserva"))
                .body("[0].suficiente", equalTo(false))
                .body("[0].percentualCobertura", equalTo(57))
                .body("[0].fimDoPeriodo", notNullValue());
        assertEquals(1150, numero(r, "[0].saldoConta"), 0.001);
        assertEquals(2000, numero(r, "[0].valorNecessario"), 0.001);
        assertEquals(-850, numero(r, "[0].diferenca"), 0.001);
    }

    @Test
    void saldoMaiorQueONecessarioEFolga() {
        Sessao s = novoUsuario();
        long conta = criarConta(s, "Reserva", "PF", 3000);
        criar(s, comReserva(periodoAtual("Baixa"), HOJE, 2000, conta));

        Response r = como(s).get("/sazonalidade/reservas");
        r.then().body("[0].suficiente", equalTo(true)).body("[0].percentualCobertura", equalTo(100));
        assertEquals(1000, numero(r, "[0].diferenca"), 0.001);
    }

    @Test
    void saldoExatamenteIgualAoNecessarioEContaComoSuficiente() {
        Sessao s = novoUsuario();
        long conta = criarConta(s, "Reserva", "PF", 2000);
        criar(s, comReserva(periodoAtual("Exato"), HOJE, 2000, conta));

        como(s).get("/sazonalidade/reservas").then().body("[0].suficiente", equalTo(true)).body("[0].percentualCobertura", equalTo(100));
    }

    @Test
    void saldoNegativoDaContaNaoGeraCoberturaNegativa() {
        Sessao s = novoUsuario();
        long conta = criarConta(s, "Reserva", "PF", 100);
        criarTransacao(s, "DESPESA", 500, HOJE, conta); // saldo atual: -400
        criar(s, comReserva(periodoAtual("Aperto"), HOJE, 1000, conta));

        como(s).get("/sazonalidade/reservas").then()
                .body("[0].percentualCobertura", equalTo(0)).body("[0].suficiente", equalTo(false));
    }

    @Test
    void saldoDaContaIgnoraLancamentosFuturos() {
        Sessao s = novoUsuario();
        long conta = criarConta(s, "Reserva", "PF", 1000);
        criarTransacao(s, "RECEITA", 5000, HOJE.plusDays(10), conta); // ainda não aconteceu
        criar(s, comReserva(periodoAtual("P"), HOJE, 2000, conta));

        Response r = como(s).get("/sazonalidade/reservas");
        assertEquals(1000, numero(r, "[0].saldoConta"), 0.001);
        r.then().body("[0].suficiente", equalTo(false));
    }

    @Test
    void naoApareceAntesDaDataAlvo() {
        Sessao s = novoUsuario();
        long conta = criarConta(s, "R", "PF", 100);
        criar(s, comReserva(periodoAtual("Futura"), HOJE.plusDays(1), 500, conta));

        como(s).get("/sazonalidade/reservas").then().body("$", empty());
    }

    @Test
    void apareceNoProprioDiaDaDataAlvo() {
        Sessao s = novoUsuario();
        long conta = criarConta(s, "R", "PF", 100);
        criar(s, comReserva(periodoAtual("Hoje"), HOJE, 500, conta));

        como(s).get("/sazonalidade/reservas").then().body("$", hasSize(1));
    }

    @Test
    void somePoisOPeriodoTerminou() {
        Sessao s = novoUsuario();
        long conta = criarConta(s, "R", "PF", 100);
        LocalDate dataAlvo = HOJE.minusMonths(3);
        Map<String, Object> encerrado = new HashMap<>();
        encerrado.put("nome", "Já passou");
        encerrado.put("mesInicio", dataAlvo.getMonthValue());
        encerrado.put("mesFim", dataAlvo.getMonthValue());
        encerrado.put("nivelImpacto", "BAIXO");
        criar(s, comReserva(encerrado, dataAlvo, 500, conta));

        como(s).get("/sazonalidade/reservas").then().body("$", empty());
    }

    @Test
    void periodoSemReservaNaoApareceNaLista() {
        Sessao s = novoUsuario();
        criar(s, periodoAtual("Sem reserva"));

        como(s).get("/sazonalidade/reservas").then().body("$", empty());
    }

    @Test
    void listaVemOrdenadaPelaDataAlvo() {
        Sessao s = novoUsuario();
        long conta = criarConta(s, "R", "PF", 100);
        criar(s, comReserva(periodoAtual("Mais recente"), HOJE.minusDays(2), 500, conta));
        criar(s, comReserva(periodoAtual("Mais antiga"), HOJE.minusDays(20), 500, conta));

        como(s).get("/sazonalidade/reservas").then()
                .body("[0].nome", equalTo("Mais antiga")).body("[1].nome", equalTo("Mais recente"));
    }

    @Test
    void cadaUsuarioVeSoAsPropriasReservas() {
        Sessao a = novoUsuario();
        Sessao b = novoUsuario();
        long conta = criarConta(a, "R", "PF", 100);
        criar(a, comReserva(periodoAtual("Da Ana"), HOJE, 500, conta));

        como(b).get("/sazonalidade/reservas").then().body("$", empty());
        como(a).get("/sazonalidade/reservas").then().body("$", hasSize(1));
    }

    @Test
    void alertasAtivosContinuamIndependentesDaReserva() {
        Sessao s = novoUsuario();
        criar(s, periodoAtual("Ativo agora"));

        como(s).get("/sazonalidade/alertas-ativos").then().body("$", hasSize(1)).body("[0].nome", equalTo("Ativo agora"));
    }
}
