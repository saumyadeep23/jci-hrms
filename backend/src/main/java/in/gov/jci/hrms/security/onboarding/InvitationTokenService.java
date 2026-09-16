package in.gov.jci.hrms.security.onboarding;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * ONBOARDING_SECURITY_REQUIREMENTS.md token security: SecureRandom, >=256 bits before encoding,
 * single-use, hash-only persistence. The raw token is returned to the caller exactly once (to be
 * emailed) and never persisted - only sha256Hex(rawToken) is ever stored. SHA-256 (not a slow KDF
 * like bcrypt) is appropriate here specifically because the input is a full-entropy 256-bit random
 * value, not a human-chosen password - there is nothing for an attacker to dictionary/brute-force
 * against, unlike a password hash.
 */
@Component
public class InvitationTokenService {

    private static final int TOKEN_BYTES = 32; // 256 bits
    private final SecureRandom secureRandom = new SecureRandom();

    public String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is a mandatory Java algorithm - this should be unreachable", e);
        }
    }
}
