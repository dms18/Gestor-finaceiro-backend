package br.com.financeiro.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class RegisterRequest {
    @NotBlank(message = "Nome é obrigatório")
    public String nome;

    @NotBlank(message = "Email é obrigatório")
    @Email(message = "Email deve ser válido")
    public String email;

    @NotBlank(message = "Senha é obrigatória")
    public String senha;

    public String cpf;

    public String cnpj;

    @NotBlank(message = "Perfil é obrigatório")
    public String perfil;
}
