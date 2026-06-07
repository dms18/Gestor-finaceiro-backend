package br.com.financeiro.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "transacoes")
public class Transacao extends PanacheEntity {

    public enum TipoTransacao {
        RECEITA, DESPESA
    }

    public enum TipoPessoa {
        PF, PJ
    }

    @NotBlank(message = "Descrição é obrigatória")
    public String descricao;

    @NotNull(message = "Valor é obrigatório")
    @DecimalMin("0.01")
    @Column(precision = 15, scale = 2)
    public BigDecimal valor;

    @NotNull(message = "Data é obrigatória")
    public LocalDate data;

    @Enumerated(EnumType.STRING)
    @NotNull
    public TipoTransacao tipo;

    @Enumerated(EnumType.STRING)
    @NotNull
    public TipoPessoa tipoPessoa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    public Categoria categoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conta_id")
    public Conta conta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    public Usuario usuario;

    @Column(name = "criada_em")
    public java.time.LocalDateTime criadaEm = java.time.LocalDateTime.now();
}
