package in.gov.jci.hrms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.jci.hrms.dto.AparCycleRequest;
import in.gov.jci.hrms.dto.AparCycleResponse;
import in.gov.jci.hrms.entity.AparCycleStatus;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.security.SecurityConfig;
import in.gov.jci.hrms.service.AparCycleService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AparCycleController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "HR_ADMIN")
class AparCycleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AparCycleService aparCycleService;

    private AparCycleResponse responseWithStatus(AparCycleStatus status) {
        Instant now = Instant.now();
        return new AparCycleResponse(1L, "2025-2026", LocalDate.of(2025, 4, 1), LocalDate.of(2026, 3, 31), status, now, now);
    }

    @Test
    void create_withValidRequest_returns201WithInitiatedStatus() throws Exception {
        AparCycleRequest request = new AparCycleRequest("2025-2026", LocalDate.of(2025, 4, 1), LocalDate.of(2026, 3, 31));
        when(aparCycleService.create(any(AparCycleRequest.class))).thenReturn(responseWithStatus(AparCycleStatus.INITIATED));

        mockMvc.perform(post("/api/apar/cycles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/apar/cycles/1"))
                .andExpect(jsonPath("$.status").value("INITIATED"));
    }

    @Test
    void create_whenCycleYearAlreadyExists_returns409() throws Exception {
        AparCycleRequest request = new AparCycleRequest("2025-2026", LocalDate.of(2025, 4, 1), LocalDate.of(2026, 3, 31));
        when(aparCycleService.create(any(AparCycleRequest.class)))
                .thenThrow(new MasterDataConflictException("APAR Cycle already exists for 2025-2026"));

        mockMvc.perform(post("/api/apar/cycles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void advance_returns200WithNextStatus() throws Exception {
        when(aparCycleService.advance(1L)).thenReturn(responseWithStatus(AparCycleStatus.SELF_APPRAISAL));

        mockMvc.perform(post("/api/apar/cycles/1/advance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SELF_APPRAISAL"));
    }

    @Test
    void advance_whenAlreadyClosed_returns400() throws Exception {
        when(aparCycleService.advance(1L))
                .thenThrow(new BusinessRuleViolationException("APAR Cycle 1 is already CLOSED"));

        mockMvc.perform(post("/api/apar/cycles/1/advance"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithAnonymousUser
    void advance_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(post("/api/apar/cycles/1/advance"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void advance_withWrongRole_returns403() throws Exception {
        mockMvc.perform(post("/api/apar/cycles/1/advance"))
                .andExpect(status().isForbidden());
    }
}
