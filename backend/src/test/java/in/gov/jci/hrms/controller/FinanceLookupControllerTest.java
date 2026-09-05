package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.IfscLookupResponse;
import in.gov.jci.hrms.security.SecurityConfig;
import in.gov.jci.hrms.service.IfscLookupService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PIMS_SPEC.md route alias for LookupController's /api/lookups/ifsc - see FinanceLookupController. */
@WebMvcTest(FinanceLookupController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "EMPLOYEE")
class FinanceLookupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IfscLookupService ifscLookupService;

    @Test
    void ifsc_whenFound_returns200WithBankAndBranch() throws Exception {
        when(ifscLookupService.lookup("SBIN0000001"))
                .thenReturn(new IfscLookupResponse("SBIN0000001", true, "State Bank of India", "Mumbai Main", null));

        mockMvc.perform(get("/api/v1/finance/ifsc/SBIN0000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.bankName").value("State Bank of India"))
                .andExpect(jsonPath("$.branch").value("Mumbai Main"));
    }

    @Test
    void ifsc_whenLookupFails_stillReturns200WithFoundFalse() throws Exception {
        when(ifscLookupService.lookup("AAAA0000001"))
                .thenReturn(new IfscLookupResponse("AAAA0000001", false, null, null, "No records found"));

        mockMvc.perform(get("/api/v1/finance/ifsc/AAAA0000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(false));
    }

    @Test
    @WithAnonymousUser
    void ifsc_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/finance/ifsc/SBIN0000001"))
                .andExpect(status().isUnauthorized());
    }
}
