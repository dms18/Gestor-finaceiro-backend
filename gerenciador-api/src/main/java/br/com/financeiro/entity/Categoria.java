package br.com.financeiro.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;

@Entity
@Table(name = "categorias")
public class Categoria extends PanacheEntity {

    public enum TipoCategoria {
        RECEITA, DESPESA
    }

    @NotBlank(message = "Nome da categoria é obrigatório")
    public String nome;

    @Enumerated(EnumType.STRING)
    @NotNull
    public TipoCategoria tipo;

    public String cor = "#3880ff";

    public String icone = "folder";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    public Usuario usuario;

    @Column(name = "criada_em")
    public java.time.LocalDateTime criadaEm = java.time.LocalDateTime.now();
}
