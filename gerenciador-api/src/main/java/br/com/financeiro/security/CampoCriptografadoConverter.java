package br.com.financeiro.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.eclipse.microprofile.config.ConfigProvider;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Converter JPA que cifra dados sensíveis (CPF/CNPJ) em repouso usando
 * AES-256-GCM. A cifragem é transparente: o restante da aplicação continua
 * lendo/gravando o valor em texto puro; apenas a coluna no banco fica cifrada.
 *
 * <p>Objetivo de segurança: em caso de vazamento do banco (dump, backup, acesso
 * indevido, SQL injection), CPF/CNPJ ficam inúteis para o atacante. Não substitui
 * TLS na comunicação — é defesa em profundidade contra comprometimento em repouso.
 *
 * <p>A chave é derivada (SHA-256) de {@code app.crypto.secret}, que DEVE ser
 * definido por variável de ambiente forte em produção.
 *
 * <p>Retrocompatibilidade: valores legados gravados em texto puro (sem o prefixo
 * {@link #PREFIXO}) são retornados como estão, permitindo migração incremental —
 * eles passam a ser cifrados no próximo update do registro.
 */
@Converter
public class CampoCriptografadoConverter implements AttributeConverter<String, String> {

    private static final String PREFIXO = "enc:v1:";
    private static final String TRANSFORMACAO = "AES/GCM/NoPadding";
    private static final int TAM_IV = 12;    // 96 bits — recomendado para GCM
    private static final int TAM_TAG = 128;  // bits
    private static final SecureRandom RANDOM = new SecureRandom();

    private static volatile SecretKeySpec chave;

    private static SecretKeySpec chave() {
        if (chave == null) {
            synchronized (CampoCriptografadoConverter.class) {
                if (chave == null) {
                    String secret = ConfigProvider.getConfig().getValue("app.crypto.secret", String.class);
                    try {
                        byte[] hash = MessageDigest.getInstance("SHA-256")
                                .digest(secret.getBytes(StandardCharsets.UTF_8));
                        chave = new SecretKeySpec(hash, "AES");
                    } catch (Exception e) {
                        throw new IllegalStateException("Falha ao derivar chave de criptografia", e);
                    }
                }
            }
        }
        return chave;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return attribute;
        }
        try {
            byte[] iv = new byte[TAM_IV];
            RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMACAO);
            cipher.init(Cipher.ENCRYPT_MODE, chave(), new GCMParameterSpec(TAM_TAG, iv));
            byte[] cifrado = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));

            byte[] combinado = new byte[iv.length + cifrado.length];
            System.arraycopy(iv, 0, combinado, 0, iv.length);
            System.arraycopy(cifrado, 0, combinado, iv.length, cifrado.length);

            return PREFIXO + Base64.getEncoder().encodeToString(combinado);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao criptografar dado sensível", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return dbData;
        }
        // Valor legado em texto puro (sem prefixo) — retorna como está.
        if (!dbData.startsWith(PREFIXO)) {
            return dbData;
        }
        try {
            byte[] combinado = Base64.getDecoder().decode(dbData.substring(PREFIXO.length()));

            byte[] iv = new byte[TAM_IV];
            System.arraycopy(combinado, 0, iv, 0, TAM_IV);
            byte[] cifrado = new byte[combinado.length - TAM_IV];
            System.arraycopy(combinado, TAM_IV, cifrado, 0, cifrado.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMACAO);
            cipher.init(Cipher.DECRYPT_MODE, chave(), new GCMParameterSpec(TAM_TAG, iv));
            return new String(cipher.doFinal(cifrado), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao descriptografar dado sensível", e);
        }
    }
}
