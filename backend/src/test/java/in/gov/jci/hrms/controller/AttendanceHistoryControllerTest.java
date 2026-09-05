package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.DailyAttendanceSummaryResponse;
import in.gov.jci.hrms.security.SecurityConfig;
import in.gov.jci.hrms.service.MobilePunchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AttendanceHistoryController.class)
@Import(SecurityConfig.class)
class AttendanceHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MobilePunchService mobilePunchService;

    @Test
    void myHistory_resolvesEmployeeIdFromJwtClaim_returns200() throws Exception {
        when(mobilePunchService.getMyHistory(7L, 2026, 8)).thenReturn(List.of(
                new DailyAttendanceSummaryResponse(
                        LocalDate.of(2026, 8, 24), "09:30:00", "18:00:00", "8.50 Hrs (8h 30m)", "PRESENT")));

        mockMvc.perform(get("/api/attendance/my-history").param("year", "2026").param("month", "8")
                        .with(jwt().jwt(builder -> builder.claim("employee_id", "7"))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].date").value("2026-08-24"))
                .andExpect(jsonPath("$[0].serviceHours").value("8.50 Hrs (8h 30m)"))
                .andExpect(jsonPath("$[0].status").value("PRESENT"));
    }

    @Test
    void myHistory_withoutEmployeeIdClaim_returns400() throws Exception {
        mockMvc.perform(get("/api/attendance/my-history").param("year", "2026").param("month", "8")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithAnonymousUser
    void myHistory_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get("/api/attendance/my-history").param("year", "2026").param("month", "8"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "FINANCE_ADMIN")
    void myHistory_anyAuthenticatedRole_isAllowed() throws Exception {
        // isAuthenticated() only - no role restriction - but @WithMockUser has no employee_id claim, so this still 400s.
        mockMvc.perform(get("/api/attendance/my-history").param("year", "2026").param("month", "8"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void myHistory_missingQueryParams_returns400() throws Exception {
        mockMvc.perform(get("/api/attendance/my-history")
                        .with(jwt().jwt(builder -> builder.claim("employee_id", "7"))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isBadRequest());
    }
}
