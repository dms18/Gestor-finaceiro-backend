package br.com.financeiro.security;

import org.eclipse.microprofile.jwt.JsonWebToken;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

@RequestScoped
public class UserPrincipal {

    @Inject
    JsonWebToken jwt;

    public Long getUserId() {
        if (!isAuthenticated()) {
            return null;
        }
        try {
            Object claim = jwt.getClaim("id");
            if (claim != null) {
                return Long.valueOf(claim.toString());
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    public String getEmail() {
        if (!isAuthenticated()) {
            return null;
        }
        Object claim = jwt.getClaim("email");
        if (claim != null) {
            return claim.toString();
        }
        return jwt.getName();
    }

    public boolean isAuthenticated() {
        return jwt != null && jwt.getName() != null;
    }
}
