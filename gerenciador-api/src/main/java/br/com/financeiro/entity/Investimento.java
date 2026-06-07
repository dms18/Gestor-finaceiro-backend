package br.com.financeiro.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

@Entity
@Table(name = "investimentos")
public class Investimento extends PanacheEntity {

    public enum StatusInvestimento {
        IDEIA, EM_ANALISE, APROVADO, DESCARTADO
    }

    public enum Prioridade {
        BAIXA, MEDIA, ALTA
    }

    @NotBlank(message = "Título é obrigatório")
    public String titulo;

    public String descricao;

    @Column(precision = 15, scale = 2)
    public BigDecimal valorEstimado;

    @Column(precision = 5, scale = 2)
    public BigDecimal retornoEstimado;

    @Enumerated(EnumType.STRING)
    @NotNull
    public StatusInvestimento status = StatusInvestimento.IDEIA;

    @Enumerated(EnumType.STRING)
    @NotNull
    public Prioridade prioridade = Prioridade.MEDIA;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    public Usuario usuario;

    @Column(name = "criado_em")
    public java.time.LocalDateTime criadoEm = java.time.LocalDateTime.now();
}
