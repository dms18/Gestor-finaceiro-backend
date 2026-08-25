package br.com.financeiro.resource;

import br.com.financeiro.dto.ContaDTO;
import br.com.financeiro.entity.Conta;
import br.com.financeiro.entity.Usuario;
import br.com.financeiro.security.UserPrincipal;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Collectors;

@Path("/contas")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
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
                .map(this::toDTO)
                .collect(Collectors.toList());

        return Response.ok(dtos).build();
    }

    @POST
    @Transactional
    public Response criar(@Valid ContaDTO dto) {
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
        conta.instituicao = dto.instituicao;
        conta.numeroConta = dto.numeroConta;
        conta.tipo = Conta.TipoConta.valueOf(dto.tipo);
        conta.saldo = dto.saldo;
        conta.tipoPessoa = Conta.TipoPessoa.valueOf(dto.tipoPessoa);
        conta.usuario = usuario;
        conta.persist();

        return Response.status(Response.Status.CREATED).entity(toDTO(conta)).build();
    }

    @PUT
    @Transactional
    @Path("/{id}")
    public Response atualizar(@PathParam("id") Long id, @Valid ContaDTO dto) {
        Long usuarioId = userPrincipal.getUserId();
        if (usuarioId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Conta conta = Conta.findById(id);
        if (conta == null || !conta.usuario.id.equals(usuarioId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        conta.nome = dto.nome;
        conta.instituicao = dto.instituicao;
        conta.numeroConta = dto.numeroConta;
        conta.tipo = Conta.TipoConta.valueOf(dto.tipo);
        conta.saldo = dto.saldo;
        conta.tipoPessoa = Conta.TipoPessoa.valueOf(dto.tipoPessoa);
        conta.persist();

        return Response.ok(toDTO(conta)).build();
    }

    @DELETE
    @Transactional
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

    private ContaDTO toDTO(Conta conta) {
        return new ContaDTO(
                conta.id, conta.nome, conta.instituicao, conta.numeroConta,
                conta.tipo.toString(), conta.saldo, conta.tipoPessoa.toString(), conta.usuario.id
        );
    }
}
