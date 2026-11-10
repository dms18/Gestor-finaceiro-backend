package br.com.financeiro.resource;

import static br.com.financeiro.support.ApiTestSupport.SENHA;
import static br.com.financeiro.support.ApiTestSupport.como;
import static br.com.financeiro.support.ApiTestSupport.novoUsuario;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

import br.com.financeiro.support.ApiTestSupport.Sessao;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AuthResourceTest {

    private static Map<String, Object> cadastro(String email) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("nome", "Maria");
        corpo.put("email", email);
        corpo.put("senha", SENHA);
        corpo.put("perfil", "PF");
        corpo.put("aceiteTermos", true);
        return corpo;
    }

    private static String emailNovo() {
        return "auth" + System.nanoTime() + "@exemplo.com";
    }

    @Test
    void cadastroDevolveTokenEUsuarioSemExporSenha() {
        given().contentType(ContentType.JSON).body(cadastro(emailNovo()))
                .post("/auth/register")
                .then().statusCode(201)
                .body("token", notNullValue())
                .body("usuario.nome", equalTo("Maria"))
                .body("usuario.perfil", equalTo("PF"))
                .body(not(containsString("senhaHash")))
                .body(not(containsString(SENHA)));
    }

    @Test
    void emailDuplicadoERecusado() {
        String email = emailNovo();
        given().contentType(ContentType.JSON).body(cadastro(email)).post("/auth/register").then().statusCode(201);

        given().contentType(ContentType.JSON).body(cadastro(email)).post("/auth/register")
                .then().statusCode(400).body("message", equalTo("Email já cadastrado"));
    }

    @Test
    void cadastroExigeAceiteDosTermos() {
        Map<String, Object> corpo = cadastro(emailNovo());
        corpo.put("aceiteTermos", false);

        given().contentType(ContentType.JSON).body(corpo).post("/auth/register")
                .then().statusCode(400).body("message", containsString("Termos de Uso"));
    }

    @Test
    void cadastroValidaEmailESenha() {
        Map<String, Object> emailRuim = cadastro("nao-e-email");
        given().contentType(ContentType.JSON).body(emailRuim).post("/auth/register").then().statusCode(400);

        Map<String, Object> senhaCurta = cadastro(emailNovo());
        senhaCurta.put("senha", "12345");
        given().contentType(ContentType.JSON).body(senhaCurta).post("/auth/register").then().statusCode(400);
    }

    @Test
    void cadastroRecusaCpfECnpjInvalidos() {
        Map<String, Object> cpfRuim = cadastro(emailNovo());
        cpfRuim.put("cpf", "111.111.111-11");
        given().contentType(ContentType.JSON).body(cpfRuim).post("/auth/register")
                .then().statusCode(400).body("message", equalTo("CPF inválido"));

        Map<String, Object> cnpjRuim = cadastro(emailNovo());
        cnpjRuim.put("cnpj", "11.222.333/0001-82");
        given().contentType(ContentType.JSON).body(cnpjRuim).post("/auth/register")
                .then().statusCode(400).body("message", equalTo("CNPJ inválido"));
    }

    @Test
    void cadastroAceitaCpfValido() {
        Map<String, Object> corpo = cadastro(emailNovo());
        corpo.put("cpf", "529.982.247-25");
        given().contentType(ContentType.JSON).body(corpo).post("/auth/register").then().statusCode(201);
    }

    @Test
    void loginComCredenciaisCorretasEntregaTokenUtilizavel() {
        Sessao s = novoUsuario();

        String token = given().contentType(ContentType.JSON)
                .body(Map.of("email", s.email(), "senha", SENHA))
                .post("/auth/login")
                .then().statusCode(200).body("token", notNullValue())
                .extract().path("token");

        given().header("Authorization", "Bearer " + token).get("/contas").then().statusCode(200);
    }

    @Test
    void senhaErradaEEmailInexistenteRecebemARespostaIdentica_naoRevelaContas() {
        Sessao s = novoUsuario();

        String msgSenhaErrada = given().contentType(ContentType.JSON)
                .body(Map.of("email", s.email(), "senha", "errada123"))
                .post("/auth/login").then().statusCode(401).extract().path("message");
        String msgSemConta = given().contentType(ContentType.JSON)
                .body(Map.of("email", emailNovo(), "senha", "errada123"))
                .post("/auth/login").then().statusCode(401).extract().path("message");

        org.junit.jupiter.api.Assertions.assertEquals(msgSenhaErrada, msgSemConta);
    }

    @Test
    void apos5FalhasOLoginFicaBloqueadoComRetryAfter() {
        Sessao s = novoUsuario();
        Map<String, Object> errado = Map.of("email", s.email(), "senha", "errada123");

        for (int i = 0; i < 5; i++) {
            given().contentType(ContentType.JSON).body(errado).post("/auth/login").then().statusCode(401);
        }

        // até a senha CORRETA é barrada durante o bloqueio
        given().contentType(ContentType.JSON).body(Map.of("email", s.email(), "senha", SENHA))
                .post("/auth/login")
                .then().statusCode(429).header("Retry-After", notNullValue());
    }

    @Test
    void esqueciSenhaResponde200IgualParaEmailCadastradoEInexistente() {
        Sessao s = novoUsuario();

        String existente = given().contentType(ContentType.JSON).body(Map.of("email", s.email()))
                .post("/auth/esqueci-senha").then().statusCode(200).extract().asString();
        String inexistente = given().contentType(ContentType.JSON).body(Map.of("email", emailNovo()))
                .post("/auth/esqueci-senha").then().statusCode(200).extract().asString();

        org.junit.jupiter.api.Assertions.assertEquals(existente, inexistente);
    }

    @Test
    void redefinirSenhaComCodigoInvalidoERecusado() {
        Sessao s = novoUsuario();

        given().contentType(ContentType.JSON)
                .body(Map.of("email", s.email(), "codigo", "000000", "novaSenha", "NovaSenha@123"))
                .post("/auth/redefinir-senha")
                .then().statusCode(400);

        // a senha antiga continua valendo
        given().contentType(ContentType.JSON).body(Map.of("email", s.email(), "senha", SENHA))
                .post("/auth/login").then().statusCode(200);
    }

    @Test
    void perfilDoUsuarioAutenticadoNaoVazaCamposSensiveis() {
        Sessao s = novoUsuario();

        como(s).get("/usuarios/perfil")
                .then().statusCode(200)
                .body("email", equalTo(s.email()))
                .body(not(containsString("senhaHash")));
    }
}
