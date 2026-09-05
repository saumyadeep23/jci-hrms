package in.gov.jci.hrms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.jci.hrms.dto.PayrollRunRequest;
import in.gov.jci.hrms.dto.PayrollRunResponse;
import in.gov.jci.hrms.entity.PayrollRunStatus;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.security.SecurityConfig;
import in.gov.jci.hrms.service.PayrollRunService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PayrollRunController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "FINANCE_ADMIN")
class PayrollRunControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PayrollRunService payrollRunService;

    private PayrollRunResponse responseWithStatus(PayrollRunStatus status) {
        return new PayrollRunResponse(1L, 2026, 8, LocalDate.of(2026, 7, 26), LocalDate.of(2026, 8, 25),
                status, status == PayrollRunStatus.FINALIZED ? "hr.admin" : null,
                status == PayrollRunStatus.FINALIZED ? Instant.now() : null,
                in.gov.jci.hrms.entity.PayrollRunType.LIVE, false, Instant.now());
    }

    @Test
    void create_withValidRequest_returns201WithDraftStatus() throws Exception {
        PayrollRunRequest request = new PayrollRunRequest(2026, 8);
        when(payrollRunService.create(any(PayrollRunRequest.class))).thenReturn(responseWithStatus(PayrollRunStatus.DRAFT));

        mockMvc.perform(post("/api/payroll/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/payroll/runs/1"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void create_withMissingRequiredFields_returns400() throws Exception {
        PayrollRunRequest invalid = new PayrollRunRequest(null, null);

        mockMvc.perform(post("/api/payroll/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_withOutOfRangeMonth_returns400() throws Exception {
        PayrollRunRequest invalid = new PayrollRunRequest(2026, 13);

        mockMvc.perform(post("/api/payroll/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_whenCycleAlreadyExists_returns409() throws Exception {
        PayrollRunRequest request = new PayrollRunRequest(2026, 8);
        when(payrollRunService.create(any(PayrollRunRequest.class)))
                .thenThrow(new MasterDataConflictException("Payroll Run already exists for 2026-8"));

        mockMvc.perform(post("/api/payroll/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void compute_whenDraft_returns200WithComputedStatus() throws Exception {
        when(payrollRunService.compute(1L)).thenReturn(responseWithStatus(PayrollRunStatus.COMPUTED));

        mockMvc.perform(post("/api/payroll/runs/1/compute"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPUTED"));
    }

    @Test
    void compute_whenNotDraft_returns400() throws Exception {
        when(payrollRunService.compute(1L))
                .thenThrow(new BusinessRuleViolationException("Payroll Run 1 must be DRAFT but is COMPUTED"));

        mockMvc.perform(post("/api/payroll/runs/1/compute"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void compute_whenRunMissing_returns404() throws Exception {
        when(payrollRunService.compute(99L)).thenThrow(new MasterDataNotFoundException("Payroll Run", 99L));

        mockMvc.perform(post("/api/payroll/runs/99/compute"))
                .andExpect(status().isNotFound());
    }

    @Test
    void finalizeRun_whenComputed_returns200WithFinalizedStatus() throws Exception {
        when(payrollRunService.finalizeRun(eq(1L), eq("hr.admin"))).thenReturn(responseWithStatus(PayrollRunStatus.FINALIZED));

        mockMvc.perform(post("/api/payroll/runs/1/finalize").param("finalizedBy", "hr.admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINALIZED"))
                .andExpect(jsonPath("$.finalizedBy").value("hr.admin"));
    }

    @Test
    void finalizeRun_whenNotComputed_returns400() throws Exception {
        when(payrollRunService.finalizeRun(eq(1L), eq("hr.admin")))
                .thenThrow(new BusinessRuleViolationException("Payroll Run 1 must be COMPUTED but is DRAFT"));

        mockMvc.perform(post("/api/payroll/runs/1/finalize").param("finalizedBy", "hr.admin"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getById_whenMissing_returns404() throws Exception {
        when(payrollRunService.getById(99L)).thenThrow(new MasterDataNotFoundException("Payroll Run", 99L));

        mockMvc.perform(get("/api/payroll/runs/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listPayslips_returns200WithEmptyList() throws Exception {
        when(payrollRunService.listPayslips(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/payroll/runs/1/payslips"))
                .andExpect(status().isOk());
    }

    @Test
    @WithAnonymousUser
    void getById_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get("/api/payroll/runs/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void create_withWrongRole_returns403() throws Exception {
        mockMvc.perform(post("/api/payroll/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PayrollRunRequest(2026, 8))))
                .andExpect(status().isForbidden());
    }
}
