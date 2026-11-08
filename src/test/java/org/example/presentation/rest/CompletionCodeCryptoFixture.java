package org.example.presentation.rest;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import org.example.application.port.out.CompletionCodeCryptoPort;

@Alternative
@Priority(1)
@ApplicationScoped
public class CompletionCodeCryptoFixture implements CompletionCodeCryptoPort {
    @Override public String generateCode() { return "001234"; }
    @Override public EncryptedCode encrypt(String requestId, int version, String code) {
        return new EncryptedCode(version, "cipher", "nonce", "test-key");
    }
    @Override public String decrypt(String requestId, EncryptedCode encryptedCode) { return "001234"; }
    @Override public String createVerifier(String requestId, int version, String code) { return "test-verifier:" + code; }
    @Override public boolean matchesVerifier(String requestId, int version, String code, String verifier) {
        return verifier != null && verifier.equals(createVerifier(requestId, version, code));
    }
}
