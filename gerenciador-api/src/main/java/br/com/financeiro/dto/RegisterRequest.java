package br.com.financeiro.dto;

import jakarta.validation.constraints.AssertTrue;
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

    /**
     * Consentimento LGPD: o usuário precisa aceitar os Termos de Uso e a
     * Política de Privacidade para criar a conta. Sem o aceite (true), o
     * cadastro é rejeitado com 400.
     */
    @AssertTrue(message = "É necessário aceitar os Termos de Uso e a Política de Privacidade")
    public boolean aceiteTermos;
}
