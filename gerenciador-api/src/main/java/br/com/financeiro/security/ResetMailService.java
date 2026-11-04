package br.com.financeiro.security;

import br.com.financeiro.entity.PasswordResetToken;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/** Envia o e-mail com o código de redefinição de senha. */
@ApplicationScoped
public class ResetMailService {

    private static final Logger LOG = Logger.getLogger(ResetMailService.class);

    @Inject
    Mailer mailer;

    /** Falhas de envio são apenas registradas, para não revelar se o e-mail existe. */
    public void enviarCodigo(String email, String nome, String codigo) {
        String texto = "Olá, " + nome + "!\n\n"
                + "Seu código para redefinir a senha do Gestor Financeiro é: " + codigo + "\n\n"
                + "Ele é válido por " + PasswordResetToken.VALIDADE_MINUTOS + " minutos. "
                + "Se você não pediu a redefinição, ignore este e-mail.";
        try {
            mailer.send(Mail.withText(email, "Código para redefinir sua senha", texto));
        } catch (RuntimeException e) {
            LOG.error("Falha ao enviar e-mail de redefinição de senha", e);
        }
    }
}
