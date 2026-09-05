package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.AuditLogResponse;
import in.gov.jci.hrms.entity.AuditAction;
import in.gov.jci.hrms.security.SecurityConfig;
import in.gov.jci.hrms.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuditLogController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "HR_ADMIN")
class AuditLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuditLogService auditLogService;

    @Test
    void getAuditLogs_returns200WithPagedContent() throws Exception {
        AuditLogResponse response = new AuditLogResponse(1L, "Employee", 5L, AuditAction.UPDATE,
                "hr.admin", "127.0.0.1", "{}", "{}", Instant.now());
        Pageable pageable = PageRequest.of(0, 20);
        when(auditLogService.getAuditLogs(eq("Employee"), eq(5L), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(response), pageable, 1));

        mockMvc.perform(get("/api/audit-logs").param("entityName", "Employee").param("entityId", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].entityName").value("Employee"));
    }

    @Test
    void getAuditLogs_withDateRange_passesThroughToService() throws Exception {
        when(auditLogService.getAuditLogs(isNull(), isNull(), eq("hr.admin"), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/audit-logs")
                        .param("performedBy", "hr.admin")
                        .param("fromDate", "2026-01-01")
                        .param("toDate", "2026-01-31"))
                .andExpect(status().isOk());
    }

    @Test
    @WithAnonymousUser
    void getAuditLogs_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get("/api/audit-logs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getAuditLogs_withWrongRole_returns403() throws Exception {
        mockMvc.perform(get("/api/audit-logs"))
                .andExpect(status().isForbidden());
    }
}
