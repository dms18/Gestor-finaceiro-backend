package br.com.financeiro.resource;

import br.com.financeiro.security.DocumentoUtil;
import br.com.financeiro.security.LoginAttemptService;
import br.com.financeiro.dto.ForgotPasswordRequest;
import br.com.financeiro.dto.ResetPasswordRequest;
import br.com.financeiro.entity.PasswordResetToken;
import br.com.financeiro.security.ResetMailService;
import br.com.financeiro.security.ResetRateLimiter;
import java.security.SecureRandom;
import java.time.LocalDateTime;
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

    @Inject
    LoginAttemptService loginAttempts;

    @Inject
    ResetRateLimiter resetLimiter;

    @Inject
    ResetMailService resetMail;

    private static final SecureRandom RANDOM = new SecureRandom();

    @POST
    @Path("/login")
    @Transactional
    public Response login(@Valid LoginRequest request) {
        long bloqueio = loginAttempts.segundosBloqueado(request.email);
        if (bloqueio > 0) {
            return Response.status(429)
                    .header("Retry-After", String.valueOf(bloqueio))
                    .entity("{\"message\":\"Muitas tentativas de login. Tente novamente em alguns minutos.\"}")
                    .build();
        }

        Usuario usuario = Usuario.findByEmail(request.email);

        if (usuario == null) {
            loginAttempts.registrarFalha(request.email);
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("{\"message\":\"Email ou senha incorretos\"}")
                    .build();
        }

        if (!passwordService.verify(request.senha, usuario.senhaHash)) {
            loginAttempts.registrarFalha(request.email);
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("{\"message\":\"Email ou senha incorretos\"}")
                    .build();
        }

        loginAttempts.registrarSucesso(request.email);
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
        if (!DocumentoUtil.vazio(request.cpf) && !DocumentoUtil.cpfValido(request.cpf)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"message\":\"CPF inválido\"}")
                    .build();
        }
        if (!DocumentoUtil.vazio(request.cnpj) && !DocumentoUtil.cnpjValido(request.cnpj)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"message\":\"CNPJ inválido\"}")
                    .build();
        }

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

    @POST
    @Path("/esqueci-senha")
    @Transactional
    public Response esqueciSenha(@Valid ForgotPasswordRequest request) {
        long espera = resetLimiter.registrar(request.email);
        if (espera > 0) {
            return Response.status(429)
                    .header("Retry-After", String.valueOf(espera))
                    .entity("{\"message\":\"Muitos pedidos de código. Tente novamente em alguns minutos.\"}")
                    .build();
        }

        Usuario usuario = Usuario.findByEmail(request.email);
        if (usuario != null && usuario.ativo) {
            PasswordResetToken.invalidarTodos(usuario.email);

            String codigo = String.format("%06d", RANDOM.nextInt(1_000_000));
            PasswordResetToken token = new PasswordResetToken();
            token.email = usuario.email;
            token.codigoHash = passwordService.hash(codigo);
            token.expiraEm = LocalDateTime.now().plusMinutes(PasswordResetToken.VALIDADE_MINUTOS);
            token.persist();

            resetMail.enviarCodigo(usuario.email, usuario.nome, codigo);
        }

        // Resposta idêntica exista o e-mail ou não, para não revelar quais contas existem.
        return Response.ok("{\"message\":\"Se o e-mail estiver cadastrado, enviamos um código de verificação.\"}").build();
    }

    @POST
    @Path("/redefinir-senha")
    @Transactional
    public Response redefinirSenha(@Valid ResetPasswordRequest request) {
        Response invalido = Response.status(Response.Status.BAD_REQUEST)
                .entity("{\"message\":\"Código inválido ou expirado\"}")
                .build();

        Usuario usuario = Usuario.findByEmail(request.email);
        if (usuario == null || !usuario.ativo) {
            return invalido;
        }

        PasswordResetToken token = PasswordResetToken.findAtivo(usuario.email);
        if (token == null || token.expirado() || token.tentativas >= PasswordResetToken.MAX_TENTATIVAS) {
            return invalido;
        }

        if (!passwordService.verify(request.codigo, token.codigoHash)) {
            token.tentativas++;
            return invalido;
        }

        usuario.senhaHash = passwordService.hash(request.novaSenha);
        token.usado = true;
        PasswordResetToken.invalidarTodos(usuario.email);
        loginAttempts.registrarSucesso(usuario.email);

        return Response.ok("{\"message\":\"Senha redefinida com sucesso\"}").build();
    }
}

