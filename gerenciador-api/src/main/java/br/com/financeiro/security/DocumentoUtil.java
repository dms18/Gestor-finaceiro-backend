package br.com.financeiro.security;

/**
 * Validação de CPF e CNPJ (tamanho + dígitos verificadores).
 * Aceita valores com ou sem máscara; vazio/nulo é tratado como "não informado".
 */
public final class DocumentoUtil {

    private DocumentoUtil() {
    }

    public static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }

    public static boolean cpfValido(String valor) {
        String d = somenteDigitos(valor);
        if (d.length() != 11 || todosIguais(d)) {
            return false;
        }
        return digito(d, 9, 10) == d.charAt(9) - '0' && digito(d, 10, 11) == d.charAt(10) - '0';
    }

    public static boolean cnpjValido(String valor) {
        String d = somenteDigitos(valor);
        if (d.length() != 14 || todosIguais(d)) {
            return false;
        }
        int[] p1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int[] p2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        return dvCnpj(d, p1) == d.charAt(12) - '0' && dvCnpj(d, p2) == d.charAt(13) - '0';
    }

    private static int digito(String d, int tamanho, int pesoInicial) {
        int soma = 0;
        for (int i = 0; i < tamanho; i++) {
            soma += (d.charAt(i) - '0') * (pesoInicial - i);
        }
        int resto = (soma * 10) % 11;
        return resto == 10 ? 0 : resto;
    }

    private static int dvCnpj(String d, int[] pesos) {
        int soma = 0;
        for (int i = 0; i < pesos.length; i++) {
            soma += (d.charAt(i) - '0') * pesos[i];
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    private static String somenteDigitos(String valor) {
        return valor == null ? "" : valor.replaceAll("\\D", "");
    }

    private static boolean todosIguais(String d) {
        return d.chars().distinct().count() == 1;
    }
}
