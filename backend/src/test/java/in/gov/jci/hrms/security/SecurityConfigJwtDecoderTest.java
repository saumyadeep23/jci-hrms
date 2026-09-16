package in.gov.jci.hrms.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises SecurityConfig's constructor -> jwtDecoder() bean method itself,
 * not just the extracted AuthenticationModeGuard logic (AuthenticationModeGuardTest),
 * to catch any wiring mistake between the two. No Spring ApplicationContext
 * or network access is used - MockEnvironment stands in for the real
 * org.springframework.core.env.Environment. See spec section 12 items A-D
 * and docs/security/SEC_001_002_REMEDIATION.md.
 */
class SecurityConfigJwtDecoderTest {

    private static final String DEFAULT_SECRET = AuthenticationModeGuard.LOCAL_DEV_DEFAULT_SECRET;
    private static final String ROTATED_SECRET = "a-real-rotated-secret-value-not-the-published-default-123456";
    private static final String ISSUER = "https://idp.example.com/realms/jci-hrms";

    /** A: local/dev profile, explicit local-dev mode -> a decoder is built (application "starts"). */
    @Test
    void localDevMode_onNoActiveProfile_buildsDecoder() {
        MockEnvironment env = new MockEnvironment(); // no active profiles == a developer's own machine / CI
        SecurityConfig config = new SecurityConfig("", DEFAULT_SECRET, "local-dev", env);

        assertThat(config.jwtDecoder()).isNotNull();
    }

    @Test
    void localDevMode_onExplicitDevProfile_buildsDecoder() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");
        SecurityConfig config = new SecurityConfig("", DEFAULT_SECRET, "local-dev", env);

        assertThat(config.jwtDecoder()).isNotNull();
    }

    /** B/D: production-like profile with local-dev mode must NOT be able to start. */
    @Test
    void localDevMode_onStagingProfile_failsClosedAtStartup() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("staging");
        SecurityConfig config = new SecurityConfig("", DEFAULT_SECRET, "local-dev", env);

        assertThatThrownBy(config::jwtDecoder).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void localDevMode_onProdProfile_failsClosedAtStartup() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        SecurityConfig config = new SecurityConfig("", DEFAULT_SECRET, "local-dev", env);

        assertThatThrownBy(config::jwtDecoder).isInstanceOf(IllegalStateException.class);
    }

    /** C: production-like profile without OIDC configuration must NOT be able to start, even if mode is correctly set to oidc. */
    @Test
    void oidcMode_onProdProfile_withoutIssuer_failsClosedAtStartup() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        SecurityConfig config = new SecurityConfig("", DEFAULT_SECRET, "oidc", env);

        assertThatThrownBy(config::jwtDecoder)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("issuer-uri");
    }

    /** Even with a correct issuer, a production-like deploy that still carries the published default secret must fail closed. */
    @Test
    void oidcMode_onProdProfile_withIssuerButDefaultSecretStillSet_failsClosedAtStartup() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        SecurityConfig config = new SecurityConfig(ISSUER, DEFAULT_SECRET, "oidc", env);

        assertThatThrownBy(config::jwtDecoder).isInstanceOf(IllegalStateException.class);
    }

    /** An unrecognized security.auth.mode value must fail closed even outside production - never silently default to local-dev. */
    @Test
    void unrecognizedMode_failsClosed_evenInDev() {
        MockEnvironment env = new MockEnvironment();
        SecurityConfig config = new SecurityConfig("", DEFAULT_SECRET, "keycloak", env);

        assertThatThrownBy(config::jwtDecoder).isInstanceOf(IllegalStateException.class);
    }

    /** No thrown message from a real jwtDecoder() failure leaks the secret's value (spec section 12 item J / section 10). */
    @Test
    void startupFailureMessage_neverExposesTheSecretValue() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("staging");
        SecurityConfig config = new SecurityConfig("", DEFAULT_SECRET, "local-dev", env);

        assertThatThrownBy(config::jwtDecoder).hasMessageNotContaining(DEFAULT_SECRET);
    }
}
