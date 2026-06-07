package br.com.financeiro.dto;

public class LoginResponse {
    public String token;
    public long expiresIn;
    public UsuarioDTO usuario;

    public LoginResponse(String token, long expiresIn, UsuarioDTO usuario) {
        this.token = token;
        this.expiresIn = expiresIn;
        this.usuario = usuario;
    }

    public static class UsuarioDTO {
        public Long id;
        public String nome;
        public String email;
        public String perfil;

        public UsuarioDTO(Long id, String nome, String email, String perfil) {
            this.id = id;
            this.nome = nome;
            this.email = email;
            this.perfil = perfil;
        }
    }
}
