package org.example.application.port.out;

public interface CompletionCodeCryptoPort {
    String generateCode();

    EncryptedCode encrypt(String requestId, int version, String code);

    String decrypt(String requestId, EncryptedCode encryptedCode);

    String createVerifier(String requestId, int version, String code);

    boolean matchesVerifier(String requestId, int version, String code, String verifier);

    record EncryptedCode(int version, String ciphertext, String nonce, String keyId) {
    }
}
