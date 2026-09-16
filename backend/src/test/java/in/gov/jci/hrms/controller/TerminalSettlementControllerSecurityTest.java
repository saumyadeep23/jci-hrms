package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.security.SecurityConfig;
import in.gov.jci.hrms.service.TerminalSettlementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Non-financial RBAC migration (docs/security/RBAC_MIGRATION_REPORT.md): proves the legacy SUPER_ADMIN
 * god-role can no longer independently approve a terminal/separation financial settlement - HR_ADMIN
 * (the class's other, functional role) remains unaffected. The exact final checker-role mapping is
 * REQUIRES_BUSINESS_CONFIRMATION (see RBAC_MIGRATION_REPORT.md) - this test only proves the SUPER_ADMIN
 * bypass itself is closed, not that HR_ADMIN is the permanently-correct owner.
 */
@WebMvcTest(TerminalSettlementController.class)
@Import(SecurityConfig.class)
class TerminalSettlementControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TerminalSettlementService terminalSettlementService;

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void approve_withOnlySuperAdminRole_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/settlements/1/approve"))
                .andExpect(status().isForbidden());
    }

    // Full response serialization needs many TerminalSettlement fields unrelated to authorization -
    // asserting the service method was actually invoked is sufficient proof that @PreAuthorize let
    // HR_ADMIN past the security layer (a 403 would short-circuit before ever calling the service).
    // A downstream NPE building the response from an incompletely-stubbed mock entity is expected and
    // ignored here - it happens strictly after the service call this test actually cares about.
    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void approve_withHrAdminRole_reachesTheService() {
        try {
            mockMvc.perform(post("/api/v1/settlements/1/approve"));
        } catch (Exception ignored) {
            // response-building failure, not an authorization failure - see comment above
        }
        org.mockito.Mockito.verify(terminalSettlementService).approve(1L);
    }
}
