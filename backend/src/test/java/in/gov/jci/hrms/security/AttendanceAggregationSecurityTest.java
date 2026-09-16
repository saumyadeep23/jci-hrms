package in.gov.jci.hrms.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * canActOnBehalfOfOthers was extracted from canEvaluateFor as part of the
 * SEC-002 remediation (docs/security/SEC_001_002_REMEDIATION.md) so
 * MobilePunchController could reuse this bean's existing self-or-HR/admin
 * rule instead of inventing a second ownership mechanism. This suite covers
 * both methods to prove the extraction preserved canEvaluateFor's original
 * behaviour.
 */
class AttendanceAggregationSecurityTest {

    private final AttendanceAggregationSecurity security = new AttendanceAggregationSecurity();

    private JwtAuthenticationToken tokenFor(Long employeeId, String... roles) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("test-subject")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600));
        if (employeeId != null) {
            builder.claim("employee_id", String.valueOf(employeeId));
        }
        List<SimpleGrantedAuthority> authorities = List.of(roles).stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        return new JwtAuthenticationToken(builder.build(), authorities);
    }

    // ---- canEvaluateFor ----

    @Test
    void canEvaluateFor_nullEmployeeId_isAlwaysAllowed() {
        assertThat(security.canEvaluateFor(tokenFor(1L, "EMPLOYEE"), null)).isTrue();
    }

    @Test
    void canEvaluateFor_ownEmployeeId_isAllowed() {
        assertThat(security.canEvaluateFor(tokenFor(1L, "EMPLOYEE"), 1L)).isTrue();
    }

    @Test
    void canEvaluateFor_someoneElsesEmployeeId_deniedForOrdinaryEmployee() {
        assertThat(security.canEvaluateFor(tokenFor(1L, "EMPLOYEE"), 2L)).isFalse();
    }

    @Test
    void canEvaluateFor_someoneElsesEmployeeId_allowedForHrAdmin() {
        assertThat(security.canEvaluateFor(tokenFor(1L, "HR_ADMIN"), 2L)).isTrue();
    }

    @Test
    void canEvaluateFor_someoneElsesEmployeeId_allowedForSuperAdmin() {
        assertThat(security.canEvaluateFor(tokenFor(1L, "SUPER_ADMIN"), 2L)).isTrue();
    }

    @Test
    void canEvaluateFor_someoneElsesEmployeeId_deniedForFinanceAdmin() {
        assertThat(security.canEvaluateFor(tokenFor(1L, "FINANCE_ADMIN"), 2L)).isFalse();
    }

    // ---- canActOnBehalfOfOthers ----

    @Test
    void canActOnBehalfOfOthers_trueForHrAdmin() {
        assertThat(security.canActOnBehalfOfOthers(tokenFor(1L, "HR_ADMIN"))).isTrue();
    }

    @Test
    void canActOnBehalfOfOthers_trueForSuperAdmin() {
        assertThat(security.canActOnBehalfOfOthers(tokenFor(1L, "SUPER_ADMIN"))).isTrue();
    }

    @Test
    void canActOnBehalfOfOthers_falseForOrdinaryEmployee() {
        assertThat(security.canActOnBehalfOfOthers(tokenFor(1L, "EMPLOYEE"))).isFalse();
    }

    @Test
    void canActOnBehalfOfOthers_falseForFinanceAdmin() {
        assertThat(security.canActOnBehalfOfOthers(tokenFor(1L, "FINANCE_ADMIN"))).isFalse();
    }

    @Test
    void canActOnBehalfOfOthers_falseForNullAuthentication() {
        assertThat(security.canActOnBehalfOfOthers(null)).isFalse();
    }
}
