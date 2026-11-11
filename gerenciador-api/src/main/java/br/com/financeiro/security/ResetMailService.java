package br.com.financeiro.security;

import br.com.financeiro.entity.PasswordResetToken;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.reactive.ReactiveMailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/** Envia o e-mail com o código de redefinição de senha. */
@ApplicationScoped
public class ResetMailService {

    private static final Logger LOG = Logger.getLogger(ResetMailService.class);

    @Inject
    ReactiveMailer mailer;

    /**
     * Envio assíncrono: a resposta HTTP não espera o servidor SMTP, então o tempo de resposta não revela
     * se o e-mail existe. Falhas de envio são apenas registradas.
     */
    public void enviarCodigo(String email, String nome, String codigo) {
        String texto = "Olá, " + nome + "!\n\n"
                + "Seu código para redefinir a senha do Gestor Financeiro é: " + codigo + "\n\n"
                + "Ele é válido por " + PasswordResetToken.VALIDADE_MINUTOS + " minutos. "
                + "Se você não pediu a redefinição, ignore este e-mail.";
        String html = montarHtml(nome, codigo);
        mailer.send(Mail.withHtml(email, "Código para redefinir sua senha", html).setText(texto))
                .subscribe().with(
                        ok -> { },
                        e -> LOG.error("Falha ao enviar e-mail de redefinição de senha", e));
    }

    private static String escapar(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** Layout no padrão visual do app (teal petróleo, cartão branco, tipografia limpa). CSS inline p/ clientes de e-mail. */
    private static String montarHtml(String nome, String codigo) {
        String digitos = codigo.chars()
                .mapToObj(c -> "<td style=\"width:40px;height:52px;background:#f0fdfa;border:1px solid #99f6e4;"
                        + "border-radius:8px;font-size:26px;font-weight:700;color:#0f766e;text-align:center;\">"
                        + (char) c + "</td>")
                .collect(java.util.stream.Collectors.joining("<td style=\"width:6px\"></td>"));
        return "<!DOCTYPE html><html lang=\"pt-BR\"><body style=\"margin:0;padding:0;background:#f1f5f9;"
                + "font-family:-apple-system,'Segoe UI',Roboto,Arial,sans-serif;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr><td align=\"center\" style=\"padding:24px 12px;\">"
                + "<table role=\"presentation\" width=\"480\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"max-width:480px;width:100%;background:#ffffff;border-radius:16px;overflow:hidden;\">"
                + "<tr><td style=\"background:#0f766e;padding:20px 28px;color:#ffffff;font-size:18px;font-weight:600;\">"
                + "Gestor Financeiro</td></tr>"
                + "<tr><td style=\"padding:28px;color:#0f172a;font-size:15px;line-height:1.5;\">"
                + "<h1 style=\"margin:0 0 12px;font-size:22px;color:#0f172a;\">Redefinir sua senha</h1>"
                + "<p style=\"margin:0 0 20px;\">Olá, " + escapar(nome) + "! Use o código abaixo para criar uma nova senha:</p>"
                + "<table role=\"presentation\" align=\"center\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:0 auto 20px;\"><tr>"
                + digitos + "</tr></table>"
                + "<p style=\"margin:0 0 8px;color:#475569;font-size:13px;\">O código é válido por "
                + PasswordResetToken.VALIDADE_MINUTOS + " minutos.</p>"
                + "<p style=\"margin:0;color:#475569;font-size:13px;\">Se você não pediu a redefinição, ignore este e-mail: "
                + "sua senha continua a mesma.</p>"
                + "</td></tr></table></td></tr></table></body></html>";
    }
}
