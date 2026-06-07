package br.com.financeiro.dto;

public class UsuarioDTO {
    public Long id;
    public String nome;
    public String email;
    public String cpf;
    public String cnpj;
    public String perfil;
    public boolean ativo;

    public UsuarioDTO() {}

    public UsuarioDTO(Long id, String nome, String email, String cpf, String cnpj, String perfil, boolean ativo) {
        this.id = id;
        this.nome = nome;
        this.email = email;
        this.cpf = cpf;
        this.cnpj = cnpj;
        this.perfil = perfil;
        this.ativo = ativo;
    }
}
