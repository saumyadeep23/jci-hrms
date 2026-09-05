package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.LeaveBalanceResponse;
import in.gov.jci.hrms.security.SecurityConfig;
import in.gov.jci.hrms.service.LeaveBalanceService;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LeaveBalanceController.class)
@Import(SecurityConfig.class)
class LeaveBalanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LeaveBalanceService leaveBalanceService;

    @Test
    void mine_resolvesEmployeeIdFromJwtClaim_returns200() throws Exception {
        when(leaveBalanceService.listForEmployee(eq(7L), eq(2026))).thenReturn(List.of(
                new LeaveBalanceResponse(1L, 10L, "CL", "Casual Leave", 2026,
                        BigDecimal.valueOf(8), BigDecimal.valueOf(2), BigDecimal.ZERO, BigDecimal.valueOf(6), Instant.now())));

        mockMvc.perform(get("/api/leave-balances/mine").param("year", "2026")
                        .with(jwt().jwt(builder -> builder.claim("employee_id", "7"))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].leaveTypeCode").value("CL"))
                .andExpect(jsonPath("$[0].availableDays").value(6));
    }

    @Test
    void mine_withoutEmployeeIdClaim_returns400() throws Exception {
        mockMvc.perform(get("/api/leave-balances/mine").param("year", "2026")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithAnonymousUser
    void mine_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get("/api/leave-balances/mine").param("year", "2026"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mine_missingYearParam_returns400() throws Exception {
        mockMvc.perform(get("/api/leave-balances/mine")
                        .with(jwt().jwt(builder -> builder.claim("employee_id", "7"))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isBadRequest());
    }
}
