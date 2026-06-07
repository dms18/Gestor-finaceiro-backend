package br.com.financeiro.security;

import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.Instant;

@ApplicationScoped
public class JwtService {

    @ConfigProperty(name = "mp.jwt.verify.issuer", defaultValue = "gestor-mei")
    String issuer;

    @ConfigProperty(name = "mp.jwt.verify.audiences", defaultValue = "gestor-mei-app")
    String audience;

    public String generateToken(Long usuarioId, String email) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(Duration.ofHours(24));

        return Jwt.issuer(issuer)
                .audience(audience)
                .subject(email)
                .claim("id", usuarioId)
                .claim("email", email)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .sign();
    }

    public long getExpirationTime() {
        return Duration.ofHours(24).toMillis();
    }
}
