package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.LeaveLedgerEntryResponse;
import in.gov.jci.hrms.entity.LeaveLedgerSource;
import in.gov.jci.hrms.security.AttendanceAggregationSecurity;
import in.gov.jci.hrms.security.SecurityConfig;
import in.gov.jci.hrms.service.LeaveLedgerEntryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LeaveLedgerEntryController.class)
@Import({SecurityConfig.class, AttendanceAggregationSecurity.class})
class LeaveLedgerEntryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LeaveLedgerEntryService leaveLedgerEntryService;

    private LeaveLedgerEntryResponse sampleEntry() {
        return new LeaveLedgerEntryResponse(1L, 7L, "EMP1001", 10L, "CL", LocalDate.of(2026, 8, 20),
                new BigDecimal("-0.5"), "Auto-debited 0.5 CL for unauthorized late attendance / short hours on 20-08-2026",
                LeaveLedgerSource.AUTO_LATE_DEDUCTION, 500L, null, Instant.now());
    }

    @Test
    void list_selfWithoutEmployeeIdParam_resolvesFromJwtClaim_returns200() throws Exception {
        when(leaveLedgerEntryService.listForEmployee(eq(7L))).thenReturn(List.of(sampleEntry()));

        mockMvc.perform(get("/api/leave-ledger-entries")
                        .with(jwt().jwt(builder -> builder.claim("employee_id", "7"))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].leaveTypeCode").value("CL"));
    }

    @Test
    void list_employeeTargetingSomeoneElse_returns403() throws Exception {
        mockMvc.perform(get("/api/leave-ledger-entries").param("employeeId", "99")
                        .with(jwt().jwt(builder -> builder.claim("employee_id", "7"))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_hrAdminTargetingSomeoneElse_returns200() throws Exception {
        when(leaveLedgerEntryService.listForEmployee(eq(99L))).thenReturn(List.of());

        mockMvc.perform(get("/api/leave-ledger-entries").param("employeeId", "99")
                        .with(jwt().jwt(builder -> builder.claim("employee_id", "7"))
                                .authorities(new SimpleGrantedAuthority("ROLE_HR_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    @WithAnonymousUser
    void list_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get("/api/leave-ledger-entries"))
                .andExpect(status().isUnauthorized());
    }
}
