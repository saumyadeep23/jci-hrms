package in.gov.jci.hrms.security.onboarding;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * ONBOARDING_SECURITY_REQUIREMENTS.md exact-domain rule: official email must be non-null,
 * non-blank, syntactically valid, and have an EXACT domain of jcimail.in - not a suffix/substring
 * match (rejects "user@subdomain.jcimail.in", "user@fakejcimail.in", "user@jcimail.in.evil.com").
 * Username = normalized official email (trim + lowercase, RFC email case-insensitivity
 * convention, applied consistently to both local part and domain per
 * ONBOARDING_SECURITY_REQUIREMENTS.md §3's explicit decision).
 */
public final class OfficialEmailValidator {

    public static final String REQUIRED_DOMAIN = "jcimail.in";

    // Deliberately simple (no full RFC 5322 grammar) - good enough to reject obviously malformed
    // input while the exact-domain check below is what actually enforces the security-relevant rule.
    private static final Pattern SYNTAX = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private OfficialEmailValidator() {
    }

    public enum Result {
        VALID,
        MISSING,
        MALFORMED,
        WRONG_DOMAIN
    }

    public static Result validate(String rawEmail) {
        if (rawEmail == null || rawEmail.isBlank()) {
            return Result.MISSING;
        }
        String trimmed = rawEmail.trim();
        if (!SYNTAX.matcher(trimmed).matches()) {
            return Result.MALFORMED;
        }
        int at = trimmed.lastIndexOf('@');
        String domain = trimmed.substring(at + 1);
        if (!domain.equalsIgnoreCase(REQUIRED_DOMAIN)) {
            return Result.WRONG_DOMAIN;
        }
        return Result.VALID;
    }

    /** Only call once validate() returned VALID - normalizes to lowercase for use as a username. */
    public static String normalize(String rawEmail) {
        return rawEmail.trim().toLowerCase(Locale.ROOT);
    }
}
