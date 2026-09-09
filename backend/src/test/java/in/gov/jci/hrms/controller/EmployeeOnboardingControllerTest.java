package in.gov.jci.hrms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.jci.hrms.dto.OnboardingDraftResponse;
import in.gov.jci.hrms.dto.OnboardingDraftUpsertRequest;
import in.gov.jci.hrms.dto.OnboardingSubmitResponse;
import in.gov.jci.hrms.dto.OnboardingPersonalDetailsRequest;
import in.gov.jci.hrms.entity.Gender;
import in.gov.jci.hrms.entity.MaritalStatus;
import in.gov.jci.hrms.entity.OnboardingStatus;
import in.gov.jci.hrms.entity.Salutation;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.OnboardingDraftNotFoundException;
import in.gov.jci.hrms.security.SecurityConfig;
import in.gov.jci.hrms.service.EmployeeOnboardingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EmployeeOnboardingController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "HR_ADMIN")
class EmployeeOnboardingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EmployeeOnboardingService onboardingService;

    private OnboardingDraftResponse draftResponse(Long id) {
        return new OnboardingDraftResponse(id, "OB-2026-000001", "0001", 1, OnboardingStatus.IN_PROGRESS, 0,
                null, null, null, null, null, List.of(), List.of(), null, null, List.of(), null,
                null, Instant.now(), Instant.now(), null);
    }

    private OnboardingPersonalDetailsRequest personal() {
        return new OnboardingPersonalDetailsRequest(
                Salutation.MS, "Asha", null, "Rao", Gender.FEMALE, LocalDate.of(1990, 5, 1), MaritalStatus.SINGLE, null,
                "Indian", null, "ABCDE1234F", "CPF00001", null, "123456789012", "asha.rao@example.com", null, "9876543210", null);
    }

    @Test
    void saveDraft_withNoDraftId_returns201WithLocationHeader() throws Exception {
        OnboardingDraftUpsertRequest request = new OnboardingDraftUpsertRequest(
                null, 1, personal(), null, null, null, null, null, null, null, null, null, null);
        when(onboardingService.upsert(any(OnboardingDraftUpsertRequest.class), any())).thenReturn(draftResponse(1L));

        mockMvc.perform(post("/api/v1/onboarding/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/onboarding/drafts/1"))
                .andExpect(jsonPath("$.draftCode").value("OB-2026-000001"));
    }

    @Test
    void saveDraft_viaUnversionedPluralAlias_returns201() throws Exception {
        // Two PIMS_SPEC.md revisions documented this endpoint differently:
        // POST /api/v1/onboarding/draft (singular) and POST /api/onboarding/drafts
        // (plural, unversioned) - both must work.
        OnboardingDraftUpsertRequest request = new OnboardingDraftUpsertRequest(
                null, 1, personal(), null, null, null, null, null, null, null, null, null, null);
        when(onboardingService.upsert(any(OnboardingDraftUpsertRequest.class), any())).thenReturn(draftResponse(1L));

        mockMvc.perform(post("/api/onboarding/drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.draftCode").value("OB-2026-000001"));
    }

    @Test
    void getById_viaUnversionedAlias_returns200() throws Exception {
        when(onboardingService.getById(1L)).thenReturn(draftResponse(1L));

        mockMvc.perform(get("/api/onboarding/drafts/1"))
                .andExpect(status().isOk());
    }

    @Test
    void saveDraft_withDraftId_returns200() throws Exception {
        OnboardingDraftUpsertRequest request = new OnboardingDraftUpsertRequest(
                1L, 2, null, null, null, null, null, null, null, null, null, null, null);
        when(onboardingService.upsert(any(OnboardingDraftUpsertRequest.class), any())).thenReturn(draftResponse(1L));

        mockMvc.perform(post("/api/v1/onboarding/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void getById_whenMissing_returns404() throws Exception {
        when(onboardingService.getById(99L)).thenThrow(new OnboardingDraftNotFoundException(99L));

        mockMvc.perform(get("/api/v1/onboarding/drafts/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void finalizeOnboarding_returnsSubmissionResult() throws Exception {
        when(onboardingService.finalizeOnboarding(1L)).thenReturn(new OnboardingSubmitResponse(1L, "OB-2026-000001", 100L, "0001"));

        mockMvc.perform(post("/api/v1/onboarding/drafts/1/finalize"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(100))
                .andExpect(jsonPath("$.employeeCode").value("0001"));
    }

    @Test
    void finalizeOnboarding_whenIncomplete_returns400() throws Exception {
        when(onboardingService.finalizeOnboarding(1L))
                .thenThrow(new BusinessRuleViolationException("Cannot finalize draft OB-2026-000001: personal is required"));

        mockMvc.perform(post("/api/v1/onboarding/drafts/1/finalize"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    void cancel_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/onboarding/drafts/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithAnonymousUser
    void getById_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/onboarding/drafts/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void saveDraft_withWrongRole_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/onboarding/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new OnboardingDraftUpsertRequest(null, null, null, null, null, null, null, null, null, null, null, null, null))))
                .andExpect(status().isForbidden());
    }
}
