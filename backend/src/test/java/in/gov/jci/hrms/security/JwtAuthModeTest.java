package in.gov.jci.hrms.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthModeTest {

    @Test
    void parsesCanonicalValues_caseAndHyphenTolerant() {
        assertThat(JwtAuthMode.parse("oidc")).isEqualTo(JwtAuthMode.OIDC);
        assertThat(JwtAuthMode.parse("OIDC")).isEqualTo(JwtAuthMode.OIDC);
        assertThat(JwtAuthMode.parse("local-dev")).isEqualTo(JwtAuthMode.LOCAL_DEV);
        assertThat(JwtAuthMode.parse("LOCAL_DEV")).isEqualTo(JwtAuthMode.LOCAL_DEV);
        assertThat(JwtAuthMode.parse(" local-dev ")).isEqualTo(JwtAuthMode.LOCAL_DEV);
    }

    @Test
    void unrecognizedOrBlank_returnsNull() {
        assertThat(JwtAuthMode.parse(null)).isNull();
        assertThat(JwtAuthMode.parse("")).isNull();
        assertThat(JwtAuthMode.parse("   ")).isNull();
        assertThat(JwtAuthMode.parse("keycloak")).isNull();
    }
}
