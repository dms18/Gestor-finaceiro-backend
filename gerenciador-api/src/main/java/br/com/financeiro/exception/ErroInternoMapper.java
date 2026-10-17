package br.com.financeiro.exception;

import io.quarkus.arc.ArcUndeclaredThrowableException;
import jakarta.persistence.PersistenceException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * Evita que falhas inesperadas (ex.: violação de integridade no banco) devolvam
 * stack trace ao cliente. Registra o erro no log com um identificador e responde
 * com uma mensagem genérica.
 */
@Provider
public class ErroInternoMapper implements ExceptionMapper<ArcUndeclaredThrowableException> {

    private static final Logger LOG = Logger.getLogger(ErroInternoMapper.class);

    @Override
    public Response toResponse(ArcUndeclaredThrowableException exception) {
        Throwable causa = exception.getCause();
        for (Throwable t = causa; t != null; t = t.getCause()) {
            if (t instanceof PersistenceException) {
                String id = UUID.randomUUID().toString();
                LOG.errorf(exception, "Violação de integridade/persistência [%s]", id);
                return Response.status(Response.Status.CONFLICT)
                        .type(MediaType.APPLICATION_JSON)
                        .entity("{\"message\":\"Não foi possível concluir: os dados conflitam com o que já existe.\",\"errorId\":\"" + id + "\"}")
                        .build();
            }
        }
        String id = UUID.randomUUID().toString();
        LOG.errorf(exception, "Erro interno [%s]", id);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON)
                .entity("{\"message\":\"Erro interno. Tente novamente.\",\"errorId\":\"" + id + "\"}")
                .build();
    }
}
