package in.gov.jci.hrms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.jci.hrms.dto.CpfBalanceLedgerRequest;
import in.gov.jci.hrms.dto.CpfBalanceLedgerResponse;
import in.gov.jci.hrms.dto.NonRefundableWithdrawalRequest;
import in.gov.jci.hrms.dto.PfDiversionResponse;
import in.gov.jci.hrms.dto.RefundableLoanDiversionRequest;
import in.gov.jci.hrms.entity.DiversionStatus;
import in.gov.jci.hrms.entity.DiversionType;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.security.SecurityConfig;
import in.gov.jci.hrms.service.PfLedgerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PfLedgerController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "CPF_ADMIN")
class PfLedgerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PfLedgerService pfLedgerService;

    @Test
    void createLedger_withValidRequest_returns201() throws Exception {
        CpfBalanceLedgerRequest request = new CpfBalanceLedgerRequest(1L, new BigDecimal("10000.00"),
                new BigDecimal("10000.00"), new BigDecimal("2000.00"));
        CpfBalanceLedgerResponse response = new CpfBalanceLedgerResponse(1L, 1L, "EMP-001",
                new BigDecimal("10000.00"), new BigDecimal("10000.00"), new BigDecimal("2000.00"), null, Instant.now());
        when(pfLedgerService.createLedger(any(CpfBalanceLedgerRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/cpf-ledger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.employeeFundBalance").value(10000.00));
    }

    @Test
    void createRefundableLoanDiversion_withInsufficientBalance_returns400() throws Exception {
        RefundableLoanDiversionRequest request = new RefundableLoanDiversionRequest(1L, new BigDecimal("5000.00"), 2L,
                LocalDate.of(2026, 1, 1));
        when(pfLedgerService.createRefundableLoanDiversion(any(RefundableLoanDiversionRequest.class)))
                .thenThrow(new BusinessRuleViolationException("Insufficient employee PF balance"));

        mockMvc.perform(post("/api/cpf-ledger/diversions/refundable-loan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRefundableLoanDiversion_withValidRequest_returns201() throws Exception {
        RefundableLoanDiversionRequest request = new RefundableLoanDiversionRequest(1L, new BigDecimal("5000.00"), 2L,
                LocalDate.of(2026, 1, 1));
        PfDiversionResponse response = new PfDiversionResponse(10L, 1L, "EMP-001", DiversionType.REFUNDABLE_LOAN,
                new BigDecimal("5000.00"), new BigDecimal("5000.00"), BigDecimal.ZERO, BigDecimal.ZERO, 2L,
                LocalDate.of(2026, 1, 1), DiversionStatus.ACTIVE, Instant.now());
        when(pfLedgerService.createRefundableLoanDiversion(any(RefundableLoanDiversionRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/cpf-ledger/diversions/refundable-loan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.diversionType").value("REFUNDABLE_LOAN"));
    }

    @Test
    void createNonRefundableWithdrawal_withValidRequest_returns201() throws Exception {
        NonRefundableWithdrawalRequest request = new NonRefundableWithdrawalRequest(1L, new BigDecimal("3000.00"),
                new BigDecimal("2000.00"), new BigDecimal("500.00"), LocalDate.of(2026, 1, 1));
        PfDiversionResponse response = new PfDiversionResponse(11L, 1L, "EMP-001", DiversionType.NON_REFUNDABLE_WITHDRAWAL,
                new BigDecimal("5500.00"), new BigDecimal("3000.00"), new BigDecimal("2000.00"), new BigDecimal("500.00"),
                null, LocalDate.of(2026, 1, 1), DiversionStatus.ACTIVE, Instant.now());
        when(pfLedgerService.createNonRefundableWithdrawal(any(NonRefundableWithdrawalRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/cpf-ledger/diversions/non-refundable-withdrawal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAmount").value(5500.00));
    }

    @Test
    void settle_returns200() throws Exception {
        PfDiversionResponse response = new PfDiversionResponse(10L, 1L, "EMP-001", DiversionType.REFUNDABLE_LOAN,
                new BigDecimal("5000.00"), new BigDecimal("5000.00"), BigDecimal.ZERO, BigDecimal.ZERO, 2L,
                LocalDate.of(2026, 1, 1), DiversionStatus.SETTLED, Instant.now());
        when(pfLedgerService.settle(10L)).thenReturn(response);

        mockMvc.perform(post("/api/cpf-ledger/diversions/10/settle"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SETTLED"));
    }

    @Test
    @WithAnonymousUser
    void settle_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(post("/api/cpf-ledger/diversions/10/settle"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void settle_withWrongRole_returns403() throws Exception {
        mockMvc.perform(post("/api/cpf-ledger/diversions/10/settle"))
                .andExpect(status().isForbidden());
    }

    // Non-financial RBAC migration pass (docs/security/RBAC_MIGRATION_REPORT.md): this module was
    // missed by the earlier SEC-003/004 CPF/JCIECCS closure - settle is a checker-shaped financial
    // action, SUPER_ADMIN alone must not be able to reach it.
    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void settle_withOnlySuperAdminRole_returns403() throws Exception {
        mockMvc.perform(post("/api/cpf-ledger/diversions/10/settle"))
                .andExpect(status().isForbidden());
    }
}
