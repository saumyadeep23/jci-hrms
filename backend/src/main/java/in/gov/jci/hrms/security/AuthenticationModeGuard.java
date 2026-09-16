package in.gov.jci.hrms.security;

import java.util.Locale;
import java.util.Set;

/**
 * Pre-flight validation for {@link SecurityConfig#jwtDecoder()}, extracted
 * into its own side-effect-free class so the fail-closed decision logic is
 * directly unit-testable without a Spring context, an embedded datasource,
 * or network access - see SEC-001 in
 * docs/security/PRE_RBAC_SECURITY_AUDIT.md and the fix writeup in
 * docs/security/SEC_001_002_REMEDIATION.md.
 *
 * <p>Every failure throws {@link IllegalStateException} from within a
 * {@code @Bean} method, which is a standard Spring mechanism for aborting
 * {@code ApplicationContext} refresh (i.e. it fails application startup) -
 * exactly the "fail closed" property SEC-001 requires. No thrown message
 * ever includes a secret's value.
 */
final class AuthenticationModeGuard {

    /** The literal default published in application.yml - comparing against it (never logging it) is how a production-like environment is caught still using it. */
    static final String LOCAL_DEV_DEFAULT_SECRET = "local-dev-only-secret-key-never-use-in-production-32bytes";

    /** Spring profile names treated as "this is not a developer's own machine or CI" - mirrors Terraform's var.environment vocabulary (infra/variables.tf), so the same value already driving RDS/KMS/WAF hardening also drives this guard. */
    static final Set<String> PRODUCTION_LIKE_PROFILES = Set.of("staging", "prod", "production");

    private AuthenticationModeGuard() {
    }

    static boolean isProductionLike(String[] activeProfiles) {
        if (activeProfiles == null) {
            return false;
        }
        for (String profile : activeProfiles) {
            if (profile != null && PRODUCTION_LIKE_PROFILES.contains(profile.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Throws when the requested JWT authentication configuration is unsafe
     * for the given environment. Called from {@code jwtDecoder()} before any
     * decoder is built - a production-like profile can reach a returned
     * (non-throwing) call only via mode=OIDC with a real issuer-uri and a
     * rotated local-dev secret.
     */
    static void validate(JwtAuthMode mode, boolean productionLike, String issuerUri, String localDevSecret) {
        if (mode == null) {
            throw new IllegalStateException(
                    "security.auth.mode must be set to 'oidc' or 'local-dev' (an unrecognized or blank value was supplied). Refusing to start.");
        }
        if (productionLike) {
            if (mode != JwtAuthMode.OIDC) {
                throw new IllegalStateException(
                        "Active Spring profile is production-like; security.auth.mode must be 'oidc' in this environment "
                                + "(local-dev JWT authentication is not permitted). Refusing to start.");
            }
            if (issuerUri == null || issuerUri.isBlank()) {
                throw new IllegalStateException(
                        "Active Spring profile is production-like and security.auth.mode=oidc, but no "
                                + "spring.security.oauth2.resourceserver.jwt.issuer-uri is configured. Refusing to start.");
            }
            if (LOCAL_DEV_DEFAULT_SECRET.equals(localDevSecret)) {
                throw new IllegalStateException(
                        "Active Spring profile is production-like but security.jwt.local-dev-secret is still set to its "
                                + "published default value. Refusing to start.");
            }
            return;
        }
        if (mode == JwtAuthMode.OIDC && (issuerUri == null || issuerUri.isBlank())) {
            throw new IllegalStateException(
                    "security.auth.mode=oidc requires spring.security.oauth2.resourceserver.jwt.issuer-uri to be set, "
                            + "even outside a production-like environment. Refusing to start.");
        }
    }
}
