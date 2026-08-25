package br.com.financeiro.dto;

/**
 * Requisição para atualizar a foto de perfil. O campo {@code foto} é um data URL
 * base64 (ex.: "data:image/jpeg;base64,...") ou {@code null} para remover a foto.
 */
public class FotoPerfilRequest {
    public String foto;

    public FotoPerfilRequest() {}
}
