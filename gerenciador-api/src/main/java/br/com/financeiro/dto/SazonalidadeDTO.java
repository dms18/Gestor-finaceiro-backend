package br.com.financeiro.dto;

public class SazonalidadeDTO {
    public Long id;
    public String nome;
    public Integer mesInicio;
    public Integer mesFim;
    public String descricao;
    public String nivelImpacto;
    public Long usuarioId;

    public SazonalidadeDTO() {}

    public SazonalidadeDTO(Long id, String nome, Integer mesInicio, Integer mesFim,
                           String descricao, String nivelImpacto, Long usuarioId) {
        this.id = id;
        this.nome = nome;
        this.mesInicio = mesInicio;
        this.mesFim = mesFim;
        this.descricao = descricao;
        this.nivelImpacto = nivelImpacto;
        this.usuarioId = usuarioId;
    }
}
