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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    public Usuario usuario;

    @Column(name = "criada_em")
    public java.time.LocalDateTime criadaEm = java.time.LocalDateTime.now();

    public boolean estaAtivaNoMes(int mes) {
        if (mesInicio <= mesFim) {
            return mes >= mesInicio && mes <= mesFim;
        } else {
            return mes >= mesInicio || mes <= mesFim;
        }
    }
}
