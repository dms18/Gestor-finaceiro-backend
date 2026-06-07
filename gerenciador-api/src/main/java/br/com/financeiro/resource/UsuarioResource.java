package br.com.financeiro.resource;

import br.com.financeiro.dto.UsuarioDTO;
import br.com.financeiro.entity.Usuario;
import br.com.financeiro.security.UserPrincipal;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/usuarios")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Transactional
public class UsuarioResource {

    @Inject
    UserPrincipal userPrincipal;

    @GET
    @Path("/perfil")
    public Response getPerfil() {
        Long usuarioId = userPrincipal.getUserId();
        if (usuarioId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Usuario usuario = Usuario.findById(usuarioId);
        if (usuario == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        UsuarioDTO dto = new UsuarioDTO(
                usuario.id, usuario.nome, usuario.email,
                usuario.cpf, usuario.cnpj, usuario.perfil.toString(), usuario.ativo
        );
        return Response.ok(dto).build();
    }

    @PUT
    @Path("/perfil")
    public Response updatePerfil(UsuarioDTO dto) {
        Long usuarioId = userPrincipal.getUserId();
        if (usuarioId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Usuario usuario = Usuario.findById(usuarioId);
        if (usuario == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        usuario.nome = dto.nome;
        usuario.cpf = dto.cpf;
        usuario.cnpj = dto.cnpj;
        usuario.persist();

        UsuarioDTO response = new UsuarioDTO(
                usuario.id, usuario.nome, usuario.email,
                usuario.cpf, usuario.cnpj, usuario.perfil.toString(), usuario.ativo
        );
        return Response.ok(response).build();
    }
}
