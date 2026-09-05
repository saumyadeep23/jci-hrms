package in.gov.jci.hrms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.jci.hrms.dto.DisciplinaryCaseRequest;
import in.gov.jci.hrms.dto.DisciplinaryCaseResponse;
import in.gov.jci.hrms.dto.DisciplinaryStageUpdateRequest;
import in.gov.jci.hrms.entity.DisciplinaryCaseStatus;
import in.gov.jci.hrms.entity.DisciplinaryCaseType;
import in.gov.jci.hrms.entity.PenaltyType;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.security.SecurityConfig;
import in.gov.jci.hrms.service.DisciplinaryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DisciplinaryController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "HR_ADMIN")
class DisciplinaryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DisciplinaryService disciplinaryService;

    private DisciplinaryCaseResponse responseWithStatus(DisciplinaryCaseStatus status) {
        Instant now = Instant.now();
        return new DisciplinaryCaseResponse(1L, "DC-2026-001", 5L, "EMP-005", DisciplinaryCaseType.CONDUCT_RULES,
                status, null, null, null, null, null, null, null, now, now);
    }

    @Test
    void createCase_withValidRequest_returns201WithInitiatedStatus() throws Exception {
        DisciplinaryCaseRequest request = new DisciplinaryCaseRequest("DC-2026-001", 5L, DisciplinaryCaseType.CONDUCT_RULES);
        when(disciplinaryService.createCase(any(DisciplinaryCaseRequest.class)))
                .thenReturn(responseWithStatus(DisciplinaryCaseStatus.INITIATED));

        mockMvc.perform(post("/api/disciplinary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/disciplinary/1"))
                .andExpect(jsonPath("$.status").value("INITIATED"));
    }

    @Test
    void createCase_withMissingRequiredFields_returns400() throws Exception {
        DisciplinaryCaseRequest invalid = new DisciplinaryCaseRequest("", null, null);

        mockMvc.perform(post("/api/disciplinary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createCase_whenEmployeeMissing_returns404() throws Exception {
        DisciplinaryCaseRequest request = new DisciplinaryCaseRequest("DC-2026-001", 5L, DisciplinaryCaseType.CONDUCT_RULES);
        when(disciplinaryService.createCase(any(DisciplinaryCaseRequest.class))).thenThrow(new EmployeeNotFoundException(5L));

        mockMvc.perform(post("/api/disciplinary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getById_returns200() throws Exception {
        when(disciplinaryService.getById(1L)).thenReturn(responseWithStatus(DisciplinaryCaseStatus.INITIATED));

        mockMvc.perform(get("/api/disciplinary/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseNumber").value("DC-2026-001"));
    }

    @Test
    void list_returns200WithPagedContent() throws Exception {
        Pageable pageable = PageRequest.of(0, 20);
        Page<DisciplinaryCaseResponse> page = new PageImpl<>(
                List.of(responseWithStatus(DisciplinaryCaseStatus.INITIATED)), pageable, 1);
        when(disciplinaryService.list(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/disciplinary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].caseNumber").value("DC-2026-001"));
    }

    @Test
    void listByEmployee_returns200() throws Exception {
        when(disciplinaryService.listByEmployee(5L)).thenReturn(List.of(responseWithStatus(DisciplinaryCaseStatus.INITIATED)));

        mockMvc.perform(get("/api/disciplinary/employee/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].employeeId").value(5));
    }

    @Test
    void updateStage_toChargeSheetIssued_returns200() throws Exception {
        DisciplinaryStageUpdateRequest request = new DisciplinaryStageUpdateRequest(
                DisciplinaryCaseStatus.CHARGE_SHEET_ISSUED, LocalDate.of(2026, 2, 1), null, null, null, null, null);
        when(disciplinaryService.updateStage(eq(1L), any(DisciplinaryStageUpdateRequest.class)))
                .thenReturn(responseWithStatus(DisciplinaryCaseStatus.CHARGE_SHEET_ISSUED));

        mockMvc.perform(put("/api/disciplinary/1/stage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHARGE_SHEET_ISSUED"));
    }

    @Test
    void updateStage_invalidTransition_returns400() throws Exception {
        DisciplinaryStageUpdateRequest request = new DisciplinaryStageUpdateRequest(
                DisciplinaryCaseStatus.PENALTY_IMPOSED, null, null, PenaltyType.CENSURE, LocalDate.of(2026, 3, 1), null, null);
        when(disciplinaryService.updateStage(eq(1L), any(DisciplinaryStageUpdateRequest.class)))
                .thenThrow(new BusinessRuleViolationException("Disciplinary Case 1 cannot move from INITIATED to PENALTY_IMPOSED"));

        mockMvc.perform(put("/api/disciplinary/1/stage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithAnonymousUser
    void createCase_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(post("/api/disciplinary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DisciplinaryCaseRequest("DC-2026-001", 5L, DisciplinaryCaseType.CONDUCT_RULES))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void createCase_withWrongRole_returns403() throws Exception {
        mockMvc.perform(post("/api/disciplinary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DisciplinaryCaseRequest("DC-2026-001", 5L, DisciplinaryCaseType.CONDUCT_RULES))))
                .andExpect(status().isForbidden());
    }
}
