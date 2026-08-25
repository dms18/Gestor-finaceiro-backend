package br.com.financeiro.resource;

import br.com.financeiro.dto.LoginRequest;
import br.com.financeiro.dto.LoginResponse;
import br.com.financeiro.dto.RegisterRequest;
import br.com.financeiro.entity.Usuario;
import br.com.financeiro.security.JwtService;
import br.com.financeiro.security.PasswordService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {

    /** Versão vigente dos Termos de Uso / Política de Privacidade aceita no cadastro. */
    public static final String TERMOS_VERSAO = "1.0 (2026-08-24)";

    @Inject
    JwtService jwtService;

    @Inject
    PasswordService passwordService;

    @POST
    @Path("/login")
    @Transactional
    public Response login(@Valid LoginRequest request) {
        Usuario usuario = Usuario.findByEmail(request.email);

        if (usuario == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("{\"message\":\"Email ou senha incorretos\"}")
                    .build();
        }

        if (!passwordService.verify(request.senha, usuario.senhaHash)) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("{\"message\":\"Email ou senha incorretos\"}")
                    .build();
        }

        return Response.ok(montarResposta(usuario)).build();
    }

    private LoginResponse montarResposta(Usuario usuario) {
        String token = jwtService.generateToken(usuario.id, usuario.email);
        return new LoginResponse(
                token,
                jwtService.getExpirationTime(),
                new LoginResponse.UsuarioDTO(
                        usuario.id,
                        usuario.nome,
                        usuario.email,
                        usuario.perfil.toString(),
                        usuario.interfaceInicial != null ? usuario.interfaceInicial.toString() : null
                )
        );
    }

    @POST
    @Path("/register")
    @Transactional
    public Response register(@Valid RegisterRequest request) {
        if (Usuario.findByEmail(request.email) != null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"message\":\"Email já cadastrado\"}")
                    .build();
        }

        Usuario usuario = new Usuario();
        usuario.nome = request.nome;
        usuario.email = request.email;
        usuario.senhaHash = passwordService.hash(request.senha);
        usuario.cpf = request.cpf;
        usuario.cnpj = request.cnpj;
        usuario.perfil = Usuario.TipoPerfil.valueOf(request.perfil);
        // Interface inicial começa igual ao perfil do usuário (PF, PJ ou AMBOS).
        usuario.interfaceInicial = usuario.perfil;
        usuario.aceitouTermos = request.aceiteTermos;
        usuario.termosVersao = TERMOS_VERSAO;
        usuario.termosAceitosEm = java.time.LocalDateTime.now();
        usuario.persist();

        return Response.status(Response.Status.CREATED).entity(montarResposta(usuario)).build();
    }
}

