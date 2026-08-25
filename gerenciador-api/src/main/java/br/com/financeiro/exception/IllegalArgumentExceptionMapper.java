package br.com.financeiro.exception;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Trata valores inválidos de enum (ex.: Enum.valueOf com um "tipo"/"status"
 * desconhecido vindo do cliente) devolvendo 400 com mensagem limpa, evitando
 * um 500 com stack trace exposto.
 */
@Provider
public class IllegalArgumentExceptionMapper implements ExceptionMapper<IllegalArgumentException> {

    @Override
    public Response toResponse(IllegalArgumentException exception) {
        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity("{\"message\":\"Valor inválido em um dos campos enviados\"}")
                .build();
    }
}
