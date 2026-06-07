import java.io.*;
import java.security.*;
import java.security.spec.*;
import java.util.Base64;

public class GenerateKeys {
    public static void main(String[] args) throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();

        PrivateKey privateKey = keyPair.getPrivate();
        PublicKey publicKey = keyPair.getPublic();

        // Write private key
        byte[] privateKeyBytes = privateKey.getEncoded();
        String privateKeyPEM = "-----BEGIN PRIVATE KEY-----\n" +
                Base64.getEncoder().encodeToString(privateKeyBytes) +
                "\n-----END PRIVATE KEY-----";

        try (FileWriter fw = new FileWriter("privateKey.pem")) {
            fw.write(privateKeyPEM);
        }

        // Write public key
        byte[] publicKeyBytes = publicKey.getEncoded();
        String publicKeyPEM = "-----BEGIN PUBLIC KEY-----\n" +
                Base64.getEncoder().encodeToString(publicKeyBytes) +
                "\n-----END PUBLIC KEY-----";

        try (FileWriter fw = new FileWriter("publicKey.pem")) {
            fw.write(publicKeyPEM);
        }

        System.out.println("Keys generated successfully!");
    }
}
