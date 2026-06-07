package br.com.financeiro.resource;

import br.com.financeiro.dto.SazonalidadeDTO;
import br.com.financeiro.entity.Sazonalidade;
import br.com.financeiro.entity.Usuario;
import br.com.financeiro.security.UserPrincipal;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;

@Path("/sazonalidade")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Transactional
public class SazonalidadeResource {

    @Inject
    UserPrincipal userPrincipal;

    @GET
    public Response listar() {
        Long usuarioId = userPrincipal.getUserId();
        if (usuarioId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        List<Sazonalidade> sazonalidades = Sazonalidade.list("usuario.id", usuarioId);
        List<SazonalidadeDTO> dtos = sazonalidades.stream()
                .map(s -> new SazonalidadeDTO(s.id, s.nome, s.mesInicio, s.mesFim, s.descricao, s.nivelImpacto.toString(), s.usuario.id))
                .collect(Collectors.toList());

        return Response.ok(dtos).build();
    }

    @GET
    @Path("/alertas-ativos")
    public Response alertasAtivos() {
        Long usuarioId = userPrincipal.getUserId();
        if (usuarioId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        int mesAtual = YearMonth.now().getMonthValue();

        List<Sazonalidade> sazonalidades = Sazonalidade.list("usuario.id", usuarioId);
        List<SazonalidadeDTO> alertas = sazonalidades.stream()
                .filter(s -> s.estaAtivaNoMes(mesAtual))
                .map(s -> new SazonalidadeDTO(s.id, s.nome, s.mesInicio, s.mesFim, s.descricao, s.nivelImpacto.toString(), s.usuario.id))
                .collect(Collectors.toList());

        return Response.ok(alertas).build();
    }

    @POST
    public Response criar(SazonalidadeDTO dto) {
        Long usuarioId = userPrincipal.getUserId();
        if (usuarioId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Usuario usuario = Usuario.findById(usuarioId);
        if (usuario == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Sazonalidade sazonalidade = new Sazonalidade();
        sazonalidade.nome = dto.nome;
        sazonalidade.mesInicio = dto.mesInicio;
        sazonalidade.mesFim = dto.mesFim;
        sazonalidade.descricao = dto.descricao;
        sazonalidade.nivelImpacto = Sazonalidade.NivelImpacto.valueOf(dto.nivelImpacto);
        sazonalidade.usuario = usuario;
        sazonalidade.persist();

        SazonalidadeDTO response = new SazonalidadeDTO(sazonalidade.id, sazonalidade.nome, sazonalidade.mesInicio,
                sazonalidade.mesFim, sazonalidade.descricao, sazonalidade.nivelImpacto.toString(), sazonalidade.usuario.id);
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    @PUT
    @Path("/{id}")
    public Response atualizar(@PathParam("id") Long id, SazonalidadeDTO dto) {
        Long usuarioId = userPrincipal.getUserId();
        if (usuarioId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Sazonalidade sazonalidade = Sazonalidade.findById(id);
        if (sazonalidade == null || !sazonalidade.usuario.id.equals(usuarioId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        sazonalidade.nome = dto.nome;
        sazonalidade.mesInicio = dto.mesInicio;
        sazonalidade.mesFim = dto.mesFim;
        sazonalidade.descricao = dto.descricao;
        sazonalidade.nivelImpacto = Sazonalidade.NivelImpacto.valueOf(dto.nivelImpacto);
        sazonalidade.persist();

        SazonalidadeDTO response = new SazonalidadeDTO(sazonalidade.id, sazonalidade.nome, sazonalidade.mesInicio,
                sazonalidade.mesFim, sazonalidade.descricao, sazonalidade.nivelImpacto.toString(), sazonalidade.usuario.id);
        return Response.ok(response).build();
    }

    @DELETE
    @Path("/{id}")
    public Response deletar(@PathParam("id") Long id) {
        Long usuarioId = userPrincipal.getUserId();
        if (usuarioId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Sazonalidade sazonalidade = Sazonalidade.findById(id);
        if (sazonalidade == null || !sazonalidade.usuario.id.equals(usuarioId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        sazonalidade.delete();
        return Response.noContent().build();
    }
}
