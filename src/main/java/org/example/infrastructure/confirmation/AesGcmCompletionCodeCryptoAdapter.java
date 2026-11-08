package org.example.infrastructure.confirmation;

import jakarta.inject.Singleton;
import jakarta.annotation.PostConstruct;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.example.application.port.out.CompletionCodeCryptoPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Singleton
public class AesGcmCompletionCodeCryptoAdapter implements CompletionCodeCryptoPort {
    private static final Logger LOG = LoggerFactory.getLogger(AesGcmCompletionCodeCryptoAdapter.class);
    private static final int NONCE_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final String MISSING_KEY = "unset";
    private static final String NO_RETIRED_KEYS = "none";

    @ConfigProperty(name = "completion-code.encryption-key", defaultValue = MISSING_KEY)
    String encryptionKey;

    @ConfigProperty(name = "completion-code.hmac-key", defaultValue = MISSING_KEY)
    String hmacKey;

    @ConfigProperty(name = "completion-code.key-id", defaultValue = MISSING_KEY)
    String keyId;

    @ConfigProperty(name = "completion-code.encryption-key-ring", defaultValue = NO_RETIRED_KEYS)
    String encryptionKeyRing;

    boolean startupConfigurationAvailable;

    SecureRandom secureRandom = new SecureRandom();

    @PostConstruct
    void validateStartupConfiguration() {
        try {
            requireKeyId();
            decodeKey(encryptionKey, 32, "encryption");
            decodeKey(hmacKey, 32, "HMAC");
            parseEncryptionKeyRing();
            startupConfigurationAvailable = true;
        } catch (IllegalStateException ex) {
            startupConfigurationAvailable = false;
            LOG.warn("Completion code crypto unavailable at startup: {}", ex.getMessage());
        }
    }

    @Override
    public String generateCode() {
        return "%06d".formatted(secureRandom.nextInt(1_000_000));
    }

    @Override
    public EncryptedCode encrypt(String requestId, int version, String code) {
        byte[] nonce = new byte[NONCE_LENGTH_BYTES];
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(activeEncryptionKey(), "AES"),
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
            cipher.updateAAD(aad(requestId, version));
            byte[] ciphertext = cipher.doFinal(code.getBytes(StandardCharsets.US_ASCII));
            return new EncryptedCode(version, encode(ciphertext), encode(nonce), requireKeyId());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Could not encrypt completion code", ex);
        }
    }

    @Override
    public String decrypt(String requestId, EncryptedCode encryptedCode) {
        if (encryptedCode == null) throw new IllegalArgumentException("encryptedCode is required");
        try {
            byte[] nonce = Base64.getUrlDecoder().decode(encryptedCode.nonce());
            if (nonce.length != NONCE_LENGTH_BYTES) throw new IllegalArgumentException("Invalid completion code nonce");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encryptionKeyFor(encryptedCode.keyId()), "AES"),
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
            cipher.updateAAD(aad(requestId, encryptedCode.version()));
            return new String(cipher.doFinal(Base64.getUrlDecoder().decode(encryptedCode.ciphertext())), StandardCharsets.US_ASCII);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("Completion code authentication failed", ex);
        }
    }

    @Override
    public String createVerifier(String requestId, int version, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(decodeKey(hmacKey, 32, "HMAC"), "HmacSHA256"));
            return encode(mac.doFinal(verifierPayload(requestId, version, code)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Could not create completion code verifier", ex);
        }
    }

    @Override
    public boolean matchesVerifier(String requestId, int version, String code, String verifier) {
        if (verifier == null) return false;
        byte[] expected = createVerifier(requestId, version, code).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = verifier.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, actual);
    }

    private byte[] aad(String requestId, int version) {
        requireRequestAndVersion(requestId, version);
        return (requestId + ":" + version).getBytes(StandardCharsets.UTF_8);
    }

    private byte[] verifierPayload(String requestId, int version, String code) {
        requireRequestAndVersion(requestId, version);
        if (code == null || !code.matches("[0-9]{6}")) throw new IllegalArgumentException("code must contain six ASCII digits");
        return (requestId + ":" + version + ":" + code).getBytes(StandardCharsets.US_ASCII);
    }

    private void requireRequestAndVersion(String requestId, int version) {
        if (requestId == null || requestId.isBlank()) throw new IllegalArgumentException("requestId is required");
        if (version < 1) throw new IllegalArgumentException("version must be positive");
    }

    private String requireKeyId() {
        if (keyId == null || keyId.isBlank() || MISSING_KEY.equals(keyId)) {
            throw new IllegalStateException("Completion code key id is unavailable");
        }
        return keyId;
    }

    private byte[] activeEncryptionKey() {
        requireKeyId();
        return decodeKey(encryptionKey, 32, "encryption");
    }

    private byte[] encryptionKeyFor(String requestedKeyId) {
        if (requestedKeyId == null || requestedKeyId.isBlank()) {
            throw new IllegalStateException("Completion code key id is unavailable");
        }
        if (requestedKeyId.equals(requireKeyId())) return activeEncryptionKey();
        String retiredKey = parseEncryptionKeyRing().get(requestedKeyId);
        if (retiredKey == null) throw new IllegalStateException("Unknown completion code key id");
        return decodeKey(retiredKey, 32, "encryption");
    }

    private Map<String, String> parseEncryptionKeyRing() {
        Map<String, String> keys = new HashMap<>();
        if (encryptionKeyRing == null || encryptionKeyRing.isBlank() ||
                NO_RETIRED_KEYS.equalsIgnoreCase(encryptionKeyRing.trim())) return keys;
        for (String entry : encryptionKeyRing.split(",", -1)) {
            int separator = entry.indexOf(':');
            if (separator < 1 || separator == entry.length() - 1) {
                throw new IllegalStateException("Completion code encryption key ring has invalid format");
            }
            String retiredKeyId = entry.substring(0, separator).trim();
            String value = entry.substring(separator + 1).trim();
            if (retiredKeyId.isBlank() || retiredKeyId.equals(keyId) || keys.putIfAbsent(retiredKeyId, value) != null) {
                throw new IllegalStateException("Completion code encryption key ring has invalid key ids");
            }
            decodeKey(value, 32, "retired encryption");
        }
        return keys;
    }

    private byte[] decodeKey(String value, int expectedBytes, String purpose) {
        if (value == null || value.isBlank() || MISSING_KEY.equals(value)) {
            throw new IllegalStateException("Completion code " + purpose + " key is unavailable");
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            if (decoded.length != expectedBytes) throw new IllegalStateException("Completion code " + purpose + " key must be " + expectedBytes + " bytes");
            return decoded;
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Completion code " + purpose + " key must be base64", ex);
        }
    }

    private String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
