package br.com.financeiro.dto;

import java.math.BigDecimal;

public class ContaDTO {
    public Long id;
    public String nome;
    public String tipo;
    public BigDecimal saldo;
    public String tipoPessoa;
    public Long usuarioId;

    public ContaDTO() {}

    public ContaDTO(Long id, String nome, String tipo, BigDecimal saldo, String tipoPessoa, Long usuarioId) {
        this.id = id;
        this.nome = nome;
        this.tipo = tipo;
        this.saldo = saldo;
        this.tipoPessoa = tipoPessoa;
        this.usuarioId = usuarioId;
    }
}
