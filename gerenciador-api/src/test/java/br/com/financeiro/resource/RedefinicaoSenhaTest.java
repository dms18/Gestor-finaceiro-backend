package br.com.financeiro.resource;

import static br.com.financeiro.support.ApiTestSupport.SENHA;
import static br.com.financeiro.support.ApiTestSupport.como;
import static br.com.financeiro.support.ApiTestSupport.novoUsuario;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.financeiro.support.ApiTestSupport.Sessao;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Fluxo de redefinição de senha e as correções de segurança associadas: limite de tentativas atômico
 * (resistente a requisições simultâneas) e queda das sessões antigas após a troca da senha.
 */
@QuarkusTest
class RedefinicaoSenhaTest {

    private static final String NOVA_SENHA = "NovaSenha@2026";

    @Inject
    MockMailbox mailbox;

    @BeforeEach
    void limparCaixa() {
        mailbox.clear();
    }

    /** O e-mail é enviado de forma assíncrona: espera aparecer na caixa simulada e extrai o código de 6 dígitos. */
    private String codigoEnviadoPara(String email) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            List<Mail> enviados = mailbox.getMailsSentTo(email);
            if (!enviados.isEmpty()) {
                Matcher m = Pattern.compile("\\b(\\d{6})\\b").matcher(enviados.get(enviados.size() - 1).getText());
                assertTrue(m.find(), "o e-mail deve conter um código de 6 dígitos");
                return m.group(1);
            }
            Thread.sleep(50);
        }
        throw new AssertionError("nenhum e-mail de redefinição chegou para " + email);
    }

    private static void pedirCodigo(String email) {
        given().contentType(ContentType.JSON).body(Map.of("email", email))
                .post("/auth/esqueci-senha").then().statusCode(200);
    }

    private static int redefinir(String email, String codigo, String novaSenha) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("email", email, "codigo", codigo, "novaSenha", novaSenha))
                .post("/auth/redefinir-senha").then().extract().statusCode();
    }

    private static String codigoErradoDiferenteDe(String certo) {
        return certo.equals("000000") ? "000001" : "000000";
    }

    // ------------------------------------------------------------ fluxo básico

    @Test
    void codigoCorretoRedefineASenha_eASenhaAntigaDeixaDeValer() throws Exception {
        Sessao s = novoUsuario();
        pedirCodigo(s.email());
        String codigo = codigoEnviadoPara(s.email());

        assertEquals(200, redefinir(s.email(), codigo, NOVA_SENHA));

        given().contentType(ContentType.JSON).body(Map.of("email", s.email(), "senha", NOVA_SENHA))
                .post("/auth/login").then().statusCode(200);
        given().contentType(ContentType.JSON).body(Map.of("email", s.email(), "senha", SENHA))
                .post("/auth/login").then().statusCode(401);
    }

    @Test
    void codigoSoPodeSerUsadoUmaVez() throws Exception {
        Sessao s = novoUsuario();
        pedirCodigo(s.email());
        String codigo = codigoEnviadoPara(s.email());

        assertEquals(200, redefinir(s.email(), codigo, NOVA_SENHA));
        assertEquals(400, redefinir(s.email(), codigo, "OutraSenha@2026"));
    }

    @Test
    void novoPedidoInvalidaOCodigoAnterior() throws Exception {
        Sessao s = novoUsuario();
        pedirCodigo(s.email());
        String primeiro = codigoEnviadoPara(s.email());
        mailbox.clear();
        pedirCodigo(s.email());
        String segundo = codigoEnviadoPara(s.email());

        if (!primeiro.equals(segundo)) {
            assertEquals(400, redefinir(s.email(), primeiro, NOVA_SENHA), "o código antigo deixou de valer");
        }
        assertEquals(200, redefinir(s.email(), segundo, NOVA_SENHA));
    }

    // ------------------------------------------------- limite de tentativas

    @Test
    void depoisDe5PalpitesErradosOCodigoCertoTambemEBarrado() throws Exception {
        Sessao s = novoUsuario();
        pedirCodigo(s.email());
        String certo = codigoEnviadoPara(s.email());
        String errado = codigoErradoDiferenteDe(certo);

        for (int i = 0; i < 5; i++) {
            assertEquals(400, redefinir(s.email(), errado, NOVA_SENHA));
        }

        assertEquals(400, redefinir(s.email(), certo, NOVA_SENHA), "limite de tentativas esgotado");
        given().contentType(ContentType.JSON).body(Map.of("email", s.email(), "senha", SENHA))
                .post("/auth/login").then().statusCode(200); // a senha antiga segue valendo
    }

    @Test
    void rajadaDePalpitesSimultaneosNaoBurlaOLimite() throws Exception {
        Sessao s = novoUsuario();
        pedirCodigo(s.email());
        String certo = codigoEnviadoPara(s.email());
        String errado = codigoErradoDiferenteDe(certo);

        // 40 palpites errados ao mesmo tempo. Com o contador "lê, verifica, grava" cada um enxergava
        // o valor antigo e o limite de 5 nunca fechava; com o incremento atômico fecha exatamente.
        ExecutorService pool = Executors.newFixedThreadPool(20);
        try {
            List<Callable<Integer>> tarefas = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                tarefas.add(() -> redefinir(s.email(), errado, NOVA_SENHA));
            }
            for (var f : pool.invokeAll(tarefas, 120, TimeUnit.SECONDS)) {
                assertEquals(400, f.get());
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(400, redefinir(s.email(), certo, NOVA_SENHA),
                "depois da rajada o código correto já não pode ser aceito");
    }

    // ------------------------------------------------ sessões antigas

    @Test
    void tokenEmitidoAntesDaRedefinicaoDeixaDeValer() throws Exception {
        Sessao s = novoUsuario();
        como(s).get("/contas").then().statusCode(200); // o token funciona

        pedirCodigo(s.email());
        assertEquals(200, redefinir(s.email(), codigoEnviadoPara(s.email()), NOVA_SENHA));

        como(s).get("/contas").then().statusCode(401); // token antigo derrubado
        como(s).get("/usuarios/perfil").then().statusCode(401);
    }

    @Test
    void novoLoginAposARedefinicaoRecebeTokenQueFunciona() throws Exception {
        Sessao s = novoUsuario();
        pedirCodigo(s.email());
        assertEquals(200, redefinir(s.email(), codigoEnviadoPara(s.email()), NOVA_SENHA));

        String novoToken = given().contentType(ContentType.JSON).body(Map.of("email", s.email(), "senha", NOVA_SENHA))
                .post("/auth/login").then().statusCode(200).body("token", notNullValue()).extract().path("token");

        given().header("Authorization", "Bearer " + novoToken).get("/contas").then().statusCode(200);
    }

    @Test
    void sessaoDeOutroUsuarioNaoECaidaPelaRedefinicaoDeUmUsuario() throws Exception {
        Sessao quemRedefine = novoUsuario();
        Sessao outro = novoUsuario();

        pedirCodigo(quemRedefine.email());
        assertEquals(200, redefinir(quemRedefine.email(), codigoEnviadoPara(quemRedefine.email()), NOVA_SENHA));

        como(outro).get("/contas").then().statusCode(200);
    }

    @Test
    void emailInexistenteNaoGeraEmailENaoRevelaNada() throws Exception {
        String email = "naoexiste" + System.nanoTime() + "@exemplo.com";

        pedirCodigo(email);

        Thread.sleep(300);
        assertEquals(0, mailbox.getMailsSentTo(email).size());
        assertNotNull(email);
    }

    // ------------------------------------------- bloqueio de login por IP

    @Test
    void bloqueioDeLoginContinuaFuncionandoParaOMesmoIp() {
        Sessao s = novoUsuario();
        Map<String, Object> errado = Map.of("email", s.email(), "senha", "errada123");

        for (int i = 0; i < 5; i++) {
            given().contentType(ContentType.JSON).body(errado).post("/auth/login").then().statusCode(401);
        }

        given().contentType(ContentType.JSON).body(Map.of("email", s.email(), "senha", SENHA))
                .post("/auth/login").then().statusCode(429).header("Retry-After", notNullValue());
    }

    @Test
    void emailInexistenteResponde401IgualEContaParaOBloqueio() {
        String email = "fantasma" + System.nanoTime() + "@exemplo.com";
        Map<String, Object> corpo = Map.of("email", email, "senha", "qualquer123");

        for (int i = 0; i < 5; i++) {
            given().contentType(ContentType.JSON).body(corpo).post("/auth/login")
                    .then().statusCode(401).body("message", equalTo("Email ou senha incorretos"));
        }
        given().contentType(ContentType.JSON).body(corpo).post("/auth/login").then().statusCode(429);
    }
}
