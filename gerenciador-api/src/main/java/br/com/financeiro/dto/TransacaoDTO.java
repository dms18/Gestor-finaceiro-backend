package br.com.financeiro.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class TransacaoDTO {
    public Long id;
    public String descricao;
    public BigDecimal valor;
    public LocalDate data;
    public String tipo;
    public String tipoPessoa;
    public Long categoriaId;
    public Long contaId;
    public Long usuarioId;

    public TransacaoDTO() {}

    public TransacaoDTO(Long id, String descricao, BigDecimal valor, LocalDate data,
                        String tipo, String tipoPessoa, Long categoriaId, Long contaId, Long usuarioId) {
        this.id = id;
        this.descricao = descricao;
        this.valor = valor;
        this.data = data;
        this.tipo = tipo;
        this.tipoPessoa = tipoPessoa;
        this.categoriaId = categoriaId;
        this.contaId = contaId;
        this.usuarioId = usuarioId;
    }
}
