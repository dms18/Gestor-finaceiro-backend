package br.com.financeiro.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class SazonalidadeDTO {
    public Long id;

    @NotBlank(message = "Nome é obrigatório")
    @Size(max = 100, message = "Nome deve ter no máximo 100 caracteres")
    public String nome;

    @NotNull(message = "Mês de início é obrigatório")
    @Min(value = 1, message = "Mês de início deve estar entre 1 e 12")
    @Max(value = 12, message = "Mês de início deve estar entre 1 e 12")
    public Integer mesInicio;

    @NotNull(message = "Mês de fim é obrigatório")
    @Min(value = 1, message = "Mês de fim deve estar entre 1 e 12")
    @Max(value = 12, message = "Mês de fim deve estar entre 1 e 12")
    public Integer mesFim;

    @Size(max = 500, message = "Descrição deve ter no máximo 500 caracteres")
    public String descricao;

    @NotBlank(message = "Nível de impacto é obrigatório")
    public String nivelImpacto;

    public Long usuarioId;

    public SazonalidadeDTO() {}

    public SazonalidadeDTO(Long id, String nome, Integer mesInicio, Integer mesFim,
                           String descricao, String nivelImpacto, Long usuarioId) {
        this.id = id;
        this.nome = nome;
        this.mesInicio = mesInicio;
        this.mesFim = mesFim;
        this.descricao = descricao;
        this.nivelImpacto = nivelImpacto;
        this.usuarioId = usuarioId;
    }
}
