package br.com.financeiro.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;

@Entity
@Table(name = "usuarios")
public class Usuario extends PanacheEntity {

    public enum TipoPerfil {
        PF, PJ, AMBOS
    }

    @NotBlank(message = "Nome é obrigatório")
    public String nome;

    @NotBlank(message = "Email é obrigatório")
    @Email(message = "Email deve ser válido")
    @Column(unique = true)
    public String email;

    @NotBlank(message = "Senha é obrigatória")
    public String senhaHash;

    public String cpf;

    public String cnpj;

    @Enumerated(EnumType.STRING)
    @NotNull
    public TipoPerfil perfil;

    public boolean ativo = true;

    @Column(name = "criado_em")
    public java.time.LocalDateTime criadoEm = java.time.LocalDateTime.now();

    public static Usuario findByEmail(String email) {
        return find("email", email).firstResult();
    }
}
