package br.com.financeiro.security;

import br.com.financeiro.entity.PasswordResetToken;
import br.com.financeiro.entity.Usuario;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Reserva, de forma atômica, uma tentativa de uso do código de redefinição de senha.
 *
 * <p>O contador precisa ser incrementado pelo próprio banco ({@code tentativas = tentativas + 1 ... where
 * tentativas < MAX}): ler o valor, verificar o código e só depois gravar permitia que requisições
 * simultâneas lessem o mesmo valor antigo e ganhassem um palpite cada uma, esvaziando o limite.
 *
 * <p>Roda em transação própria e curta (libera a linha sem esperar o bcrypt) e DEVE ser chamada antes de
 * a transação do endpoint ler qualquer coisa: com o isolamento de snapshot do MariaDB 11, se a transação
 * principal já tivesse lido o token, gravar nele depois (o UPDATE em massa de "usado") falharia com
 * "Record has changed since last read" (erro 1020), pois esta transação já alterou a linha.
 */
@ApplicationScoped
public class ResetAttemptService {

    /**
     * Localiza o código ativo do e-mail e reserva um palpite.
     *
     * @return o hash do código a ser conferido pelo chamador; null se não há usuário ativo, código ativo,
     *         o código expirou ou o limite de tentativas já foi atingido.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public String reservarTentativa(String email) {
        Usuario usuario = Usuario.findByEmail(email);
        if (usuario == null || !usuario.ativo) {
            return null;
        }
        PasswordResetToken token = PasswordResetToken.findAtivo(usuario.email);
        if (token == null || token.expirado()) {
            return null;
        }
        int linhas = PasswordResetToken.update(
                "tentativas = tentativas + 1 where id = ?1 and usado = false and tentativas < ?2",
                token.id, PasswordResetToken.MAX_TENTATIVAS);
        return linhas == 1 ? token.codigoHash : null;
    }
}
