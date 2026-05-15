package org.example.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptionServiceTest {

    private EncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        byte[] keyBytes = new byte[32];
        SecretKey key = new SecretKeySpec(keyBytes, "AES");
        encryptionService = new EncryptionService(key);
    }

    @Test
    void shouldEncryptAndDecryptRoundtrip_whenValidPlaintext() {
        String plaintext = "access-token-abc123";

        String encrypted = encryptionService.encrypt(plaintext);
        String decrypted = encryptionService.decrypt(encrypted);

        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void shouldProduceDifferentCiphertext_whenSamePlaintextEncryptedTwice() {
        String plaintext = "same-plaintext";

        String first = encryptionService.encrypt(plaintext);
        String second = encryptionService.encrypt(plaintext);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void shouldThrowRuntimeException_whenEncryptedFormatHasNoColon() {
        assertThatThrownBy(() -> encryptionService.decrypt("nodivider"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Decryption failed");
    }

    @Test
    void shouldThrowRuntimeException_whenCiphertextIsTampered() {
        String encrypted = encryptionService.encrypt("original");
        String[] parts = encrypted.split(":", 2);
        byte[] tampered = Base64.getDecoder().decode(parts[1]);
        tampered[0] ^= 0xFF;
        String tamperedEncrypted = parts[0] + ":" + Base64.getEncoder().encodeToString(tampered);

        assertThatThrownBy(() -> encryptionService.decrypt(tamperedEncrypted))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Decryption failed");
    }
}
