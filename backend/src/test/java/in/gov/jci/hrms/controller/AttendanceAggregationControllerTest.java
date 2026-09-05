package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.DailyAttendanceDetailResponse;
import in.gov.jci.hrms.entity.AttendanceDetailStatus;
import in.gov.jci.hrms.security.AttendanceAggregationSecurity;
import in.gov.jci.hrms.security.SecurityConfig;
import in.gov.jci.hrms.service.AttendanceAggregationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AttendanceAggregationController.class)
@Import({SecurityConfig.class, AttendanceAggregationSecurity.class})
class AttendanceAggregationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AttendanceAggregationService attendanceAggregationService;

    private DailyAttendanceDetailResponse sampleResponse() {
        return new DailyAttendanceDetailResponse(LocalDate.of(2026, 8, 20), "09:30:00", "18:20:00",
                new BigDecimal("8.83"), AttendanceDetailStatus.PRESENT, null, null, null, null, null, null);
    }

    @Test
    void evaluateMonth_selfWithoutEmployeeIdParam_resolvesFromJwtClaim_returns200() throws Exception {
        when(attendanceAggregationService.evaluateMonth(eq(7L), eq(2026), eq(8))).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(post("/api/attendance/aggregation/evaluate").param("year", "2026").param("month", "8")
                        .with(jwt().jwt(builder -> builder.claim("employee_id", "7"))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].detailStatus").value("PRESENT"));
    }

    @Test
    void evaluateMonth_employeeTargetingSomeoneElse_returns403() throws Exception {
        mockMvc.perform(post("/api/attendance/aggregation/evaluate")
                        .param("employeeId", "99").param("year", "2026").param("month", "8")
                        .with(jwt().jwt(builder -> builder.claim("employee_id", "7"))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void evaluateMonth_hrAdminTargetingSomeoneElse_returns200() throws Exception {
        when(attendanceAggregationService.evaluateMonth(eq(99L), eq(2026), eq(8))).thenReturn(List.of());

        mockMvc.perform(post("/api/attendance/aggregation/evaluate")
                        .param("employeeId", "99").param("year", "2026").param("month", "8")
                        .with(jwt().jwt(builder -> builder.claim("employee_id", "7"))
                                .authorities(new SimpleGrantedAuthority("ROLE_HR_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    @WithAnonymousUser
    void evaluateMonth_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(post("/api/attendance/aggregation/evaluate").param("year", "2026").param("month", "8"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void evaluateDay_selfWithoutEmployeeIdParam_returns200() throws Exception {
        when(attendanceAggregationService.evaluateDay(eq(7L), eq(LocalDate.of(2026, 8, 20)))).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/attendance/aggregation/evaluate-day").param("date", "2026-08-20")
                        .with(jwt().jwt(builder -> builder.claim("employee_id", "7"))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detailStatus").value("PRESENT"));
    }
}
