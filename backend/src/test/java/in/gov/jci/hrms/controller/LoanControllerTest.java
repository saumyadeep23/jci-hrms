package in.gov.jci.hrms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.jci.hrms.dto.EmployeeLoanRequest;
import in.gov.jci.hrms.dto.EmployeeLoanResponse;
import in.gov.jci.hrms.dto.LoanForecloseRequest;
import in.gov.jci.hrms.dto.LoanRepaymentRequest;
import in.gov.jci.hrms.dto.LoanRepaymentResponse;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeLoan;
import in.gov.jci.hrms.entity.LoanStatus;
import in.gov.jci.hrms.entity.LoanType;
import in.gov.jci.hrms.entity.LoanTypeCode;
import in.gov.jci.hrms.entity.RepaymentSource;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.repository.EmployeeLoanRepository;
import in.gov.jci.hrms.security.LoanSecurity;
import in.gov.jci.hrms.security.SecurityConfig;
import in.gov.jci.hrms.service.LoanRepaymentService;
import in.gov.jci.hrms.service.LoanService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LoanController.class)
@Import({SecurityConfig.class, LoanSecurity.class})
@WithMockUser(roles = "FINANCE_ADMIN")
class LoanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LoanService loanService;

    @MockBean
    private LoanRepaymentService loanRepaymentService;

    @MockBean
    private EmployeeLoanRepository employeeLoanRepository;

    private EmployeeLoanRequest validRequest() {
        return new EmployeeLoanRequest(1L, 2L, "LN-2026-001", new BigDecimal("12000.00"), 12, LocalDate.of(2026, 1, 1));
    }

    private EmployeeLoanResponse responseWithStatus(LoanStatus status) {
        Instant now = Instant.now();
        return new EmployeeLoanResponse(1L, 1L, "CPF Secured Loan", 2L, "EMP-001", "LN-2026-001",
                new BigDecimal("12000.00"), new BigDecimal("12.00"), 12, 12, new BigDecimal("12000.00"),
                status, LocalDate.of(2026, 1, 1), now, now);
    }

    @Test
    void sanction_withValidRequest_returns201WithSanctionedStatus() throws Exception {
        EmployeeLoanRequest request = validRequest();
        when(loanService.sanction(any(EmployeeLoanRequest.class))).thenReturn(responseWithStatus(LoanStatus.SANCTIONED));

        mockMvc.perform(post("/api/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/loans/1"))
                .andExpect(jsonPath("$.status").value("SANCTIONED"));
    }

    @Test
    void sanction_withMissingRequiredFields_returns400() throws Exception {
        EmployeeLoanRequest invalid = new EmployeeLoanRequest(null, null, "", null, null, null);

        mockMvc.perform(post("/api/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sanction_whenAccountNumberInUse_returns409() throws Exception {
        EmployeeLoanRequest request = validRequest();
        when(loanService.sanction(any(EmployeeLoanRequest.class)))
                .thenThrow(new MasterDataConflictException("Employee Loan account number already in use: LN-2026-001"));

        mockMvc.perform(post("/api/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void disburse_returns200WithDisbursedStatus() throws Exception {
        when(loanService.disburse(1L)).thenReturn(responseWithStatus(LoanStatus.DISBURSED));

        mockMvc.perform(post("/api/loans/1/disburse"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISBURSED"));
    }

    // Non-financial RBAC migration pass (docs/security/RBAC_MIGRATION_REPORT.md): this module was
    // missed by the earlier SEC-003/004 CPF/JCIECCS closure - disburse/foreclose are checker-shaped
    // financial actions, SUPER_ADMIN alone must not be able to reach them.
    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void disburse_withOnlySuperAdminRole_returns403() throws Exception {
        mockMvc.perform(post("/api/loans/1/disburse"))
                .andExpect(status().isForbidden());
    }

    @Test
    void foreclose_returns200() throws Exception {
        LoanForecloseRequest request = new LoanForecloseRequest(LocalDate.of(2026, 6, 1), "REF-1");
        LoanRepaymentResponse repaymentResponse = new LoanRepaymentResponse(1L, 1L, RepaymentSource.FORECLOSURE,
                new BigDecimal("6060.00"), new BigDecimal("6000.00"), new BigDecimal("60.00"),
                LocalDate.of(2026, 6, 1), null, "REF-1", Instant.now());
        when(loanService.foreclose(eq(1L), any(LoanForecloseRequest.class))).thenReturn(repaymentResponse);

        mockMvc.perform(post("/api/loans/1/foreclose")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(6060.00));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void foreclose_withOnlySuperAdminRole_returns403() throws Exception {
        LoanForecloseRequest request = new LoanForecloseRequest(LocalDate.of(2026, 6, 1), "REF-1");

        mockMvc.perform(post("/api/loans/1/foreclose")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void foreclose_whenAlreadyClosed_returns400() throws Exception {
        LoanForecloseRequest request = new LoanForecloseRequest(LocalDate.of(2026, 6, 1), "REF-1");
        when(loanService.foreclose(eq(1L), any(LoanForecloseRequest.class)))
                .thenThrow(new BusinessRuleViolationException("Employee Loan 1 cannot be foreclosed from status CLOSED"));

        mockMvc.perform(post("/api/loans/1/foreclose")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void recordRepayment_returns201() throws Exception {
        LoanRepaymentRequest request = new LoanRepaymentRequest(RepaymentSource.CASH_DEPOSIT, new BigDecimal("1120.00"),
                LocalDate.of(2026, 2, 1), null, null);
        LoanRepaymentResponse response = new LoanRepaymentResponse(5L, 1L, RepaymentSource.CASH_DEPOSIT,
                new BigDecimal("1120.00"), new BigDecimal("1000.00"), new BigDecimal("120.00"),
                LocalDate.of(2026, 2, 1), null, null, Instant.now());
        when(loanRepaymentService.recordRepayment(eq(1L), any(LoanRepaymentRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/loans/1/repayments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/loans/repayments/5"));
    }

    @Test
    void recordRepayment_whenAmountBelowInterest_returns400() throws Exception {
        LoanRepaymentRequest request = new LoanRepaymentRequest(RepaymentSource.CASH_DEPOSIT, new BigDecimal("10.00"),
                LocalDate.of(2026, 2, 1), null, null);
        when(loanRepaymentService.recordRepayment(eq(1L), any(LoanRepaymentRequest.class)))
                .thenThrow(new BusinessRuleViolationException("Repayment amount is less than the accrued interest"));

        mockMvc.perform(post("/api/loans/1/repayments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithAnonymousUser
    void sanction_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(post("/api/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void sanction_withWrongRole_returns403() throws Exception {
        mockMvc.perform(post("/api/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }

    private EmployeeLoan loanOwnedBy(Long employeeId) {
        LoanType loanType = new LoanType(LoanTypeCode.CPF_SECURED, "CPF Secured Loan", new BigDecimal("12.00"), 48, true, true);
        Department department = new Department("ENG", "Engineering");
        Designation designation = new Designation("Manager");
        Employee employee = new Employee("EMP-001", "Asha", "Rao", "asha.rao@example.com",
                LocalDate.of(2020, 1, 1), department, designation);
        ReflectionTestUtils.setField(employee, "id", employeeId);
        EmployeeLoan loan = new EmployeeLoan(loanType, employee, "LN-2026-001", new BigDecimal("12000.00"),
                new BigDecimal("12.00"), 12, LocalDate.of(2026, 1, 1));
        ReflectionTestUtils.setField(loan, "id", 1L);
        return loan;
    }

    @Test
    void getById_asOwningEmployee_returns200() throws Exception {
        when(employeeLoanRepository.findById(1L)).thenReturn(Optional.of(loanOwnedBy(7L)));
        when(loanService.getById(1L)).thenReturn(responseWithStatus(LoanStatus.ACTIVE));

        mockMvc.perform(get("/api/loans/1")
                        .with(jwt().jwt(builder -> builder.claim("employee_id", "7"))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isOk());
    }

    @Test
    void getById_asDifferentEmployee_returns403() throws Exception {
        when(employeeLoanRepository.findById(1L)).thenReturn(Optional.of(loanOwnedBy(7L)));

        mockMvc.perform(get("/api/loans/1")
                        .with(jwt().jwt(builder -> builder.claim("employee_id", "8"))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isForbidden());
    }
}
