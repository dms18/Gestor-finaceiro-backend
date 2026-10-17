package br.com.financeiro.security;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limita tentativas de login por e-mail para dificultar força bruta.
 * Após {@value #MAX_FALHAS} falhas dentro da janela, o e-mail fica bloqueado por
 * {@value #BLOQUEIO_MINUTOS} minutos. Vale também para e-mails inexistentes,
 * para não revelar quais contas existem. Estado em memória (uma instância).
 */
@ApplicationScoped
public class LoginAttemptService {

    static final int MAX_FALHAS = 5;
    static final long BLOQUEIO_MINUTOS = 5;
    private static final int MAX_CHAVES = 10_000;

    private static final class Registro {
        int falhas;
        Instant primeiraFalha = Instant.now();
        Instant bloqueadoAte;
    }

    private final ConcurrentHashMap<String, Registro> registros = new ConcurrentHashMap<>();

    /** @return segundos restantes de bloqueio, ou 0 se o login pode ser tentado. */
    public long segundosBloqueado(String email) {
        Registro r = registros.get(chave(email));
        if (r == null) {
            return 0;
        }
        synchronized (r) {
            if (r.bloqueadoAte == null) {
                return 0;
            }
            long restante = Duration.between(Instant.now(), r.bloqueadoAte).getSeconds();
            if (restante <= 0) {
                registros.remove(chave(email), r);
                return 0;
            }
            return restante;
        }
    }

    public void registrarFalha(String email) {
        if (registros.size() > MAX_CHAVES) {
            limparExpirados();
        }
        Registro r = registros.computeIfAbsent(chave(email), k -> new Registro());
        synchronized (r) {
            Instant agora = Instant.now();
            if (Duration.between(r.primeiraFalha, agora).toMinutes() >= BLOQUEIO_MINUTOS) {
                r.falhas = 0;
                r.primeiraFalha = agora;
            }
            r.falhas++;
            if (r.falhas >= MAX_FALHAS) {
                r.bloqueadoAte = agora.plus(Duration.ofMinutes(BLOQUEIO_MINUTOS));
            }
        }
    }

    public void registrarSucesso(String email) {
        registros.remove(chave(email));
    }

    private void limparExpirados() {
        Instant limite = Instant.now().minus(Duration.ofMinutes(BLOQUEIO_MINUTOS));
        registros.entrySet().removeIf(e -> e.getValue().primeiraFalha.isBefore(limite)
                && (e.getValue().bloqueadoAte == null || e.getValue().bloqueadoAte.isBefore(Instant.now())));
    }

    private static String chave(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
