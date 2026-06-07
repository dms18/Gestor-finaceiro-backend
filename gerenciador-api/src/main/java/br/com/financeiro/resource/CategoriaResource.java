package br.com.financeiro.resource;

import br.com.financeiro.dto.CategoriaDTO;
import br.com.financeiro.entity.Categoria;
import br.com.financeiro.entity.Usuario;
import br.com.financeiro.security.UserPrincipal;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Collectors;

@Path("/categorias")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Transactional
public class CategoriaResource {

    @Inject
    UserPrincipal userPrincipal;

    @GET
    public Response listar() {
        Long usuarioId = userPrincipal.getUserId();
        if (usuarioId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        List<Categoria> categorias = Categoria.list("usuario.id", usuarioId);
        List<CategoriaDTO> dtos = categorias.stream()
                .map(c -> new CategoriaDTO(c.id, c.nome, c.tipo.toString(), c.cor, c.icone, c.usuario.id))
                .collect(Collectors.toList());

        return Response.ok(dtos).build();
    }

    @POST
    public Response criar(CategoriaDTO dto) {
        Long usuarioId = userPrincipal.getUserId();
        if (usuarioId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Usuario usuario = Usuario.findById(usuarioId);
        if (usuario == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Categoria categoria = new Categoria();
        categoria.nome = dto.nome;
        categoria.tipo = Categoria.TipoCategoria.valueOf(dto.tipo);
        categoria.cor = dto.cor != null ? dto.cor : "#3880ff";
        categoria.icone = dto.icone != null ? dto.icone : "folder";
        categoria.usuario = usuario;
        categoria.persist();

        CategoriaDTO response = new CategoriaDTO(categoria.id, categoria.nome, categoria.tipo.toString(), categoria.cor, categoria.icone, categoria.usuario.id);
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    @PUT
    @Path("/{id}")
    public Response atualizar(@PathParam("id") Long id, CategoriaDTO dto) {
        Long usuarioId = userPrincipal.getUserId();
        if (usuarioId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Categoria categoria = Categoria.findById(id);
        if (categoria == null || !categoria.usuario.id.equals(usuarioId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        categoria.nome = dto.nome;
        categoria.tipo = Categoria.TipoCategoria.valueOf(dto.tipo);
        categoria.cor = dto.cor;
        categoria.icone = dto.icone;
        categoria.persist();

        CategoriaDTO response = new CategoriaDTO(categoria.id, categoria.nome, categoria.tipo.toString(), categoria.cor, categoria.icone, categoria.usuario.id);
        return Response.ok(response).build();
    }

    @DELETE
    @Path("/{id}")
    public Response deletar(@PathParam("id") Long id) {
        Long usuarioId = userPrincipal.getUserId();
        if (usuarioId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Categoria categoria = Categoria.findById(id);
        if (categoria == null || !categoria.usuario.id.equals(usuarioId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        categoria.delete();
        return Response.noContent().build();
    }
}
