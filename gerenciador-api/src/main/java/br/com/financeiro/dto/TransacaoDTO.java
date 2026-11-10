package br.com.financeiro.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

public class TransacaoDTO {
    public Long id;

    @NotBlank(message = "Descrição é obrigatória")
    @Size(max = 200, message = "Descrição deve ter no máximo 200 caracteres")
    public String descricao;

    @NotNull(message = "Valor é obrigatório")
    @Positive(message = "Valor deve ser maior que zero")
    @Digits(integer = 13, fraction = 2, message = "Valor inválido: use até 2 casas decimais")
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

    /** Entrada: se true, "valor" é o montante total e será dividido em "numeroParcelas" parcelas mensais. */
    public Boolean parcelado;

    @Min(value = 2, message = "O parcelamento exige no mínimo 2 parcelas")
    @Max(value = 60, message = "O parcelamento permite no máximo 60 parcelas")
    public Integer numeroParcelas;

    /** Saída: dados da parcela (null em transações avulsas). */
    public String grupoParcelas;
    public Integer parcelaNumero;
    public Integer totalParcelas;

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
