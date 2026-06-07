package br.com.financeiro.resource;

import br.com.financeiro.dto.InvestimentoDTO;
import br.com.financeiro.entity.Investimento;
import br.com.financeiro.entity.Usuario;
import br.com.financeiro.security.UserPrincipal;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Collectors;

@Path("/investimentos")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Transactional
public class InvestimentoResource {

    @Inject
    UserPrincipal userPrincipal;

    @GET
    public Response listar(@QueryParam("status") String status) {
        Long usuarioId = userPrincipal.getUserId();
        if (usuarioId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        String query = "usuario.id = ?1";
        List<Investimento> investimentos;

        if (status != null) {
            query += " and status = ?2";
            investimentos = Investimento.list(query, usuarioId, Investimento.StatusInvestimento.valueOf(status));
        } else {
            investimentos = Investimento.list(query, usuarioId);
        }

        List<InvestimentoDTO> dtos = investimentos.stream()
                .map(i -> new InvestimentoDTO(i.id, i.titulo, i.descricao, i.valorEstimado, i.retornoEstimado,
                        i.status.toString(), i.prioridade.toString(), i.usuario.id))
                .collect(Collectors.toList());

        return Response.ok(dtos).build();
    }

    @POST
    public Response criar(InvestimentoDTO dto) {
        Long usuarioId = userPrincipal.getUserId();
        if (usuarioId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Usuario usuario = Usuario.findById(usuarioId);
        if (usuario == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Investimento investimento = new Investimento();
        investimento.titulo = dto.titulo;
        investimento.descricao = dto.descricao;
        investimento.valorEstimado = dto.valorEstimado;
        investimento.retornoEstimado = dto.retornoEstimado;
        investimento.status = Investimento.StatusInvestimento.valueOf(dto.status != null ? dto.status : "IDEIA");
        investimento.prioridade = Investimento.Prioridade.valueOf(dto.prioridade != null ? dto.prioridade : "MEDIA");
        investimento.usuario = usuario;
        investimento.persist();

        InvestimentoDTO response = new InvestimentoDTO(investimento.id, investimento.titulo, investimento.descricao,
                investimento.valorEstimado, investimento.retornoEstimado, investimento.status.toString(),
                investimento.prioridade.toString(), investimento.usuario.id);
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    @PUT
    @Path("/{id}")
    public Response atualizar(@PathParam("id") Long id, InvestimentoDTO dto) {
        Long usuarioId = userPrincipal.getUserId();
        if (usuarioId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Investimento investimento = Investimento.findById(id);
        if (investimento == null || !investimento.usuario.id.equals(usuarioId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        investimento.titulo = dto.titulo;
        investimento.descricao = dto.descricao;
        investimento.valorEstimado = dto.valorEstimado;
        investimento.retornoEstimado = dto.retornoEstimado;
        investimento.status = Investimento.StatusInvestimento.valueOf(dto.status);
        investimento.prioridade = Investimento.Prioridade.valueOf(dto.prioridade);
        investimento.persist();

        InvestimentoDTO response = new InvestimentoDTO(investimento.id, investimento.titulo, investimento.descricao,
                investimento.valorEstimado, investimento.retornoEstimado, investimento.status.toString(),
                investimento.prioridade.toString(), investimento.usuario.id);
        return Response.ok(response).build();
    }

    @DELETE
    @Path("/{id}")
    public Response deletar(@PathParam("id") Long id) {
        Long usuarioId = userPrincipal.getUserId();
        if (usuarioId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Investimento investimento = Investimento.findById(id);
        if (investimento == null || !investimento.usuario.id.equals(usuarioId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        investimento.delete();
        return Response.noContent().build();
    }
}
