package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.AttendanceBreakdownResponse;
import in.gov.jci.hrms.dto.PayslipSummaryResponse;
import in.gov.jci.hrms.dto.PfBucketSplitResponse;
import in.gov.jci.hrms.security.EmployeeSecurity;
import in.gov.jci.hrms.security.SecurityConfig;
import in.gov.jci.hrms.service.PayrollReportingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportingController.class)
@Import({SecurityConfig.class, EmployeeSecurity.class})
class ReportingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PayrollReportingService payrollReportingService;

    private PayslipSummaryResponse summaryFor(Long employeeId) {
        return new PayslipSummaryResponse(1L, 2026, 8, LocalDate.of(2026, 7, 26), LocalDate.of(2026, 8, 25),
                employeeId, "EMP-005", "Asha Rao", new BigDecimal("40000.00"), new BigDecimal("52000.00"),
                new BigDecimal("4800.00"), new BigDecimal("5000.00"), new BigDecimal("47200.00"), BigDecimal.ZERO,
                new AttendanceBreakdownResponse(31, 29, 0, 0, 2, 0, 0, BigDecimal.ZERO),
                new PfBucketSplitResponse(new BigDecimal("4800.00"), new BigDecimal("3600.00"), new BigDecimal("1400.00")),
                List.of());
    }

    @Test
    @WithMockUser(roles = "FINANCE_ADMIN")
    void bankDisbursementFile_asFinanceAdmin_returnsCsvWithAttachmentHeader() throws Exception {
        when(payrollReportingService.generateBankDisbursementFile(1L))
                .thenReturn("EmployeeCode,EmployeeName,BankName,AccountNumber,IFSC,NetPay\nEMP-005,Asha Rao,SBI,123,IFSC0001,47200.00\n");

        mockMvc.perform(get("/api/reports/payroll/1/bank-file"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(content().contentTypeCompatibleWith("text/csv"));
    }

    @Test
    @WithAnonymousUser
    void bankDisbursementFile_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get("/api/reports/payroll/1/bank-file"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void bankDisbursementFile_withWrongRole_returns403() throws Exception {
        mockMvc.perform(get("/api/reports/payroll/1/bank-file"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "FINANCE_ADMIN")
    void payslipSummary_asFinanceAdmin_returns200() throws Exception {
        when(payrollReportingService.getPayslipSummary(1L, 5L)).thenReturn(summaryFor(5L));

        mockMvc.perform(get("/api/reports/payroll/1/payslip/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeCode").value("EMP-005"));
    }

    @Test
    void payslipSummary_asOwningEmployee_returns200() throws Exception {
        when(payrollReportingService.getPayslipSummary(1L, 5L)).thenReturn(summaryFor(5L));

        mockMvc.perform(get("/api/reports/payroll/1/payslip/5")
                        .with(jwt().jwt(builder -> builder.claim("employee_id", "5"))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isOk());
    }

    @Test
    void payslipSummary_asDifferentEmployee_returns403() throws Exception {
        mockMvc.perform(get("/api/reports/payroll/1/payslip/5")
                        .with(jwt().jwt(builder -> builder.claim("employee_id", "6"))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isForbidden());
    }
}
