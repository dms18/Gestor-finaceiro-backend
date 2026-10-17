package br.com.financeiro.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;

public class CategoriaDTO {
    public Long id;

    @NotBlank(message = "Nome da categoria é obrigatório")
    @Size(max = 60, message = "Nome da categoria deve ter no máximo 60 caracteres")
    public String nome;

    @NotBlank(message = "Tipo da categoria é obrigatório")
    public String tipo;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Cor inválida: use o formato #RRGGBB")
    public String cor;
    @Pattern(regexp = "^[a-z0-9-]{1,40}$", message = "Ícone inválido")
    public String icone;
    public Long usuarioId;

    public CategoriaDTO() {}

    public CategoriaDTO(Long id, String nome, String tipo, String cor, String icone, Long usuarioId) {
        this.id = id;
        this.nome = nome;
        this.tipo = tipo;
        this.cor = cor;
        this.icone = icone;
        this.usuarioId = usuarioId;
    }
}
