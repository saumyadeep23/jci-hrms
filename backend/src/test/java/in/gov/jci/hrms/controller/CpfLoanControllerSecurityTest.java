package in.gov.jci.hrms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.jci.hrms.dto.CpfLoanSanctionRequest;
import in.gov.jci.hrms.dto.CpfLoanSettlementRequest;
import in.gov.jci.hrms.dto.RejectRemarksRequest;
import in.gov.jci.hrms.entity.CpfLoanSettlementMode;
import in.gov.jci.hrms.security.SecurityConfig;
import in.gov.jci.hrms.service.CpfLoanApplicationService;
import in.gov.jci.hrms.service.CpfLoanSettlementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SEC-010 (docs/security/RBAC_MIGRATION_REPORT.md): proves the legacy SUPER_ADMIN god-role can no longer
 * bypass the CPF_ADMIN/FINANCE_ADMIN checker gate on sanction/disburse/reject/settle-cash - the four CPF
 * financial approval actions SEC-003's service-layer maker != checker rule protects - while confirming the
 * legitimate functional role (CPF_ADMIN) is unaffected.
 */
@WebMvcTest(CpfLoanController.class)
@Import(SecurityConfig.class)
class CpfLoanControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CpfLoanApplicationService cpfLoanApplicationService;

    @MockBean
    private CpfLoanSettlementService cpfLoanSettlementService;

    private CpfLoanSanctionRequest sanctionRequest() {
        return new CpfLoanSanctionRequest(new BigDecimal("12000.00"), "SANC/SEC010/01", LocalDate.now(), 12, null, null, null, null);
    }

    private CpfLoanSettlementRequest settlementRequest() {
        return new CpfLoanSettlementRequest(new BigDecimal("12000.00"), BigDecimal.ZERO, CpfLoanSettlementMode.CASH,
                "CASH-SEC010-01", LocalDate.now(), LocalDate.now(), "TRUST-BANK-01", null, "SEC-010 test");
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void sanction_withOnlySuperAdminRole_returns403() throws Exception {
        mockMvc.perform(put("/api/v1/payroll/trust/loans/1/sanction")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sanctionRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void disburse_withOnlySuperAdminRole_returns403() throws Exception {
        mockMvc.perform(put("/api/v1/payroll/trust/loans/1/disburse"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void reject_withOnlySuperAdminRole_returns403() throws Exception {
        mockMvc.perform(put("/api/v1/payroll/trust/loans/1/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RejectRemarksRequest("not eligible"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void settleCash_withOnlySuperAdminRole_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/payroll/trust/loans/1/settle-cash")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(settlementRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CPF_ADMIN")
    void sanction_withCpfAdminRole_isNotForbidden() throws Exception {
        mockMvc.perform(put("/api/v1/payroll/trust/loans/1/sanction")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sanctionRequest())))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus()).isNotEqualTo(403));
    }
}
