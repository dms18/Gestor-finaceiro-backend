package br.com.financeiro.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public class InvestimentoDTO {
    public Long id;

    @NotBlank(message = "Título é obrigatório")
    @Size(max = 150, message = "Título deve ter no máximo 150 caracteres")
    public String titulo;

    @Size(max = 1000, message = "Descrição deve ter no máximo 1000 caracteres")
    public String descricao;
    @PositiveOrZero(message = "Valor estimado não pode ser negativo")
    @Digits(integer = 13, fraction = 2, message = "Valor estimado inválido: use até 2 casas decimais")
    public BigDecimal valorEstimado;
    @DecimalMin(value = "-100.00", message = "Retorno estimado não pode ser menor que -100%")
    @Digits(integer = 3, fraction = 2, message = "Retorno estimado inválido: máximo 999,99 com até 2 casas decimais")
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
