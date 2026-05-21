package org.example.service;

import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

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

    @Test
    void shouldInitialiseFromEnvironmentKey_whenEncryptionKeyIsSet() {
        EncryptionService service = new EncryptionService();
        assertThat(service.decrypt(service.encrypt("hello"))).isEqualTo("hello");
    }

    @Test
    void shouldThrowIllegalState_whenEncryptionKeyIsBlank() {
        Dotenv mockDotenv = mock(Dotenv.class);
        when(mockDotenv.get("ENCRYPTION_KEY")).thenReturn("   ");
        DotenvBuilder mockBuilder = mock(DotenvBuilder.class);
        when(mockBuilder.ignoreIfMissing()).thenReturn(mockBuilder);
        when(mockBuilder.load()).thenReturn(mockDotenv);

        try (MockedStatic<Dotenv> dotenvMock = mockStatic(Dotenv.class)) {
            dotenvMock.when(Dotenv::configure).thenReturn(mockBuilder);

            assertThatThrownBy(EncryptionService::new)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Missing required environment variable");
        }
    }

    @Test
    void shouldThrowIllegalState_whenEncryptionKeyIsNull() {
        Dotenv mockDotenv = mock(Dotenv.class);
        when(mockDotenv.get("ENCRYPTION_KEY")).thenReturn(null);
        DotenvBuilder mockBuilder = mock(DotenvBuilder.class);
        when(mockBuilder.ignoreIfMissing()).thenReturn(mockBuilder);
        when(mockBuilder.load()).thenReturn(mockDotenv);

        try (MockedStatic<Dotenv> dotenvMock = mockStatic(Dotenv.class)) {
            dotenvMock.when(Dotenv::configure).thenReturn(mockBuilder);

            assertThatThrownBy(EncryptionService::new)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Missing required environment variable");
        }
    }

    @Test
    void shouldThrowIllegalState_whenEncryptionKeyIsNotThirtyTwoBytes() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        Dotenv mockDotenv = mock(Dotenv.class);
        when(mockDotenv.get("ENCRYPTION_KEY")).thenReturn(shortKey);
        DotenvBuilder mockBuilder = mock(DotenvBuilder.class);
        when(mockBuilder.ignoreIfMissing()).thenReturn(mockBuilder);
        when(mockBuilder.load()).thenReturn(mockDotenv);

        try (MockedStatic<Dotenv> dotenvMock = mockStatic(Dotenv.class)) {
            dotenvMock.when(Dotenv::configure).thenReturn(mockBuilder);

            assertThatThrownBy(EncryptionService::new)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ENCRYPTION_KEY must decode to exactly 32 bytes");
        }
    }
}
