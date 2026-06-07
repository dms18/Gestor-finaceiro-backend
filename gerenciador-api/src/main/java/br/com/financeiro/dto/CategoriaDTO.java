package br.com.financeiro.dto;

public class CategoriaDTO {
    public Long id;
    public String nome;
    public String tipo;
    public String cor;
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
