package br.com.financeiro.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Toda rota de dados exige autenticação. Se alguém criar um endpoint novo e esquecer
 * a checagem de usuário, este teste (com a rota adicionada à lista) acusa.
 */
@QuarkusTest
class SegurancaTest {

    static Stream<String> rotasProtegidas() {
        return Stream.of(
                "/contas",
                "/categorias",
                "/transacoes",
                "/transacoes/resumo",
                "/sazonalidade",
                "/sazonalidade/alertas-ativos",
                "/sazonalidade/reservas",
                "/investimentos",
                "/usuarios/perfil");
    }

    @ParameterizedTest
    @MethodSource("rotasProtegidas")
    void semTokenRecebe401(String rota) {
        given().get(rota).then().statusCode(401);
    }

    @ParameterizedTest
    @MethodSource("rotasProtegidas")
    void tokenInvalidoRecebe401(String rota) {
        given().header("Authorization", "Bearer isto.nao.e.um.jwt").get(rota).then().statusCode(401);
    }

    @Test
    void tokenComAssinaturaAdulteradaRecebe401() {
        // payload plausível, assinatura falsa
        String forjado = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9."
                + "eyJpc3MiOiJnZXN0b3ItbWVpIiwiaWQiOjEsImF1ZCI6Imdlc3Rvci1tZWktYXBwIn0."
                + "assinatura-falsa";
        given().header("Authorization", "Bearer " + forjado).get("/contas").then().statusCode(401);
    }

    @Test
    void escritaSemTokenTambemERecusada() {
        // corpos VÁLIDOS: a recusa tem de vir da falta de login, não da validação do corpo
        given().contentType("application/json")
                .body("{\"nome\":\"x\",\"tipo\":\"CORRENTE\",\"saldo\":10,\"tipoPessoa\":\"PF\"}")
                .post("/contas").then().statusCode(401);
        given().contentType("application/json")
                .body("{\"descricao\":\"x\",\"valor\":10,\"data\":\"2026-01-01\",\"tipo\":\"RECEITA\",\"tipoPessoa\":\"PF\"}")
                .post("/transacoes").then().statusCode(401);
        given().delete("/transacoes/1").then().statusCode(401);
        given().delete("/contas/1").then().statusCode(401);
    }

    @Test
    void respostasTrazemCabecalhosDeSeguranca() {
        given().get("/contas").then()
                .header("X-Content-Type-Options", equalTo("nosniff"));
    }
}
