package br.com.financeiro.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;

@Entity
@Table(name = "sazonalidades")
public class Sazonalidade extends PanacheEntity {

    public enum NivelImpacto {
        BAIXO, MEDIO, ALTO
    }

    @NotBlank(message = "Nome do período é obrigatório")
    public String nome;

    @NotNull(message = "Mês de início é obrigatório")
    @Min(1)
    @Max(12)
    public Integer mesInicio;

    @NotNull(message = "Mês de fim é obrigatório")
    @Min(1)
    @Max(12)
    public Integer mesFim;

    public String descricao;

    @Enumerated(EnumType.STRING)
    @NotNull
    public NivelImpacto nivelImpacto = NivelImpacto.MEDIO;

    /** Data em que o caixa precisa estar disponível; a partir dela o Dashboard compara saldo x necessário. */
    @Column(name = "data_alvo")
    public java.time.LocalDate dataAlvo;

    /** Caixa necessário para atravessar o período. */
    @Column(name = "valor_necessario", precision = 15, scale = 2)
    public java.math.BigDecimal valorNecessario;

    /** Conta cujo saldo é comparado com o caixa necessário. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conta_debito_id")
    public Conta contaDebito;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    public Usuario usuario;

    @Column(name = "criada_em")
    public java.time.LocalDateTime criadaEm = java.time.LocalDateTime.now();

    public boolean temReserva() {
        return dataAlvo != null && valorNecessario != null && contaDebito != null;
    }

    /**
     * Último dia do período que contém (ou vem logo após) a data-alvo: o último dia do
     * mês de fim, no ano da data-alvo ou no seguinte se esse mês já tiver passado.
     */
    public java.time.LocalDate fimDoPeriodo() {
        java.time.LocalDate fim = java.time.YearMonth.of(dataAlvo.getYear(), mesFim).atEndOfMonth();
        if (fim.isBefore(dataAlvo)) {
            fim = java.time.YearMonth.of(dataAlvo.getYear() + 1, mesFim).atEndOfMonth();
        }
        return fim;
    }

    /** A comparação aparece no Dashboard da data-alvo até o fim do período. */
    public boolean reservaAtiva(java.time.LocalDate hoje) {
        return temReserva() && !hoje.isBefore(dataAlvo) && !hoje.isAfter(fimDoPeriodo());
    }

    public boolean estaAtivaNoMes(int mes) {
        if (mesInicio <= mesFim) {
            return mes >= mesInicio && mes <= mesFim;
        } else {
            return mes >= mesInicio || mes <= mesFim;
        }
    }
}
