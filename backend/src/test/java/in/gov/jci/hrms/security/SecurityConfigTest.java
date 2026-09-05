package in.gov.jci.hrms.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.jci.hrms.controller.DepartmentController;
import in.gov.jci.hrms.controller.PayrollRunController;
import in.gov.jci.hrms.controller.PfLedgerController;
import in.gov.jci.hrms.dto.CpfBalanceLedgerResponse;
import in.gov.jci.hrms.dto.DepartmentResponse;
import in.gov.jci.hrms.dto.PayrollRunResponse;
import in.gov.jci.hrms.entity.PayrollRunStatus;
import in.gov.jci.hrms.entity.PayrollRunType;
import in.gov.jci.hrms.service.DepartmentService;
import in.gov.jci.hrms.service.PayrollRunService;
import in.gov.jci.hrms.service.PfLedgerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Two things this suite verifies that per-controller test classes don't
 * cover on their own:
 * <ol>
 *   <li>JwtRoleConverter/SecurityUtils correctly translate a raw JWT's
 *   Keycloak-shaped role claims and employee_id claim into Spring Security
 *   types - the mechanism every @PreAuthorize check in this app relies on.
 *   <li>The role matrix holds across controllers from three different tiers
 *   (HR_ADMIN, FINANCE_ADMIN, CPF_ADMIN/COOP_ADMIN) wired through the real
 *   SecurityConfig filter chain in one slice, not just individually.
 * </ol>
 * A full @SpringBootTest is deliberately avoided here - this app has no
 * embedded/test datasource configured anywhere (see EmployeeRepositoryTest,
 * the one test excluded from the standard run because it needs a live
 * Postgres), so a full-context security test would be similarly unusable
 * without one.
 */
class SecurityConfigTest {

    // ---- JwtRoleConverter ----

    @Test
    void jwtRoleConverter_extractsRealmAccessRoles() {
        Jwt jwt = jwtWithClaims(Map.of("realm_access", Map.of("roles", List.of("HR_ADMIN", "SUPER_ADMIN"))));

        Collection<GrantedAuthority> authorities = new JwtRoleConverter().convert(jwt);

        assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_HR_ADMIN", "ROLE_SUPER_ADMIN");
    }

    @Test
    void jwtRoleConverter_extractsResourceAccessRoles() {
        Jwt jwt = jwtWithClaims(Map.of("resource_access",
                Map.of("hrms-backend", Map.of("roles", List.of("FINANCE_ADMIN")))));

        Collection<GrantedAuthority> authorities = new JwtRoleConverter().convert(jwt);

        assertThat(authorities).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_FINANCE_ADMIN");
    }

    @Test
    void jwtRoleConverter_mergesRealmAndResourceRoles() {
        Jwt jwt = jwtWithClaims(Map.of(
                "realm_access", Map.of("roles", List.of("EMPLOYEE")),
                "resource_access", Map.of("hrms-backend", Map.of("roles", List.of("CPF_ADMIN")))));

        Collection<GrantedAuthority> authorities = new JwtRoleConverter().convert(jwt);

        assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_EMPLOYEE", "ROLE_CPF_ADMIN");
    }

    @Test
    void jwtRoleConverter_withNoRoleClaims_returnsNoAuthorities() {
        Jwt jwt = jwtWithClaims(Map.of());

        Collection<GrantedAuthority> authorities = new JwtRoleConverter().convert(jwt);

        assertThat(authorities).isEmpty();
    }

    // ---- SecurityUtils.currentEmployeeId ----

    @Test
    void currentEmployeeId_fromJwtAuthentication_parsesClaim() {
        Jwt jwt = jwtWithClaims(Map.of("employee_id", "42"));
        JwtAuthenticationToken token = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE")));

        assertThat(SecurityUtils.currentEmployeeId(token)).isEqualTo(42L);
    }

    @Test
    void currentEmployeeId_withoutClaim_returnsNull() {
        Jwt jwt = jwtWithClaims(Map.of());
        JwtAuthenticationToken token = new JwtAuthenticationToken(jwt, List.of());

        assertThat(SecurityUtils.currentEmployeeId(token)).isNull();
    }

    @Test
    void currentEmployeeId_forNonJwtAuthentication_returnsNull() {
        var notJwt = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                new User("hr.admin", "N/A", List.of(new SimpleGrantedAuthority("ROLE_HR_ADMIN"))), null, List.of());

        assertThat(SecurityUtils.currentEmployeeId(notJwt)).isNull();
    }

    private Jwt jwtWithClaims(Map<String, Object> extraClaims) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("test-subject")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600));
        extraClaims.forEach(builder::claim);
        return builder.build();
    }

    // ---- Role matrix across controller tiers (HR_ADMIN / FINANCE_ADMIN / CPF_ADMIN) ----

    @WebMvcTest({DepartmentController.class, PayrollRunController.class, PfLedgerController.class})
    @Import(SecurityConfig.class)
    static class RoleMatrixTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @MockBean
        private DepartmentService departmentService;

        @MockBean
        private PayrollRunService payrollRunService;

        @MockBean
        private PfLedgerService pfLedgerService;

        @MockBean
        private JwtDecoder jwtDecoder;

        @Test
        @WithAnonymousUser
        void allThreeTiers_denyAnonymousWith401() throws Exception {
            mockMvc.perform(get("/api/departments/1")).andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/payroll/runs/1")).andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/cpf-ledger/1")).andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(roles = "HR_ADMIN")
        void hrAdmin_canReachDepartments_butNotPayrollOrCpfLedger() throws Exception {
            when(departmentService.getById(1L)).thenReturn(
                    new DepartmentResponse(1L, "ENG", "Engineering", null, Instant.now(), Instant.now(), null));

            mockMvc.perform(get("/api/departments/1")).andExpect(status().isOk());
            mockMvc.perform(get("/api/payroll/runs/1")).andExpect(status().isForbidden());
            mockMvc.perform(get("/api/cpf-ledger/1")).andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "FINANCE_ADMIN")
        void financeAdmin_canReachPayrollAndCpfLedger_butNotDepartments() throws Exception {
            when(payrollRunService.getById(1L)).thenReturn(new PayrollRunResponse(1L, 2026, 8,
                    LocalDate.of(2026, 7, 26), LocalDate.of(2026, 8, 25), PayrollRunStatus.DRAFT, null, null,
                    PayrollRunType.LIVE, false, Instant.now()));
            when(pfLedgerService.getLedgerByEmployeeId(1L)).thenReturn(new CpfBalanceLedgerResponse(1L, 1L, "EMP-001",
                    new BigDecimal("10000.00"), new BigDecimal("10000.00"), new BigDecimal("2000.00"), null, Instant.now()));

            mockMvc.perform(get("/api/payroll/runs/1")).andExpect(status().isOk());
            mockMvc.perform(get("/api/departments/1")).andExpect(status().isForbidden());
            // FINANCE_ADMIN is explicitly one of PfLedgerController's allowed roles (FR spec) alongside CPF_ADMIN/COOP_ADMIN.
            mockMvc.perform(get("/api/cpf-ledger/1")).andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "CPF_ADMIN")
        void cpfAdmin_canReachCpfLedger_butNotDepartmentsOrPayroll() throws Exception {
            when(pfLedgerService.getLedgerByEmployeeId(1L)).thenReturn(new CpfBalanceLedgerResponse(1L, 1L, "EMP-001",
                    new BigDecimal("10000.00"), new BigDecimal("10000.00"), new BigDecimal("2000.00"), null, Instant.now()));

            mockMvc.perform(get("/api/cpf-ledger/1")).andExpect(status().isOk());
            mockMvc.perform(get("/api/departments/1")).andExpect(status().isForbidden());
            mockMvc.perform(get("/api/payroll/runs/1")).andExpect(status().isForbidden());
        }
    }
}
