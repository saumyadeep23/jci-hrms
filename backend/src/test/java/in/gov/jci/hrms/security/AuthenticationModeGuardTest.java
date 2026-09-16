package in.gov.jci.hrms.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Direct, no-Spring-context, no-network coverage of the SEC-001 fail-closed
 * decision logic (docs/security/SEC_001_002_REMEDIATION.md, spec section 12
 * items A-D). SecurityConfigJwtDecoderTest additionally exercises this
 * through the real SecurityConfig.jwtDecoder() bean method to catch any
 * wiring mistake between the two.
 */
class AuthenticationModeGuardTest {

    private static final String DEFAULT_SECRET = AuthenticationModeGuard.LOCAL_DEV_DEFAULT_SECRET;
    private static final String ROTATED_SECRET = "a-real-rotated-secret-value-not-the-published-default-123456";
    private static final String ISSUER = "https://idp.example.com/realms/jci-hrms";

    // ---- A: local/dev mode can proceed ----

    @Test
    void devProfile_localDevMode_isAllowed() {
        AuthenticationModeGuard.validate(JwtAuthMode.LOCAL_DEV, false, "", DEFAULT_SECRET);
        // no exception = success
    }

    @Test
    void devProfile_oidcModeWithIssuer_isAllowed() {
        AuthenticationModeGuard.validate(JwtAuthMode.OIDC, false, ISSUER, DEFAULT_SECRET);
    }

    @Test
    void devProfile_oidcModeWithoutIssuer_failsClosed() {
        assertThatThrownBy(() -> AuthenticationModeGuard.validate(JwtAuthMode.OIDC, false, "", DEFAULT_SECRET))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("issuer-uri");
    }

    // ---- B/D: production-like + local-dev mode must fail closed, regardless of which production-like profile ----

    @Test
    void productionLikeProfile_localDevMode_failsClosed() {
        assertThatThrownBy(() -> AuthenticationModeGuard.validate(JwtAuthMode.LOCAL_DEV, true, "", DEFAULT_SECRET))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("local-dev JWT authentication is not permitted");
    }

    @Test
    void stagingProfile_localDevMode_failsClosed() {
        boolean productionLike = AuthenticationModeGuard.isProductionLike(new String[]{"staging"});

        assertThatThrownBy(() -> AuthenticationModeGuard.validate(JwtAuthMode.LOCAL_DEV, productionLike, "", DEFAULT_SECRET))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void prodProfile_localDevMode_failsClosed() {
        boolean productionLike = AuthenticationModeGuard.isProductionLike(new String[]{"prod"});

        assertThatThrownBy(() -> AuthenticationModeGuard.validate(JwtAuthMode.LOCAL_DEV, productionLike, "", DEFAULT_SECRET))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---- C: production-like without OIDC configuration must fail closed ----

    @Test
    void productionLikeProfile_oidcModeWithoutIssuer_failsClosed() {
        assertThatThrownBy(() -> AuthenticationModeGuard.validate(JwtAuthMode.OIDC, true, "", ROTATED_SECRET))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("issuer-uri");
    }

    @Test
    void productionLikeProfile_oidcModeWithBlankIssuer_treatedSameAsMissing() {
        assertThatThrownBy(() -> AuthenticationModeGuard.validate(JwtAuthMode.OIDC, true, "   ", ROTATED_SECRET))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("issuer-uri");
    }

    // ---- production-like + default secret still active must fail closed even with mode/issuer correct ----

    @Test
    void productionLikeProfile_oidcModeWithDefaultSecretStillSet_failsClosed() {
        assertThatThrownBy(() -> AuthenticationModeGuard.validate(JwtAuthMode.OIDC, true, ISSUER, DEFAULT_SECRET))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("published default");
    }

    // ---- The one combination that must be allowed in a production-like environment ----

    @Test
    void productionLikeProfile_oidcModeWithIssuerAndRotatedSecret_isAllowed() {
        AuthenticationModeGuard.validate(JwtAuthMode.OIDC, true, ISSUER, ROTATED_SECRET);
    }

    // ---- Unrecognized/blank mode must fail closed everywhere, not just in production ----

    @Test
    void nullMode_failsClosed_evenOutsideProduction() {
        assertThatThrownBy(() -> AuthenticationModeGuard.validate(null, false, ISSUER, DEFAULT_SECRET))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("security.auth.mode");
    }

    @Test
    void nullMode_failsClosed_inProduction() {
        assertThatThrownBy(() -> AuthenticationModeGuard.validate(null, true, ISSUER, ROTATED_SECRET))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---- J: no thrown message ever contains the secret's actual value ----

    @Test
    void exceptionMessages_neverContainTheSecretValue() {
        assertThatThrownBy(() -> AuthenticationModeGuard.validate(JwtAuthMode.OIDC, true, ISSUER, DEFAULT_SECRET))
                .hasMessageNotContaining(DEFAULT_SECRET);
        assertThatThrownBy(() -> AuthenticationModeGuard.validate(JwtAuthMode.LOCAL_DEV, true, "", DEFAULT_SECRET))
                .hasMessageNotContaining(DEFAULT_SECRET);
        assertThatThrownBy(() -> AuthenticationModeGuard.validate(JwtAuthMode.OIDC, false, "", ROTATED_SECRET))
                .hasMessageNotContaining(ROTATED_SECRET);
    }

    // ---- isProductionLike ----

    @Test
    void isProductionLike_detectsStagingAndProdCaseInsensitively() {
        assertThat(AuthenticationModeGuard.isProductionLike(new String[]{"staging"})).isTrue();
        assertThat(AuthenticationModeGuard.isProductionLike(new String[]{"PROD"})).isTrue();
        assertThat(AuthenticationModeGuard.isProductionLike(new String[]{"production"})).isTrue();
        assertThat(AuthenticationModeGuard.isProductionLike(new String[]{"dev"})).isFalse();
        assertThat(AuthenticationModeGuard.isProductionLike(new String[]{"test"})).isFalse();
        assertThat(AuthenticationModeGuard.isProductionLike(new String[]{})).isFalse();
        assertThat(AuthenticationModeGuard.isProductionLike(null)).isFalse();
    }

    @Test
    void isProductionLike_trueIfAnyActiveProfileMatches() {
        assertThat(AuthenticationModeGuard.isProductionLike(new String[]{"dev", "staging"})).isTrue();
    }
}
