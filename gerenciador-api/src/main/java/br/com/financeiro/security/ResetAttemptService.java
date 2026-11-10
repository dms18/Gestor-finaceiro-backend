package br.com.financeiro.security;

import br.com.financeiro.entity.PasswordResetToken;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Reserva, de forma atômica, uma tentativa de uso do código de redefinição de senha.
 *
 * <p>O contador precisa ser incrementado pelo próprio banco ({@code tentativas = tentativas + 1 ... where
 * tentativas < MAX}): ler o valor, verificar o código e só depois gravar permitia que requisições
 * simultâneas lessem o mesmo valor antigo e ganhassem um palpite cada uma, esvaziando o limite.
 * Roda em transação própria e curta para liberar a linha logo, sem esperar o bcrypt da requisição.
 */
@ApplicationScoped
public class ResetAttemptService {

    /** @return true se esta requisição ganhou o direito a um palpite; false se o limite já foi atingido. */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public boolean reservarTentativa(Long tokenId) {
        int linhas = PasswordResetToken.update(
                "tentativas = tentativas + 1 where id = ?1 and usado = false and tentativas < ?2",
                tokenId, PasswordResetToken.MAX_TENTATIVAS);
        return linhas == 1;
    }
}
