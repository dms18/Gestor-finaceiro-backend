package br.com.financeiro.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordServiceTest {

    private final PasswordService service = new PasswordService();

    @Test
    void hashNaoGuardaASenhaEmTextoPuro() {
        String hash = service.hash("Senha@123");

        assertNotEquals("Senha@123", hash);
        assertFalse(hash.contains("Senha@123"));
        assertTrue(hash.startsWith("$2"), "deve ser bcrypt");
        assertTrue(hash.contains("$12$"), "custo 12");
    }

    @Test
    void verificaSenhaCorretaEErrada() {
        String hash = service.hash("Senha@123");

        assertTrue(service.verify("Senha@123", hash));
        assertFalse(service.verify("senha@123", hash));
        assertFalse(service.verify("", hash));
    }

    @Test
    void cadaHashTemSaltDiferente() {
        String a = service.hash("Senha@123");
        String b = service.hash("Senha@123");

        assertNotEquals(a, b);
        assertTrue(service.verify("Senha@123", a));
        assertTrue(service.verify("Senha@123", b));
    }
}
