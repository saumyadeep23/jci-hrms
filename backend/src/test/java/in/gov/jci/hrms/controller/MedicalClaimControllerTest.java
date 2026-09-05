package in.gov.jci.hrms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.jci.hrms.dto.MedicalClaimItemRequest;
import in.gov.jci.hrms.dto.MedicalClaimRequest;
import in.gov.jci.hrms.dto.MedicalClaimResponse;
import in.gov.jci.hrms.dto.MedicalClaimVerifyRequest;
import in.gov.jci.hrms.entity.ExpenseType;
import in.gov.jci.hrms.entity.ReimbursementClaimStatus;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.security.SecurityConfig;
import in.gov.jci.hrms.service.MedicalClaimService;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MedicalClaimController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "HR_ADMIN")
class MedicalClaimControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MedicalClaimService medicalClaimService;

    private MedicalClaimRequest validRequest() {
        return new MedicalClaimRequest("MC-2026-001", 1L, null, false, List.of(
                new MedicalClaimItemRequest(ExpenseType.CONSULTATION, new BigDecimal("500.00"), "BILL-1", LocalDate.of(2026, 1, 5), null)
        ));
    }

    private MedicalClaimResponse responseWithStatus(ReimbursementClaimStatus status) {
        Instant now = Instant.now();
        return new MedicalClaimResponse(1L, "MC-2026-001", 1L, "EMP-001", null, null,
                new BigDecimal("500.00"), null, status, null, null, null, now, now, List.of());
    }

    @Test
    void create_withValidRequest_returns201WithDraftStatus() throws Exception {
        MedicalClaimRequest request = validRequest();
        when(medicalClaimService.create(any(MedicalClaimRequest.class))).thenReturn(responseWithStatus(ReimbursementClaimStatus.DRAFT));

        mockMvc.perform(post("/api/reimbursements/medical")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/reimbursements/medical/1"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void create_withNoItems_returns400() throws Exception {
        MedicalClaimRequest invalid = new MedicalClaimRequest("MC-2026-002", 1L, null, false, List.of());

        mockMvc.perform(post("/api/reimbursements/medical")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_whenEmployeeMissing_returns404() throws Exception {
        MedicalClaimRequest request = validRequest();
        when(medicalClaimService.create(any(MedicalClaimRequest.class))).thenThrow(new EmployeeNotFoundException(1L));

        mockMvc.perform(post("/api/reimbursements/medical")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_whenIneligible_returns400() throws Exception {
        MedicalClaimRequest request = validRequest();
        when(medicalClaimService.create(any(MedicalClaimRequest.class)))
                .thenThrow(new BusinessRuleViolationException("Employee 1 is not eligible for medical reimbursement"));

        mockMvc.perform(post("/api/reimbursements/medical")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submit_returns200WithSubmittedStatus() throws Exception {
        when(medicalClaimService.submit(1L)).thenReturn(responseWithStatus(ReimbursementClaimStatus.SUBMITTED));

        mockMvc.perform(post("/api/reimbursements/medical/1/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }

    @Test
    void verifyByHr_returns200WithVerifiedStatus() throws Exception {
        MedicalClaimVerifyRequest request = new MedicalClaimVerifyRequest("hr.verifier", List.of(
                new MedicalClaimVerifyRequest.ItemAllowedAmount(100L, new BigDecimal("450.00"))
        ));
        when(medicalClaimService.verifyByHr(org.mockito.ArgumentMatchers.eq(1L), any(MedicalClaimVerifyRequest.class)))
                .thenReturn(responseWithStatus(ReimbursementClaimStatus.VERIFIED_BY_HR));

        mockMvc.perform(post("/api/reimbursements/medical/1/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED_BY_HR"));
    }

    @Test
    @WithMockUser(roles = "FINANCE_ADMIN")
    void approveByFinance_returns200WithApprovedStatus() throws Exception {
        when(medicalClaimService.approveByFinance(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq("finance.officer")))
                .thenReturn(responseWithStatus(ReimbursementClaimStatus.APPROVED_BY_FINANCE));

        mockMvc.perform(post("/api/reimbursements/medical/1/approve").param("approvedBy", "finance.officer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED_BY_FINANCE"));
    }

    @Test
    void reject_returns200WithRejectedStatus() throws Exception {
        when(medicalClaimService.reject(1L)).thenReturn(responseWithStatus(ReimbursementClaimStatus.REJECTED));

        mockMvc.perform(post("/api/reimbursements/medical/1/reject"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    @WithAnonymousUser
    void create_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(post("/api/reimbursements/medical")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void approveByFinance_withWrongRole_returns403() throws Exception {
        mockMvc.perform(post("/api/reimbursements/medical/1/approve").param("approvedBy", "finance.officer"))
                .andExpect(status().isForbidden());
    }
}
