package br.com.financeiro.security;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limita pedidos de código de redefinição por e-mail ({@value #MAX_PEDIDOS} a cada
 * {@value #JANELA_MINUTOS} minutos), evitando spam de e-mails. Estado em memória.
 */
@ApplicationScoped
public class ResetRateLimiter {

    static final int MAX_PEDIDOS = 3;
    static final long JANELA_MINUTOS = 15;
    private static final int MAX_CHAVES = 10_000;

    private static final class Registro {
        int pedidos;
        Instant inicio = Instant.now();
    }

    private final ConcurrentHashMap<String, Registro> registros = new ConcurrentHashMap<>();

    /**
     * Registra um pedido.
     * @return 0 se permitido, ou os segundos restantes até poder pedir de novo.
     */
    public long registrar(String email) {
        if (registros.size() > MAX_CHAVES) {
            Instant limite = Instant.now().minus(Duration.ofMinutes(JANELA_MINUTOS));
            registros.entrySet().removeIf(e -> e.getValue().inicio.isBefore(limite));
        }
        Registro r = registros.computeIfAbsent(chave(email), k -> new Registro());
        synchronized (r) {
            Instant agora = Instant.now();
            if (Duration.between(r.inicio, agora).toMinutes() >= JANELA_MINUTOS) {
                r.pedidos = 0;
                r.inicio = agora;
            }
            if (r.pedidos >= MAX_PEDIDOS) {
                return Math.max(1, Duration.between(agora, r.inicio.plus(Duration.ofMinutes(JANELA_MINUTOS))).getSeconds());
            }
            r.pedidos++;
            return 0;
        }
    }

    private static String chave(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
