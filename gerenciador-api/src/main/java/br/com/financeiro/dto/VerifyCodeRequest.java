package br.com.financeiro.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class VerifyCodeRequest {
    @NotBlank(message = "Email é obrigatório")
    @Email(message = "Email deve ser válido")
    public String email;

    @NotBlank(message = "Código é obrigatório")
    @Pattern(regexp = "\\d{6}", message = "Código deve ter 6 dígitos")
    public String codigo;
}
