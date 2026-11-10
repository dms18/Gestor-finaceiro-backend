package br.com.financeiro.security;

import at.favre.lib.crypto.bcrypt.BCrypt;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PasswordService {

    /** Hash fixo, do mesmo custo dos reais, usado só para gastar o mesmo tempo quando a conta não existe. */
    private static final String HASH_FICTICIO = BCrypt.withDefaults().hashToString(12, "senha-ficticia".toCharArray());

    /**
     * Executa uma verificação bcrypt sem efeito algum. Chamado quando o e-mail não existe, para que a
     * resposta demore o mesmo que a de um e-mail cadastrado e o tempo não revele quais contas existem.
     */
    public void gastarTempoComoSeVerificasse(String plainPassword) {
        BCrypt.verifyer().verify(plainPassword == null ? new char[0] : plainPassword.toCharArray(), HASH_FICTICIO);
    }

    public String hash(String plainPassword) {
        return BCrypt.withDefaults().hashToString(12, plainPassword.toCharArray());
    }

    public boolean verify(String plainPassword, String hashedPassword) {
        BCrypt.Result result = BCrypt.verifyer().verify(plainPassword.toCharArray(), hashedPassword);
        return result.verified;
    }
}
