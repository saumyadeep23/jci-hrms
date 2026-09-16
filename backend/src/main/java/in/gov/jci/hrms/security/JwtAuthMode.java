package in.gov.jci.hrms.security;

import java.util.Locale;

/**
 * Explicit, named JWT authentication modes for {@link SecurityConfig#jwtDecoder()}.
 * SEC-001 remediation (docs/security/SEC_001_002_REMEDIATION.md): the app
 * must never infer "use local HS256 auth" merely from an issuer-uri being
 * blank - a positive selection via {@code security.auth.mode} is required,
 * and {@link AuthenticationModeGuard} enforces which selections are safe for
 * a given environment.
 */
enum JwtAuthMode {
    OIDC,
    LOCAL_DEV;

    /** Case/format-tolerant ("oidc", "OIDC", "local-dev", "LOCAL_DEV") parse; returns null for anything unrecognized or blank rather than guessing. */
    static JwtAuthMode parse(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        for (JwtAuthMode value : values()) {
            if (value.name().equals(normalized)) {
                return value;
            }
        }
        return null;
    }
}
