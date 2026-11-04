package br.com.financeiro.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** Código de uso único para redefinição de senha. Guarda apenas o hash do código. */
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken extends PanacheEntity {

    public static final int MAX_TENTATIVAS = 5;
    public static final int VALIDADE_MINUTOS = 15;

    @Column(nullable = false)
    public String email;

    @Column(nullable = false)
    public String codigoHash;

    public LocalDateTime expiraEm;

    public int tentativas = 0;

    public boolean usado = false;

    public LocalDateTime criadoEm = LocalDateTime.now();

    /** Token ativo mais recente do e-mail (não usado), ou null. */
    public static PasswordResetToken findAtivo(String email) {
        return find("email = ?1 and usado = false order by id desc", email).firstResult();
    }

    /** Invalida todos os códigos pendentes do e-mail. */
    public static void invalidarTodos(String email) {
        update("usado = true where email = ?1 and usado = false", email);
    }

    public boolean expirado() {
        return expiraEm == null || LocalDateTime.now().isAfter(expiraEm);
    }
}
