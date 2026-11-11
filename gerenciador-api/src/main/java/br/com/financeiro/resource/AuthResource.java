package br.com.financeiro.resource;

import br.com.financeiro.security.DocumentoUtil;
import br.com.financeiro.security.LoginAttemptService;
import br.com.financeiro.dto.ForgotPasswordRequest;
import br.com.financeiro.dto.ResetPasswordRequest;
import br.com.financeiro.dto.VerifyCodeRequest;
import br.com.financeiro.entity.PasswordResetToken;
import br.com.financeiro.security.ResetAttemptService;
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
import io.vertx.core.http.HttpServerRequest;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {

    /** Versão vigente dos Termos de Uso / Política de Privacidade aceita no cadastro. */
    public static final String TERMOS_VERSAO = "1.1 (2026-11-10)";

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

    @Inject
    ResetAttemptService resetAttempts;

    @Context
    HttpServerRequest httpRequest;

    /**
     * IP do cliente. Em produção a API só é alcançável pelo nginx e o Quarkus aplica o X-Forwarded-For
     * dele (proxy-address-forwarding), então este é o IP real de quem chamou.
     */
    private String ipDoCliente() {
        var endereco = httpRequest == null ? null : httpRequest.remoteAddress();
        return endereco == null ? "" : endereco.host();
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    @POST
    @Path("/login")
    @Transactional
    public Response login(@Valid LoginRequest request) {
        String ip = ipDoCliente();
        long bloqueio = loginAttempts.segundosBloqueado(request.email, ip);
        if (bloqueio > 0) {
            return Response.status(429)
                    .header("Retry-After", String.valueOf(bloqueio))
                    .entity("{\"message\":\"Muitas tentativas de login. Tente novamente em alguns minutos.\"}")
                    .build();
        }

        Usuario usuario = Usuario.findByEmail(request.email);

        if (usuario == null) {
            // gasta o mesmo tempo de uma verificação real: o tempo não pode revelar que a conta não existe
            passwordService.gastarTempoComoSeVerificasse(request.senha);
            loginAttempts.registrarFalha(request.email, ip);
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("{\"message\":\"Email ou senha incorretos\"}")
                    .build();
        }

        if (!passwordService.verify(request.senha, usuario.senhaHash)) {
            loginAttempts.registrarFalha(request.email, ip);
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("{\"message\":\"Email ou senha incorretos\"}")
                    .build();
        }

        loginAttempts.registrarSucesso(request.email, ip);
        return Response.ok(montarResposta(usuario)).build();
    }

    private LoginResponse montarResposta(Usuario usuario) {
        String token = jwtService.generateToken(usuario.id, usuario.email,
                usuario.tokenVersao == null ? 0 : usuario.tokenVersao);
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
        } else {
            // conta inexistente ou inativa: faz o mesmo trabalho caro, para o tempo não revelar a diferença
            passwordService.gastarTempoComoSeVerificasse(request.email);
        }

        // Resposta idêntica exista o e-mail ou não, para não revelar quais contas existem.
        return Response.ok("{\"message\":\"Se o e-mail estiver cadastrado, enviamos um código de verificação.\"}").build();
    }

    /** Confere o código sem trocar a senha (o app só pede a nova senha depois de um código válido). */
    @POST
    @Path("/verificar-codigo")
    @Transactional
    public Response verificarCodigo(@Valid VerifyCodeRequest request) {
        if (!codigoValido(request.email, request.codigo)) {
            return codigoInvalido();
        }
        return Response.ok("{\"message\":\"Código válido\"}").build();
    }

    private Response codigoInvalido() {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity("{\"message\":\"Código inválido ou expirado\"}")
                .build();
    }

    /** Valida e-mail + código, consumindo uma tentativa de forma atômica. */
    private boolean codigoValido(String email, String codigo) {
        // Reserva ATÔMICA de uma tentativa em transação própria (o banco incrementa): requisições simultâneas
        // não ganham palpites extras. Precisa vir ANTES de qualquer leitura nesta transação (ver
        // ResetAttemptService). Só quem conseguiu a reserva chega a verificar o código.
        String hash = resetAttempts.reservarTentativa(email);
        return hash != null && passwordService.verify(codigo, hash);
    }

    @POST
    @Path("/redefinir-senha")
    @Transactional
    public Response redefinirSenha(@Valid ResetPasswordRequest request) {
        if (!codigoValido(request.email, request.codigo)) {
            return codigoInvalido();
        }
        Usuario usuario = Usuario.findByEmail(request.email);

        usuario.senhaHash = passwordService.hash(request.novaSenha);
        // derruba todas as sessões já emitidas: um token roubado não sobrevive à troca de senha
        usuario.tokenVersao = (usuario.tokenVersao == null ? 0 : usuario.tokenVersao) + 1;
        // Esta transação só lê o usuário/token DEPOIS da reserva da tentativa (outra transação), então o
        // UPDATE em massa abaixo não conflita com ela. Ele
        // marca este token (e os demais pendentes) como usado.
        PasswordResetToken.invalidarTodos(usuario.email);
        loginAttempts.liberarEmail(usuario.email);

        return Response.ok("{\"message\":\"Senha redefinida com sucesso\"}").build();
    }
}

