package org.example.service;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class EncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKey secretKey;

    EncryptionService(SecretKey secretKey) {
        this.secretKey = secretKey;
    }

    public EncryptionService() {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        String keyBase64 = dotenv.get("ENCRYPTION_KEY");
        if (keyBase64 == null || keyBase64.isBlank()) {
            throw new IllegalStateException(
                "Missing required environment variable: ENCRYPTION_KEY. " +
                "Generate one with: openssl rand -base64 32"
            );
        }
        byte[] keyBytes = Base64.getDecoder().decode(keyBase64);
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                "ENCRYPTION_KEY must decode to exactly 32 bytes. " +
                "Generate one with: openssl rand -base64 32"
            );
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes());

            return Base64.getEncoder().encodeToString(iv)
                + ":"
                + Base64.getEncoder().encodeToString(ciphertext);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public String decrypt(String encrypted) {
        try {
            String[] parts = encrypted.split(":", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid encrypted value format");
            }

            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] ciphertext = Base64.getDecoder().decode(parts[1]);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            return new String(cipher.doFinal(ciphertext));
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }
}
