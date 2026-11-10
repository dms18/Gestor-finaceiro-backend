package br.com.financeiro.resource;

import static br.com.financeiro.support.ApiTestSupport.como;
import static br.com.financeiro.support.ApiTestSupport.criarConta;
import static br.com.financeiro.support.ApiTestSupport.criarTransacao;
import static br.com.financeiro.support.ApiTestSupport.novoUsuario;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import br.com.financeiro.support.ApiTestSupport.Sessao;
import io.quarkus.test.junit.QuarkusTest;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ContaResourceTest {

    private static Map<String, Object> corpo(String nome, Object saldo) {
        return Map.of("nome", nome, "tipo", "CORRENTE", "saldo", saldo, "tipoPessoa", "PF");
    }

    @Test
    void criaContaComSaldoInicial() {
        Sessao s = novoUsuario();

        como(s).body(Map.of("nome", "Principal", "instituicao", "Nubank", "numeroConta", "123-4",
                        "tipo", "POUPANCA", "saldo", 1500.50, "tipoPessoa", "PJ"))
                .post("/contas")
                .then().statusCode(201)
                .body("nome", equalTo("Principal"))
                .body("instituicao", equalTo("Nubank"))
                .body("tipo", equalTo("POUPANCA"))
                .body("tipoPessoa", equalTo("PJ"))
                .body("saldo", equalTo(1500.50f));
    }

    @Test
    void saldoInicialZeroEValido() {
        como(novoUsuario()).body(corpo("Caixa", 0)).post("/contas").then().statusCode(201);
    }

    @Test
    void recusaSaldoNegativo() {
        como(novoUsuario()).body(corpo("Caixa", -1)).post("/contas").then().statusCode(400);
    }

    @Test
    void recusaMaisDeDuasCasasDecimais() {
        como(novoUsuario()).body(corpo("Caixa", 10.123)).post("/contas").then().statusCode(400);
    }

    @Test
    void recusaNomeVazioEVinculoInvalido() {
        Sessao s = novoUsuario();

        como(s).body(corpo("", 10)).post("/contas").then().statusCode(400);
        como(s).body(Map.of("nome", "X", "tipo", "CORRENTE", "saldo", 10, "tipoPessoa", "XX"))
                .post("/contas").then().statusCode(400);
    }

    @Test
    void listaSomenteAsContasDoProprioUsuario() {
        Sessao a = novoUsuario();
        Sessao b = novoUsuario();
        criarConta(a, "Da Ana", "PF", 100);

        como(b).get("/contas").then().statusCode(200).body("$", empty());
        como(a).get("/contas").then().statusCode(200).body("$", hasSize(1)).body("[0].nome", equalTo("Da Ana"));
    }

    @Test
    void atualizaAPropriaConta() {
        Sessao s = novoUsuario();
        long id = criarConta(s, "Antiga", "PF", 100);

        como(s).body(corpo("Nova", 250)).put("/contas/" + id)
                .then().statusCode(200).body("nome", equalTo("Nova")).body("saldo", equalTo(250));
    }

    @Test
    void outroUsuarioNaoEditaNemExcluiContaAlheia_recebe404() {
        Sessao dono = novoUsuario();
        Sessao intruso = novoUsuario();
        long id = criarConta(dono, "Minha", "PF", 100);

        como(intruso).body(corpo("Invadida", 1)).put("/contas/" + id).then().statusCode(404);
        como(intruso).delete("/contas/" + id).then().statusCode(404);

        como(dono).get("/contas").then().body("[0].nome", equalTo("Minha"));
    }

    @Test
    void naoExcluiContaComTransacoesVinculadas() {
        Sessao s = novoUsuario();
        long conta = criarConta(s, "Com movimento", "PF", 100);
        criarTransacao(s, "RECEITA", 10, LocalDate.now(), conta);

        como(s).delete("/contas/" + conta)
                .then().statusCode(409).body("message", containsString("transação"));
    }

    @Test
    void naoExcluiContaUsadaComoContaDeDebitoDeSazonalidade() {
        Sessao s = novoUsuario();
        long conta = criarConta(s, "Reserva", "PF", 100);
        como(s).body(Map.of("nome", "Natal", "mesInicio", 1, "mesFim", 12, "nivelImpacto", "ALTO",
                        "dataAlvo", LocalDate.now().toString(), "valorNecessario", 500, "contaDebitoId", conta))
                .post("/sazonalidade").then().statusCode(201);

        como(s).delete("/contas/" + conta)
                .then().statusCode(409).body("message", containsString("sazonalidade"));
    }

    @Test
    void excluiContaSemVinculos() {
        Sessao s = novoUsuario();
        long conta = criarConta(s, "Livre", "PF", 100);

        como(s).delete("/contas/" + conta).then().statusCode(204);
        como(s).get("/contas").then().body("$", empty());
    }
}
