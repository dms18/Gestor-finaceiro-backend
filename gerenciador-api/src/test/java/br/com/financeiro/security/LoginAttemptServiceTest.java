package br.com.financeiro.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoginAttemptServiceTest {

    private static final String IP_A = "203.0.113.10";
    private static final String IP_B = "198.51.100.20";

    private LoginAttemptService service;

    @BeforeEach
    void novo() {
        service = new LoginAttemptService();
    }

    private void falhar(String email, String ip, int vezes) {
        for (int i = 0; i < vezes; i++) {
            service.registrarFalha(email, ip);
        }
    }

    @Test
    void semFalhasNaoHaBloqueio() {
        assertEquals(0, service.segundosBloqueado("a@b.com", IP_A));
    }

    @Test
    void abaixoDoLimiteNaoBloqueia() {
        falhar("a@b.com", IP_A, LoginAttemptService.MAX_FALHAS - 1);
        assertEquals(0, service.segundosBloqueado("a@b.com", IP_A));
    }

    @Test
    void noLimiteBloqueiaAqueleIpPorAlgunsMinutos() {
        falhar("a@b.com", IP_A, LoginAttemptService.MAX_FALHAS);

        long restante = service.segundosBloqueado("a@b.com", IP_A);
        assertTrue(restante > 0, "deve estar bloqueado");
        assertTrue(restante <= LoginAttemptService.BLOQUEIO_MINUTOS * 60);
    }

    @Test
    void ataqueDeOutroIpNaoTrancaODonoDaConta() {
        // O atacante erra a senha da vítima muitas vezes, mas de um único IP...
        falhar("vitima@b.com", IP_B, LoginAttemptService.MAX_FALHAS);

        assertTrue(service.segundosBloqueado("vitima@b.com", IP_B) > 0, "o IP do atacante fica bloqueado");
        // ...e o dono, vindo de outro IP, continua conseguindo entrar.
        assertEquals(0, service.segundosBloqueado("vitima@b.com", IP_A));
    }

    @Test
    void forcaBrutaDistribuidaEntreVariosIpsBloqueiaOEmailTodo() {
        // 25 falhas espalhadas em IPs diferentes (4 por IP, abaixo do limite por IP)
        int total = LoginAttemptService.MAX_FALHAS_POR_EMAIL;
        for (int i = 0; i < total; i++) {
            service.registrarFalha("alvo@b.com", "192.0.2." + (i / 4));
        }

        assertTrue(service.segundosBloqueado("alvo@b.com", "192.0.2.250") > 0,
                "o e-mail fica bloqueado em qualquer IP");
    }

    @Test
    void trancarODonoExigeMuitasMaisRequisicoesDoQueAntes() {
        // Antes bastavam 5 falhas para trancar qualquer pessoa; agora um único IP não consegue isso.
        falhar("alvo@b.com", IP_B, LoginAttemptService.MAX_FALHAS);

        assertEquals(0, service.segundosBloqueado("alvo@b.com", IP_A));
        assertTrue(LoginAttemptService.MAX_FALHAS_POR_EMAIL > LoginAttemptService.MAX_FALHAS * 3);
    }

    @Test
    void loginComSucessoZeraAsFalhasDoParEDoEmail() {
        falhar("a@b.com", IP_A, LoginAttemptService.MAX_FALHAS - 1);
        service.registrarSucesso("a@b.com", IP_A);
        falhar("a@b.com", IP_A, LoginAttemptService.MAX_FALHAS - 1);

        assertEquals(0, service.segundosBloqueado("a@b.com", IP_A));
    }

    @Test
    void sucessoLiberaUmParJaBloqueado() {
        falhar("a@b.com", IP_A, LoginAttemptService.MAX_FALHAS);
        service.registrarSucesso("a@b.com", IP_A);

        assertEquals(0, service.segundosBloqueado("a@b.com", IP_A));
    }

    @Test
    void redefinirSenhaLiberaOEmailEmTodosOsIps() {
        falhar("a@b.com", IP_A, LoginAttemptService.MAX_FALHAS);
        falhar("a@b.com", IP_B, LoginAttemptService.MAX_FALHAS);

        service.liberarEmail("a@b.com");

        assertEquals(0, service.segundosBloqueado("a@b.com", IP_A));
        assertEquals(0, service.segundosBloqueado("a@b.com", IP_B));
    }

    @Test
    void emailsSaoIndependentes() {
        falhar("a@b.com", IP_A, LoginAttemptService.MAX_FALHAS);

        assertTrue(service.segundosBloqueado("a@b.com", IP_A) > 0);
        assertEquals(0, service.segundosBloqueado("outro@b.com", IP_A));
    }

    @Test
    void ignoraMaiusculasEEspacosNoEmail() {
        falhar("  Maria@Exemplo.COM ", IP_A, LoginAttemptService.MAX_FALHAS);

        assertTrue(service.segundosBloqueado("maria@exemplo.com", IP_A) > 0);
    }

    @Test
    void emailInexistenteTambemBloqueia_naoRevelaQuaisContasExistem() {
        falhar("nao-existe@exemplo.com", IP_A, LoginAttemptService.MAX_FALHAS);

        assertTrue(service.segundosBloqueado("nao-existe@exemplo.com", IP_A) > 0);
    }

    @Test
    void entradasNulasNaoQuebram() {
        service.registrarFalha(null, null);
        assertEquals(0, service.segundosBloqueado(null, null));
    }
}
