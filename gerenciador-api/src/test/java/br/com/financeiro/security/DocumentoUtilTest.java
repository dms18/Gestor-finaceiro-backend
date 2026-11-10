package br.com.financeiro.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DocumentoUtilTest {

    @Test
    void cpfValidoComESemMascara() {
        assertTrue(DocumentoUtil.cpfValido("529.982.247-25"));
        assertTrue(DocumentoUtil.cpfValido("52998224725"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "529.982.247-24",   // dígito verificador errado
            "111.111.111-11",   // todos iguais
            "000.000.000-00",
            "123",              // curto demais
            "529.982.247-255",  // longo demais
            "abcdefghijk"
    })
    void cpfInvalido(String cpf) {
        assertFalse(DocumentoUtil.cpfValido(cpf));
    }

    @Test
    void cpfNuloOuVazioNaoEValido() {
        assertFalse(DocumentoUtil.cpfValido(null));
        assertFalse(DocumentoUtil.cpfValido(""));
    }

    @Test
    void cnpjValidoComESemMascara() {
        assertTrue(DocumentoUtil.cnpjValido("11.222.333/0001-81"));
        assertTrue(DocumentoUtil.cnpjValido("11222333000181"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "11.222.333/0001-82",  // dígito verificador errado
            "11.111.111/1111-11",  // todos iguais
            "123",
            "11222333000181999"
    })
    void cnpjInvalido(String cnpj) {
        assertFalse(DocumentoUtil.cnpjValido(cnpj));
    }

    @Test
    void vazioReconheceNuloEEspacos() {
        assertTrue(DocumentoUtil.vazio(null));
        assertTrue(DocumentoUtil.vazio(""));
        assertTrue(DocumentoUtil.vazio("   "));
        assertFalse(DocumentoUtil.vazio("529.982.247-25"));
    }
}
