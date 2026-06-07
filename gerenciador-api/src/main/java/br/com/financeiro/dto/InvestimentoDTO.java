package br.com.financeiro.dto;

import java.math.BigDecimal;

public class InvestimentoDTO {
    public Long id;
    public String titulo;
    public String descricao;
    public BigDecimal valorEstimado;
    public BigDecimal retornoEstimado;
    public String status;
    public String prioridade;
    public Long usuarioId;

    public InvestimentoDTO() {}

    public InvestimentoDTO(Long id, String titulo, String descricao, BigDecimal valorEstimado,
                           BigDecimal retornoEstimado, String status, String prioridade, Long usuarioId) {
        this.id = id;
        this.titulo = titulo;
        this.descricao = descricao;
        this.valorEstimado = valorEstimado;
        this.retornoEstimado = retornoEstimado;
        this.status = status;
        this.prioridade = prioridade;
        this.usuarioId = usuarioId;
    }
}
