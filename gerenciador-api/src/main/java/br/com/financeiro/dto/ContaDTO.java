package br.com.financeiro.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class ContaDTO {
    public Long id;

    @NotBlank(message = "Nome da conta é obrigatório")
    public String nome;

    public String instituicao;

    public String numeroConta;

    @NotBlank(message = "Tipo da conta é obrigatório")
    public String tipo;

    @NotNull(message = "Saldo é obrigatório")
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
