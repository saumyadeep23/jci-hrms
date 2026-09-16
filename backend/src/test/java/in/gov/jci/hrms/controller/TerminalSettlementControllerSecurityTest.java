package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.entity.TerminalSettlement;
import in.gov.jci.hrms.security.RbacSecurity;
import in.gov.jci.hrms.security.SecurityConfig;
import in.gov.jci.hrms.service.TerminalSettlementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Final RBAC business-authority closure (docs/security/RBAC_MIGRATION_REPORT.md): approve() is the
 * final financial authorization currently implemented in the terminal-settlement lifecycle (see
 * TerminalSettlementService.approve()'s own trace - validates bank/beneficiary allocation and
 * irreversibly debits the encashed leave ledger). It now requires DISBURSEMENT_AUTHORIZE
 * (FIN_ADMIN_DISB's permission, reused as-is). Neither SYSTEM_ADMIN nor HR_ADMIN (the HR
 * preparation-stage role, unaffected on every other endpoint in this controller) may reach it merely
 * by role membership - only the permission matters.
 */
@WebMvcTest(TerminalSettlementController.class)
@Import(SecurityConfig.class)
class TerminalSettlementControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TerminalSettlementService terminalSettlementService;

    @MockBean(name = "rbac")
    private RbacSecurity rbac;

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void approve_withOnlySuperAdminRole_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/settlements/1/approve"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void approve_withOnlyHrAdminRole_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/settlements/1/approve"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void approve_withUnrelatedRole_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/settlements/1/approve"))
                .andExpect(status().isForbidden());
    }

    // Full response serialization needs many TerminalSettlement fields unrelated to authorization -
    // asserting the service method was actually invoked is sufficient proof that @PreAuthorize let
    // the DISBURSEMENT_AUTHORIZE holder past the security layer (a 403 would short-circuit before
    // ever calling the service). A downstream failure building the response from an incompletely-
    // stubbed mock entity is expected and ignored here - it happens strictly after the service call
    // this test actually cares about.
    @Test
    @WithMockUser(roles = "USER")
    void approve_withDisbursementAuthorizePermission_reachesTheService() {
        when(rbac.hasPermission(any(), eq("DISBURSEMENT_AUTHORIZE"))).thenReturn(true);
        TerminalSettlement settlement = mock(TerminalSettlement.class);
        when(terminalSettlementService.approve(1L)).thenReturn(settlement);

        try {
            mockMvc.perform(post("/api/v1/settlements/1/approve"));
        } catch (Exception ignored) {
            // response-building failure, not an authorization failure - see comment above
        }
        org.mockito.Mockito.verify(terminalSettlementService).approve(1L);
    }
}
