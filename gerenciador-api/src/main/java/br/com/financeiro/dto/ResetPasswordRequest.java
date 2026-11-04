package br.com.financeiro.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ResetPasswordRequest {
    @NotBlank(message = "Email é obrigatório")
    @Email(message = "Email deve ser válido")
    public String email;

    @NotBlank(message = "Código é obrigatório")
    @Pattern(regexp = "\\d{6}", message = "Código deve ter 6 dígitos")
    public String codigo;

    @NotBlank(message = "Nova senha é obrigatória")
    @Size(min = 6, max = 72, message = "Senha deve ter entre 6 e 72 caracteres")
    public String novaSenha;
}
