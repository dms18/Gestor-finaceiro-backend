package br.com.financeiro.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class ContaDTO {
    public Long id;

    @NotBlank(message = "Nome da conta é obrigatório")
    @Size(max = 100, message = "Nome da conta deve ter no máximo 100 caracteres")
    public String nome;

    @Size(max = 100, message = "Instituição deve ter no máximo 100 caracteres")
    public String instituicao;

    @Size(max = 30, message = "Número da conta deve ter no máximo 30 caracteres")
    public String numeroConta;

    @NotBlank(message = "Tipo da conta é obrigatório")
    public String tipo;

    @NotNull(message = "Saldo é obrigatório")
    @DecimalMin(value = "0.00", message = "Saldo inicial não pode ser negativo")
    @Digits(integer = 13, fraction = 2, message = "Saldo inválido: use até 2 casas decimais e no máximo 13 dígitos inteiros")
    public BigDecimal saldo;

    @NotBlank(message = "Tipo de pessoa é obrigatório")
    public String tipoPessoa;

    public Long usuarioId;

    public ContaDTO() {}

    public ContaDTO(Long id, String nome, String tipo, BigDecimal saldo, String tipoPessoa, Long usuarioId) {
        this(id, nome, null, null, tipo, saldo, tipoPessoa, usuarioId);
    }

    public ContaDTO(Long id, String nome, String instituicao, String numeroConta, String tipo,
                    BigDecimal saldo, String tipoPessoa, Long usuarioId) {
        this.id = id;
        this.nome = nome;
        this.instituicao = instituicao;
        this.numeroConta = numeroConta;
        this.tipo = tipo;
        this.saldo = saldo;
        this.tipoPessoa = tipoPessoa;
        this.usuarioId = usuarioId;
    }
}
