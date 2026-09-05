package in.gov.jci.hrms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.jci.hrms.dto.LegacyMigrationStatusResponse;
import in.gov.jci.hrms.dto.SalaryHistoryEntryResponse;
import in.gov.jci.hrms.dto.StageLeaveBalancesRequest;
import in.gov.jci.hrms.dto.StagingAcknowledgmentResponse;
import in.gov.jci.hrms.dto.StagingLeaveBalanceRequest;
import in.gov.jci.hrms.dto.ValidateAndPromoteRequest;
import in.gov.jci.hrms.security.EmployeeSecurity;
import in.gov.jci.hrms.security.SecurityConfig;
import in.gov.jci.hrms.service.LegacyMigrationService;
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
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LegacyMigrationController.class)
@Import({SecurityConfig.class, EmployeeSecurity.class})
@WithMockUser(roles = "HR_ADMIN")
class LegacyMigrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LegacyMigrationService legacyMigrationService;

    @Test
    void stageLeaveBalances_returns200WithStagedCount() throws Exception {
        StageLeaveBalancesRequest request = new StageLeaveBalancesRequest(
                List.of(new StagingLeaveBalanceRequest("EMP-001", "EL", new BigDecimal("12.0"), LocalDate.of(2026, 1, 1))));
        when(legacyMigrationService.stageLeaveBalances(any())).thenReturn(new StagingAcknowledgmentResponse(1));

        mockMvc.perform(post("/api/legacy-migration/stage/leave-balances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void validateAndPromote_returns200WithStatus() throws Exception {
        when(legacyMigrationService.validateAndPromote(any())).thenReturn(new LegacyMigrationStatusResponse(List.of()));

        mockMvc.perform(post("/api/legacy-migration/validate-and-promote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ValidateAndPromoteRequest(null, "hr.admin"))))
                .andExpect(status().isOk());
    }

    @Test
    void status_returns200() throws Exception {
        when(legacyMigrationService.getStatus()).thenReturn(new LegacyMigrationStatusResponse(List.of()));

        mockMvc.perform(get("/api/legacy-migration/status"))
                .andExpect(status().isOk());
    }

    @Test
    @WithAnonymousUser
    void status_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get("/api/legacy-migration/status"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "FINANCE_ADMIN")
    void status_withWrongRole_returns403() throws Exception {
        mockMvc.perform(get("/api/legacy-migration/status"))
                .andExpect(status().isForbidden());
    }

    @Test
    void salaryHistory_asSelf_returns200() throws Exception {
        when(legacyMigrationService.getSalaryHistory(5L)).thenReturn(List.<SalaryHistoryEntryResponse>of());

        mockMvc.perform(get("/api/legacy-migration/salary-history/5")
                        .with(jwt().jwt(builder -> builder.claim("employee_id", "5"))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isOk());
    }

    @Test
    void salaryHistory_asDifferentEmployee_returns403() throws Exception {
        mockMvc.perform(get("/api/legacy-migration/salary-history/5")
                        .with(jwt().jwt(builder -> builder.claim("employee_id", "6"))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isForbidden());
    }
}
