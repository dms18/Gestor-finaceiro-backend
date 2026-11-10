package br.com.financeiro.security;

import br.com.financeiro.entity.Usuario;
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
            if (claim == null) {
                return null;
            }
            Long id = Long.valueOf(claim.toString());
            return sessaoVigente(id) ? id : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * O token só vale se o usuário ainda existe, está ativo e a versão de sessão do token é a atual.
     * Assim a redefinição de senha derruba as sessões já emitidas (um token roubado não sobrevive a ela).
     * Tokens antigos, sem o claim "ver", equivalem à versão 0.
     */
    private boolean sessaoVigente(Long id) {
        Usuario usuario = Usuario.findById(id);
        if (usuario == null || !usuario.ativo) {
            return false;
        }
        Object ver = jwt.getClaim("ver");
        int versaoDoToken = ver == null ? 0 : Integer.parseInt(ver.toString());
        int versaoAtual = usuario.tokenVersao == null ? 0 : usuario.tokenVersao;
        return versaoDoToken == versaoAtual;
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
