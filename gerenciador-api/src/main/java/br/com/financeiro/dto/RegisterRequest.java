package br.com.financeiro.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class RegisterRequest {
    @NotBlank(message = "Nome é obrigatório")
    @Size(max = 120, message = "Nome deve ter no máximo 120 caracteres")
    public String nome;

    @NotBlank(message = "Email é obrigatório")
    @Email(message = "Email deve ser válido")
    @Size(max = 160, message = "Email deve ter no máximo 160 caracteres")
    public String email;

    @NotBlank(message = "Senha é obrigatória")
    @Size(min = 6, max = 72, message = "Senha deve ter entre 6 e 72 caracteres")
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
