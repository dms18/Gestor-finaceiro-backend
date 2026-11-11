package br.com.financeiro.resource;

import static br.com.financeiro.support.ApiTestSupport.SENHA;
import static br.com.financeiro.support.ApiTestSupport.como;
import static br.com.financeiro.support.ApiTestSupport.novoUsuario;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import br.com.financeiro.entity.Usuario;
import br.com.financeiro.support.ApiTestSupport.Sessao;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AdminResourceTest {

    private static Sessao novoAdmin() {
        Sessao s = novoUsuario();
        QuarkusTransaction.requiringNew().run(() -> Usuario.update("icAdm = true where email = ?1", s.email()));
        return s;
    }

    @Test
    void semTokenRecebe401() {
        given().get("/admin/resumo").then().statusCode(401);
    }

    @Test
    void usuarioComumRecebe403() {
        como(novoUsuario()).get("/admin/resumo").then().statusCode(403);
    }

    @Test
    void administradorVeOsIndicadores() {
        como(novoAdmin()).get("/admin/resumo")
                .then().statusCode(200)
                .body("usuarios.total", greaterThanOrEqualTo(1))
                .body("usuarios.porPerfil.AMBOS", greaterThanOrEqualTo(1))
                .body("atividade.transacoes", notNullValue())
                .body("cadastrosPorDia", hasSize(30))
                .body("usuariosRecentes.size()", greaterThanOrEqualTo(1));
    }

    @Test
    void infraExigeLoginEPermissao() {
        given().get("/admin/infra").then().statusCode(401);
        como(novoUsuario()).get("/admin/infra").then().statusCode(403);
        como(novoAdmin()).header("Origin", "http://localhost").get("/admin/infra").then().statusCode(403);
    }

    @Test
    void administradorVeInfraMesmoSemDocker() {
        como(novoAdmin()).get("/admin/infra")
                .then().statusCode(200)
                .body("vps", notNullValue())
                .body("vps.discoTotalBytes", notNullValue())
                .body("containersDisponiveis", equalTo(false))
                .body("containers", hasSize(0));
    }

    @Test
    void appInstaladoNaoAcessaMesmoSendoAdministrador() {
        como(novoAdmin()).header("Origin", "http://localhost").get("/admin/resumo")
                .then().statusCode(403);
    }

    @Test
    void loginInformaSeEAdministrador() {
        Sessao admin = novoAdmin();
        Sessao comum = novoUsuario();

        given().contentType(ContentType.JSON).body(Map.of("email", admin.email(), "senha", SENHA))
                .post("/auth/login").then().statusCode(200).body("usuario.admin", equalTo(true));
        given().contentType(ContentType.JSON).body(Map.of("email", comum.email(), "senha", SENHA))
                .post("/auth/login").then().statusCode(200).body("usuario.admin", equalTo(false));
    }

    @Test
    void perfilNaoPermiteSeAutopromoverAAdministrador() {
        Sessao s = novoUsuario();

        como(s).body(Map.of("nome", "Quero ser admin", "perfil", "AMBOS", "admin", true))
                .put("/usuarios/perfil");

        como(s).get("/usuarios/perfil").then().statusCode(200).body("admin", equalTo(false));
        como(s).get("/admin/resumo").then().statusCode(403);
    }
}
