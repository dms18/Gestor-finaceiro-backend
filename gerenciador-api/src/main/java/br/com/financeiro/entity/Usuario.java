package br.com.financeiro.entity;

import br.com.financeiro.security.CampoCriptografadoConverter;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
    @JsonIgnore
    public String senhaHash;

    @Convert(converter = CampoCriptografadoConverter.class)
    public String cpf;

    @Convert(converter = CampoCriptografadoConverter.class)
    public String cnpj;

    @Enumerated(EnumType.STRING)
    @NotNull
    public TipoPerfil perfil;

    /**
     * Interface inicial preferida do usuário, carregada no login e usada como
     * filtro padrão do dashboard (PF, PJ ou AMBOS = visão geral).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "interface_inicial")
    public TipoPerfil interfaceInicial;

    public boolean ativo = true;

    /** Foto de perfil armazenada como data URL base64 (ex.: "data:image/jpeg;base64,..."). */
    @Lob
    @Column(name = "foto_perfil", columnDefinition = "LONGTEXT")
    public String fotoPerfil;

    // Consentimento LGPD — comprovação do aceite dos Termos de Uso / Política de Privacidade.
    // Tipo Boolean (wrapper) para tolerar linhas antigas com valor NULL no banco
    // (criadas antes desta coluna existir), evitando erro ao carregar a entidade.
    @Column(name = "aceitou_termos")
    public Boolean aceitouTermos = false;

    @Column(name = "termos_versao")
    public String termosVersao;

    @Column(name = "termos_aceitos_em")
    public java.time.LocalDateTime termosAceitosEm;

    @Column(name = "criado_em")
    public java.time.LocalDateTime criadoEm = java.time.LocalDateTime.now();

    public static Usuario findByEmail(String email) {
        return find("email", email).firstResult();
    }
}
