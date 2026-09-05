package in.gov.jci.hrms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.jci.hrms.dto.TadaClaimRequest;
import in.gov.jci.hrms.dto.TadaClaimResponse;
import in.gov.jci.hrms.dto.TadaClaimVerifyRequest;
import in.gov.jci.hrms.entity.CityClass;
import in.gov.jci.hrms.entity.ReimbursementClaimStatus;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.security.SecurityConfig;
import in.gov.jci.hrms.service.TadaClaimService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TadaClaimController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "HR_ADMIN")
class TadaClaimControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TadaClaimService tadaClaimService;

    private TadaClaimResponse responseWithStatus(ReimbursementClaimStatus status) {
        return new TadaClaimResponse(1L, 2L, "TC-2026-001", 1L, "EMP-001",
                new BigDecimal("5000.00"), null, status, null, null, LocalDate.now(), Instant.now(), Instant.now());
    }

    @Test
    void create_withValidRequest_returns201WithDraftStatus() throws Exception {
        TadaClaimRequest request = new TadaClaimRequest(2L, 1L, "TC-2026-001", new BigDecimal("5000.00"));
        when(tadaClaimService.create(any(TadaClaimRequest.class))).thenReturn(responseWithStatus(ReimbursementClaimStatus.DRAFT));

        mockMvc.perform(post("/api/reimbursements/tada")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/reimbursements/tada/1"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void create_withMissingRequiredFields_returns400() throws Exception {
        TadaClaimRequest invalid = new TadaClaimRequest(null, null, "", null);

        mockMvc.perform(post("/api/reimbursements/tada")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_whenTourRequestMissing_returns404() throws Exception {
        TadaClaimRequest request = new TadaClaimRequest(2L, 1L, "TC-2026-001", new BigDecimal("5000.00"));
        when(tadaClaimService.create(any(TadaClaimRequest.class))).thenThrow(new MasterDataNotFoundException("Tour Request", 2L));

        mockMvc.perform(post("/api/reimbursements/tada")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void submit_returns200WithSubmittedStatus() throws Exception {
        when(tadaClaimService.submit(1L)).thenReturn(responseWithStatus(ReimbursementClaimStatus.SUBMITTED));

        mockMvc.perform(post("/api/reimbursements/tada/1/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }

    @Test
    void verifyByHr_withinCeiling_returns200() throws Exception {
        TadaClaimVerifyRequest request = new TadaClaimVerifyRequest("hr.verifier", new BigDecimal("4000.00"),
                new BigDecimal("7"), CityClass.X);
        when(tadaClaimService.verifyByHr(eq(1L), any(TadaClaimVerifyRequest.class)))
                .thenReturn(responseWithStatus(ReimbursementClaimStatus.VERIFIED_BY_HR));

        mockMvc.perform(post("/api/reimbursements/tada/1/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED_BY_HR"));
    }

    @Test
    void verifyByHr_exceedingCeiling_returns400() throws Exception {
        TadaClaimVerifyRequest request = new TadaClaimVerifyRequest("hr.verifier", new BigDecimal("9000.00"),
                new BigDecimal("7"), CityClass.X);
        when(tadaClaimService.verifyByHr(eq(1L), any(TadaClaimVerifyRequest.class)))
                .thenThrow(new BusinessRuleViolationException("TADA Claim 1 allowed amount exceeds the TADA ceiling of 4050.00"));

        mockMvc.perform(post("/api/reimbursements/tada/1/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "FINANCE_ADMIN")
    void approveByFinance_returns200WithApprovedStatus() throws Exception {
        when(tadaClaimService.approveByFinance(eq(1L), eq("finance.officer")))
                .thenReturn(responseWithStatus(ReimbursementClaimStatus.APPROVED_BY_FINANCE));

        mockMvc.perform(post("/api/reimbursements/tada/1/approve").param("approvedBy", "finance.officer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED_BY_FINANCE"));
    }

    @Test
    void reject_returns200WithRejectedStatus() throws Exception {
        when(tadaClaimService.reject(1L)).thenReturn(responseWithStatus(ReimbursementClaimStatus.REJECTED));

        mockMvc.perform(post("/api/reimbursements/tada/1/reject"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    @WithAnonymousUser
    void create_withoutAuthentication_returns401() throws Exception {
        TadaClaimRequest request = new TadaClaimRequest(2L, 1L, "TC-2026-001", new BigDecimal("5000.00"));

        mockMvc.perform(post("/api/reimbursements/tada")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void approveByFinance_withWrongRole_returns403() throws Exception {
        mockMvc.perform(post("/api/reimbursements/tada/1/approve").param("approvedBy", "finance.officer"))
                .andExpect(status().isForbidden());
    }
}
