package br.com.financeiro.resource;

import br.com.financeiro.security.DocumentoUtil;
import br.com.financeiro.dto.FotoPerfilRequest;
import br.com.financeiro.dto.InterfaceInicialRequest;
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
public class UsuarioResource {

    /** Tamanho máximo do data URL da foto (~3 MB de base64 ≈ 2,2 MB de imagem). */
    private static final int MAX_FOTO_LENGTH = 3_000_000;

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

        return Response.ok(toDTO(usuario)).build();
    }

    @PUT
    @Transactional
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

        if (dto == null || dto.nome == null || dto.nome.isBlank() || dto.nome.length() > 120) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"message\":\"Nome é obrigatório (até 120 caracteres)\"}")
                    .build();
        }
        if (dto.email != null && !dto.email.isBlank() && !dto.email.equalsIgnoreCase(usuario.email)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"message\":\"Não é possível alterar o e-mail da conta\"}")
                    .build();
        }
        if (!DocumentoUtil.vazio(dto.cpf) && !DocumentoUtil.cpfValido(dto.cpf)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"message\":\"CPF inválido\"}")
                    .build();
        }
        if (!DocumentoUtil.vazio(dto.cnpj) && !DocumentoUtil.cnpjValido(dto.cnpj)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"message\":\"CNPJ inválido\"}")
                    .build();
        }

        usuario.nome = dto.nome;
        usuario.cpf = DocumentoUtil.vazio(dto.cpf) ? null : dto.cpf;
        usuario.cnpj = DocumentoUtil.vazio(dto.cnpj) ? null : dto.cnpj;
        usuario.persist();

        return Response.ok(toDTO(usuario)).build();
    }

    @PUT
    @Transactional
    @Path("/perfil/foto")
    public Response updateFoto(FotoPerfilRequest request) {
        Long usuarioId = userPrincipal.getUserId();
        if (usuarioId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Usuario usuario = Usuario.findById(usuarioId);
        if (usuario == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        String foto = request != null ? request.foto : null;

        // null/vazio remove a foto
        if (foto != null && !foto.isBlank()) {
            if (!foto.startsWith("data:image/")) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("{\"message\":\"Formato de imagem inválido\"}")
                        .build();
            }
            if (foto.length() > MAX_FOTO_LENGTH) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("{\"message\":\"Imagem muito grande. Escolha uma foto menor.\"}")
                        .build();
            }
            usuario.fotoPerfil = foto;
        } else {
            usuario.fotoPerfil = null;
        }

        usuario.persist();
        return Response.ok(toDTO(usuario)).build();
    }

    @PUT
    @Transactional
    @Path("/perfil/interface")
    public Response updateInterface(InterfaceInicialRequest request) {
        Long usuarioId = userPrincipal.getUserId();
        if (usuarioId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Usuario usuario = Usuario.findById(usuarioId);
        if (usuario == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        String valor = request != null ? request.interfaceInicial : null;
        if (valor == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"message\":\"Interface inicial é obrigatória\"}")
                    .build();
        }
        try {
            usuario.interfaceInicial = Usuario.TipoPerfil.valueOf(valor);
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"message\":\"Interface inicial inválida\"}")
                    .build();
        }

        usuario.persist();
        return Response.ok(toDTO(usuario)).build();
    }

    private UsuarioDTO toDTO(Usuario usuario) {
        return new UsuarioDTO(
                usuario.id, usuario.nome, usuario.email,
                usuario.cpf, usuario.cnpj, usuario.perfil.toString(), usuario.ativo,
                usuario.fotoPerfil,
                usuario.interfaceInicial != null ? usuario.interfaceInicial.toString() : null
        );
    }
}
