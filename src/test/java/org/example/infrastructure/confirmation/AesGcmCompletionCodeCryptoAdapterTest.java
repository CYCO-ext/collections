package org.example.infrastructure.confirmation;

import org.example.application.port.out.CompletionCodeCryptoPort.EncryptedCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AesGcmCompletionCodeCryptoAdapterTest {
    private AesGcmCompletionCodeCryptoAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new AesGcmCompletionCodeCryptoAdapter();
        adapter.encryptionKey = Base64.getEncoder().encodeToString(new byte[32]);
        byte[] hmacKey = new byte[32];
        for (int i = 0; i < hmacKey.length; i++) hmacKey[i] = (byte) (i + 1);
        adapter.hmacKey = Base64.getEncoder().encodeToString(hmacKey);
        adapter.keyId = "key-1";
        adapter.encryptionKeyRing = "";
        adapter.secureRandom = new SecureRandom() {
            @Override public int nextInt(int bound) { return 7; }
            @Override public void nextBytes(byte[] bytes) { java.util.Arrays.fill(bytes, (byte) 9); }
        };
    }

    @Test
    void generatedCodeHasSixDigitsAndPreservesLeadingZeros() {
        assertEquals("000007", adapter.generateCode());
    }

    @Test
    void encryptedCodeRoundTripsLeadingZerosAndAuthenticatesCollectionAndVersion() {
        EncryptedCode encrypted = adapter.encrypt("request-1", 3, "001234");

        assertEquals("001234", adapter.decrypt("request-1", encrypted));
        assertEquals(3, encrypted.version());
        assertEquals("key-1", encrypted.keyId());
        assertThrows(IllegalStateException.class, () -> adapter.decrypt("request-2", encrypted));
        assertThrows(IllegalStateException.class,
                () -> adapter.decrypt("request-1", new EncryptedCode(4, encrypted.ciphertext(), encrypted.nonce(), encrypted.keyId())));
    }

    @Test
    void modifiedCiphertextIsRejected() {
        EncryptedCode encrypted = adapter.encrypt("request-1", 1, "001234");
        String changed = (encrypted.ciphertext().charAt(0) == 'A' ? "B" : "A") + encrypted.ciphertext().substring(1);

        assertThrows(IllegalStateException.class,
                () -> adapter.decrypt("request-1", new EncryptedCode(1, changed, encrypted.nonce(), encrypted.keyId())));
    }

    @Test
    void verifierIsBoundToCollectionAndCode() {
        String verifier = adapter.createVerifier("request-1", 2, "001234");

        assertTrue(adapter.matchesVerifier("request-1", 2, "001234", verifier));
        assertFalse(adapter.matchesVerifier("request-2", 2, "001234", verifier));
        assertFalse(adapter.matchesVerifier("request-1", 2, "001235", verifier));
    }

    @Test
    void missingKeysFailClosed() {
        adapter.encryptionKey = "unset";
        adapter.validateStartupConfiguration();
        assertFalse(adapter.startupConfigurationAvailable);
        assertThrows(IllegalStateException.class, () -> adapter.encrypt("request-1", 1, "001234"));
        adapter.encryptionKey = Base64.getEncoder().encodeToString(new byte[32]);
        adapter.hmacKey = "unset";
        adapter.validateStartupConfiguration();
        assertFalse(adapter.startupConfigurationAvailable);
        assertThrows(IllegalStateException.class, () -> adapter.createVerifier("request-1", 1, "001234"));
    }

    @Test
    void noRetiredKeyRingSentinelAllowsStartupWithoutRetiredKeys() {
        adapter.encryptionKeyRing = "none";

        adapter.validateStartupConfiguration();

        assertTrue(adapter.startupConfigurationAvailable);
        EncryptedCode encrypted = adapter.encrypt("request-1", 1, "001234");
        assertEquals("001234", adapter.decrypt("request-1", encrypted));
    }

    @Test
    void retiredEncryptionKeyRemainsReadableByKeyIdAfterRotation() {
        String oldKey = adapter.encryptionKey;
        EncryptedCode existing = adapter.encrypt("request-1", 1, "001234");

        AesGcmCompletionCodeCryptoAdapter rotated = new AesGcmCompletionCodeCryptoAdapter();
        rotated.encryptionKey = Base64.getEncoder().encodeToString(new byte[]{1, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0});
        rotated.hmacKey = adapter.hmacKey;
        rotated.keyId = "key-2";
        rotated.encryptionKeyRing = "key-1:" + oldKey;
        rotated.secureRandom = adapter.secureRandom;

        assertEquals("001234", rotated.decrypt("request-1", existing));
        EncryptedCode issuedAfterRotation = rotated.encrypt("request-1", 2, "000007");
        assertEquals("key-2", issuedAfterRotation.keyId());
        assertEquals("000007", rotated.decrypt("request-1", issuedAfterRotation));
        assertThrows(IllegalStateException.class,
                () -> rotated.decrypt("request-1", new EncryptedCode(1, existing.ciphertext(), existing.nonce(), "unknown-key")));
    }
}
