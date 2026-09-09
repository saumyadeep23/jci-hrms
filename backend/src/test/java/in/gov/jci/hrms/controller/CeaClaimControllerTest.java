package in.gov.jci.hrms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.jci.hrms.dto.CeaClaimBillPassRequest;
import in.gov.jci.hrms.dto.CeaClaimRejectRequest;
import in.gov.jci.hrms.dto.CeaClaimResponse;
import in.gov.jci.hrms.dto.CeaClaimSubmitRequest;
import in.gov.jci.hrms.dto.CeaClaimVerifyRequest;
import in.gov.jci.hrms.entity.CeaClaimStatus;
import in.gov.jci.hrms.entity.CeaClaimType;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.security.SecurityConfig;
import in.gov.jci.hrms.service.CeaClaimService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CeaClaimController.class)
@Import(SecurityConfig.class)
class CeaClaimControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CeaClaimService ceaClaimService;

    private CeaClaimSubmitRequest validSubmitRequest() {
        return new CeaClaimSubmitRequest("CEA/2026/001", 10L, "2026-2027", CeaClaimType.CEA,
                "ABC School", "REG-1", "V", LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31),
                new BigDecimal("30000.00"), null);
    }

    private CeaClaimResponse responseWithStatus(CeaClaimStatus status) {
        Instant now = Instant.now();
        return new CeaClaimResponse(1L, "CEA/2026/001", 7L, "EMP-007", "Asha Rao", 10L, "Junior Rao", "2026-2027", CeaClaimType.CEA,
                "ABC School", "REG-1", "V", LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31),
                new BigDecimal("30000.00"), new BigDecimal("28125.00"), null, status,
                null, null, null, null, null, null, null, null, null,
                false, null, null, null, now, now);
    }

    // ---- Employee self-service: submit / my-claims ----

    @Test
    void submitClaim_resolvesEmployeeIdFromJwtClaim_returns201() throws Exception {
        when(ceaClaimService.submitClaim(eq(7L), any(CeaClaimSubmitRequest.class)))
                .thenReturn(responseWithStatus(CeaClaimStatus.SUBMITTED));

        mockMvc.perform(post("/api/v1/self-service/cea-claims")
                        .with(jwt().jwt(builder -> builder.claim("employee_id", "7"))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validSubmitRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.claimStatus").value("SUBMITTED"));
    }

    @Test
    void submitClaim_withoutEmployeeIdClaim_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/self-service/cea-claims")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validSubmitRequest())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithAnonymousUser
    void submitClaim_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/self-service/cea-claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validSubmitRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void myClaims_resolvesEmployeeIdFromJwtClaim_returns200() throws Exception {
        when(ceaClaimService.listByEmployee(7L)).thenReturn(List.of(responseWithStatus(CeaClaimStatus.SUBMITTED)));

        mockMvc.perform(get("/api/v1/self-service/cea-claims/my-claims")
                        .with(jwt().jwt(builder -> builder.claim("employee_id", "7"))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].claimStatus").value("SUBMITTED"));
    }

    // ---- HR gate: pending-verification / verify ----

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void pendingVerification_asHrAdmin_returns200() throws Exception {
        when(ceaClaimService.pendingVerification()).thenReturn(List.of(responseWithStatus(CeaClaimStatus.SUBMITTED)));

        mockMvc.perform(get("/api/v1/admin/cea-claims/pending-verification"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].claimStatus").value("SUBMITTED"));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void pendingVerification_wrongRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/cea-claims/pending-verification"))
                .andExpect(status().isForbidden());
    }

    @Test
    void verify_asHrAdminWithEmployeeIdClaim_returns200WithVerifiedStatus() throws Exception {
        // Real HR admins authenticate through the same JWT resource server as everyone else - their
        // token carries both the HR_ADMIN role AND their own employee_id claim (they're an Employee
        // too), which CeaClaimController.verify() resolves as the verifying officer. @WithMockUser
        // produces a non-JWT principal (correctly rejected above), so this needs a real jwt() token.
        when(ceaClaimService.verifyClaim(eq(1L), eq(2L), any(CeaClaimVerifyRequest.class)))
                .thenReturn(responseWithStatus(CeaClaimStatus.VERIFIED));

        mockMvc.perform(put("/api/v1/admin/cea-claims/1/verify")
                        .with(jwt().jwt(builder -> builder.claim("employee_id", "2"))
                                .authorities(new SimpleGrantedAuthority("ROLE_HR_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CeaClaimVerifyRequest(new BigDecimal("27000.00")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimStatus").value("VERIFIED"));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void verify_wrongRole_returns403() throws Exception {
        mockMvc.perform(put("/api/v1/admin/cea-claims/1/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CeaClaimVerifyRequest(null))))
                .andExpect(status().isForbidden());
    }

    // ---- Finance gate: pending-bill-passing / pass-bill ----

    @Test
    @WithMockUser(roles = "FINANCE_ADMIN")
    void pendingBillPassing_asFinanceAdmin_returns200() throws Exception {
        when(ceaClaimService.pendingBillPassing()).thenReturn(List.of(responseWithStatus(CeaClaimStatus.VERIFIED)));

        mockMvc.perform(get("/api/v1/admin/cea-claims/pending-bill-passing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].claimStatus").value("VERIFIED"));
    }

    @Test
    void passBill_asFinanceAdminWithEmployeeIdClaim_returns200WithBillPassedStatus() throws Exception {
        // Same reasoning as verify_asHrAdminWithEmployeeIdClaim_returns200WithVerifiedStatus - a real
        // Finance admin's JWT carries their own employee_id claim too.
        CeaClaimBillPassRequest request = new CeaClaimBillPassRequest(
                new BigDecimal("27000.00"), "BILL/2027/001", LocalDate.of(2027, 4, 5), "SANCTION/2027/001", LocalDate.of(2027, 4, 5));
        when(ceaClaimService.passBill(eq(1L), eq(3L), any(CeaClaimBillPassRequest.class)))
                .thenReturn(responseWithStatus(CeaClaimStatus.BILL_PASSED));

        mockMvc.perform(put("/api/v1/admin/cea-claims/1/pass-bill")
                        .with(jwt().jwt(builder -> builder.claim("employee_id", "3"))
                                .authorities(new SimpleGrantedAuthority("ROLE_FINANCE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimStatus").value("BILL_PASSED"));
    }

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void passBill_wrongRole_returns403() throws Exception {
        CeaClaimBillPassRequest request = new CeaClaimBillPassRequest(
                new BigDecimal("27000.00"), "BILL/2027/001", LocalDate.of(2027, 4, 5), "SANCTION/2027/001", LocalDate.of(2027, 4, 5));

        mockMvc.perform(put("/api/v1/admin/cea-claims/1/pass-bill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // ---- History / Log (either gate) ----

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void listAll_asHrAdmin_returns200() throws Exception {
        when(ceaClaimService.listAll()).thenReturn(List.of(responseWithStatus(CeaClaimStatus.DISBURSED)));

        mockMvc.perform(get("/api/v1/admin/cea-claims"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].claimStatus").value("DISBURSED"));
    }

    @Test
    @WithMockUser(roles = "FINANCE_ADMIN")
    void listAll_asFinanceAdmin_returns200() throws Exception {
        when(ceaClaimService.listAll()).thenReturn(List.of(responseWithStatus(CeaClaimStatus.REJECTED)));

        mockMvc.perform(get("/api/v1/admin/cea-claims"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].claimStatus").value("REJECTED"));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void listAll_wrongRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/cea-claims"))
                .andExpect(status().isForbidden());
    }

    // ---- Reject (either gate) ----

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void reject_asHrAdmin_returns200WithRejectedStatus() throws Exception {
        when(ceaClaimService.rejectClaim(1L, "Ineligible school"))
                .thenReturn(responseWithStatus(CeaClaimStatus.REJECTED));

        mockMvc.perform(put("/api/v1/admin/cea-claims/1/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CeaClaimRejectRequest("Ineligible school"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimStatus").value("REJECTED"));
    }

    @Test
    @WithMockUser(roles = "FINANCE_ADMIN")
    void reject_asFinanceAdmin_returns200WithRejectedStatus() throws Exception {
        when(ceaClaimService.rejectClaim(1L, "Amount overclaimed"))
                .thenReturn(responseWithStatus(CeaClaimStatus.REJECTED));

        mockMvc.perform(put("/api/v1/admin/cea-claims/1/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CeaClaimRejectRequest("Amount overclaimed"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimStatus").value("REJECTED"));
    }

    @Test
    void verify_serviceThrowsBusinessRuleViolation_returns400() throws Exception {
        // Uses a real jwt() token (not @WithMockUser) so this genuinely exercises the
        // service-exception-to-400 mapping, rather than 400ing earlier on a missing employee_id claim.
        when(ceaClaimService.verifyClaim(eq(1L), eq(2L), any(CeaClaimVerifyRequest.class)))
                .thenThrow(new BusinessRuleViolationException("CEA claim 1 must be SUBMITTED to verify but is VERIFIED"));

        mockMvc.perform(put("/api/v1/admin/cea-claims/1/verify")
                        .with(jwt().jwt(builder -> builder.claim("employee_id", "2"))
                                .authorities(new SimpleGrantedAuthority("ROLE_HR_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CeaClaimVerifyRequest(null))))
                .andExpect(status().isBadRequest());
    }
}
