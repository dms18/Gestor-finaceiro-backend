package br.com.financeiro.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

public class TransacaoDTO {
    public Long id;

    @NotBlank(message = "Descrição é obrigatória")
    public String descricao;

    @NotNull(message = "Valor é obrigatório")
    @Positive(message = "Valor deve ser maior que zero")
    public BigDecimal valor;

    @NotNull(message = "Data é obrigatória")
    public LocalDate data;

    @NotBlank(message = "Tipo é obrigatório")
    public String tipo;

    @NotBlank(message = "Tipo de pessoa é obrigatório")
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
