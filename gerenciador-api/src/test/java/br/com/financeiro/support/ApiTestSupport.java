package br.com.financeiro.support;

import static io.restassured.RestAssured.given;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Atalhos para os testes de API: cada teste cria o próprio usuário, então um não interfere no outro. */
public final class ApiTestSupport {

    public static final String SENHA = "Teste@12345";
    private static final AtomicLong SEQ = new AtomicLong();

    private ApiTestSupport() {
    }

    public record Sessao(String token, String email) {
    }

    public static Sessao novoUsuario() {
        String email = "teste" + System.nanoTime() + "-" + SEQ.incrementAndGet() + "@exemplo.com";
        String token = given()
                .contentType(ContentType.JSON)
                .body(Map.of("nome", "Usuário Teste", "email", email, "senha", SENHA, "perfil", "AMBOS", "aceiteTermos", true))
                .post("/auth/register")
                .then().statusCode(201)
                .extract().path("token");
        return new Sessao(token, email);
    }

    /** Requisição autenticada como o usuário da sessão. */
    public static RequestSpecification como(Sessao s) {
        return given().header("Authorization", "Bearer " + s.token()).contentType(ContentType.JSON);
    }

    public static long criarConta(Sessao s, String nome, String tipoPessoa, Object saldoInicial) {
        Number id = como(s)
                .body(Map.of("nome", nome, "tipo", "CORRENTE", "saldo", saldoInicial, "tipoPessoa", tipoPessoa))
                .post("/contas")
                .then().statusCode(201)
                .extract().path("id");
        return id.longValue();
    }

    public static Map<String, Object> transacao(String descricao, Object valor, LocalDate data, String tipo,
                                                String tipoPessoa, Long contaId) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("descricao", descricao);
        corpo.put("valor", valor);
        corpo.put("data", data.toString());
        corpo.put("tipo", tipo);
        corpo.put("tipoPessoa", tipoPessoa);
        if (contaId != null) {
            corpo.put("contaId", contaId);
        }
        return corpo;
    }

    /** Cria uma transação à vista e devolve o id. */
    public static long criarTransacao(Sessao s, String tipo, Object valor, LocalDate data, Long contaId) {
        Number id = como(s)
                .body(transacao("Lançamento", valor, data, tipo, "PF", contaId))
                .post("/transacoes")
                .then().statusCode(201)
                .extract().path("id");
        return id.longValue();
    }

    public static Response resumo(Sessao s, String query) {
        return como(s).get("/transacoes/resumo" + query);
    }

    public static double numero(Response r, String caminho) {
        return r.jsonPath().getDouble(caminho);
    }
}
