package br.com.financeiro.resource;

import br.com.financeiro.dto.ContaDTO;
import br.com.financeiro.entity.Conta;
import br.com.financeiro.entity.Usuario;
import br.com.financeiro.security.UserPrincipal;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Collectors;

@Path("/contas")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Transactional
public class ContaResource {

    @Inject
    UserPrincipal userPrincipal;

    @GET
    public Response listar() {
        Long usuarioId = userPrincipal.getUserId();
        if (usuarioId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        List<Conta> contas = Conta.list("usuario.id", usuarioId);
        List<ContaDTO> dtos = contas.stream()
                .map(c -> new ContaDTO(c.id, c.nome, c.tipo.toString(), c.saldo, c.tipoPessoa.toString(), c.usuario.id))
                .collect(Collectors.toList());

        return Response.ok(dtos).build();
    }

    @POST
    public Response criar(ContaDTO dto) {
        Long usuarioId = userPrincipal.getUserId();
        if (usuarioId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Usuario usuario = Usuario.findById(usuarioId);
        if (usuario == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Conta conta = new Conta();
        conta.nome = dto.nome;
        conta.tipo = Conta.TipoConta.valueOf(dto.tipo);
        conta.saldo = dto.saldo;
        conta.tipoPessoa = Conta.TipoPessoa.valueOf(dto.tipoPessoa);
        conta.usuario = usuario;
        conta.persist();

        ContaDTO response = new ContaDTO(conta.id, conta.nome, conta.tipo.toString(), conta.saldo, conta.tipoPessoa.toString(), conta.usuario.id);
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    @PUT
    @Path("/{id}")
    public Response atualizar(@PathParam("id") Long id, ContaDTO dto) {
        Long usuarioId = userPrincipal.getUserId();
        if (usuarioId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Conta conta = Conta.findById(id);
        if (conta == null || !conta.usuario.id.equals(usuarioId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        conta.nome = dto.nome;
        conta.tipo = Conta.TipoConta.valueOf(dto.tipo);
        conta.saldo = dto.saldo;
        conta.persist();

        ContaDTO response = new ContaDTO(conta.id, conta.nome, conta.tipo.toString(), conta.saldo, conta.tipoPessoa.toString(), conta.usuario.id);
        return Response.ok(response).build();
    }

    @DELETE
    @Path("/{id}")
    public Response deletar(@PathParam("id") Long id) {
        Long usuarioId = userPrincipal.getUserId();
        if (usuarioId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Conta conta = Conta.findById(id);
        if (conta == null || !conta.usuario.id.equals(usuarioId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        conta.delete();
        return Response.noContent().build();
    }
}
