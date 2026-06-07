package br.com.financeiro.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

@Entity
@Table(name = "contas")
public class Conta extends PanacheEntity {

    public enum TipoConta {
        CORRENTE, POUPANCA, CAIXA
    }

    public enum TipoPessoa {
        PF, PJ
    }

    @NotBlank(message = "Nome da conta é obrigatório")
    public String nome;

    @Enumerated(EnumType.STRING)
    @NotNull
    public TipoConta tipo;

    @NotNull(message = "Saldo é obrigatório")
    @DecimalMin("0.00")
    @Column(precision = 15, scale = 2)
    public BigDecimal saldo = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @NotNull
    public TipoPessoa tipoPessoa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    public Usuario usuario;

    @Column(name = "criada_em")
    public java.time.LocalDateTime criadaEm = java.time.LocalDateTime.now();
}
