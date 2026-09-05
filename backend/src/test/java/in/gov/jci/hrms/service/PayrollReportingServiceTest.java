package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.PayslipSummaryResponse;
import in.gov.jci.hrms.entity.AttendanceStatus;
import in.gov.jci.hrms.entity.BankAccountStatus;
import in.gov.jci.hrms.entity.DailyAttendance;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeBankAccount;
import in.gov.jci.hrms.entity.HeadType;
import in.gov.jci.hrms.entity.PayrollRun;
import in.gov.jci.hrms.entity.Payslip;
import in.gov.jci.hrms.entity.PayslipItem;
import in.gov.jci.hrms.entity.SalaryHeadMaster;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.DailyAttendanceRepository;
import in.gov.jci.hrms.repository.EmployeeBankAccountRepository;
import in.gov.jci.hrms.repository.PayrollRunRepository;
import in.gov.jci.hrms.repository.PayslipItemRepository;
import in.gov.jci.hrms.repository.PayslipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayrollReportingServiceTest {

    private static final Long RUN_ID = 1L;
    private static final Long EMPLOYEE_ID = 5L;

    @Mock
    private PayrollRunRepository payrollRunRepository;
    @Mock
    private PayslipRepository payslipRepository;
    @Mock
    private PayslipItemRepository payslipItemRepository;
    @Mock
    private DailyAttendanceRepository dailyAttendanceRepository;
    @Mock
    private EmployeeBankAccountRepository bankAccountRepository;

    private PayrollReportingService payrollReportingService;
    private Employee employee;
    private PayrollRun run;

    @BeforeEach
    void setUp() {
        payrollReportingService = new PayrollReportingService(
                payrollRunRepository, payslipRepository, payslipItemRepository, dailyAttendanceRepository, bankAccountRepository);

        Department department = new Department("ENG", "Engineering");
        Designation designation = new Designation("Manager");
        employee = new Employee("EMP-001", "Asha", "Rao", "asha.rao@example.com",
                LocalDate.of(2020, 1, 1), department, designation);
        ReflectionTestUtils.setField(employee, "id", EMPLOYEE_ID);

        run = new PayrollRun(2026, 8, LocalDate.of(2026, 7, 26), LocalDate.of(2026, 8, 25));
        ReflectionTestUtils.setField(run, "id", RUN_ID);
    }

    /** employee_bank_accounts (V30) is the sole source of truth now that employees.bank_* is gone (V51). */
    private EmployeeBankAccount bankAccount(String bankName, String accountNumber, String ifsc) {
        return new EmployeeBankAccount(employee, bankName, "Main Branch", accountNumber, ifsc);
    }

    private Payslip payslipFor(Employee emp, boolean hold, BigDecimal netPay) {
        Payslip payslip = new Payslip(run, emp, new BigDecimal("40000.00"), new BigDecimal("52000.00"),
                new BigDecimal("4800.00"), new BigDecimal("5000.00"), netPay, BigDecimal.ZERO, hold);
        ReflectionTestUtils.setField(payslip, "id", 100L);
        return payslip;
    }

    // ---- generateBankDisbursementFile ----

    @Test
    void generateBankDisbursementFile_includesHeaderAndEmployeeRow() {
        when(payrollRunRepository.findById(RUN_ID)).thenReturn(Optional.of(run));
        when(payslipRepository.findByPayrollRunId(RUN_ID))
                .thenReturn(List.of(payslipFor(employee, false, new BigDecimal("47200.00"))));
        when(bankAccountRepository.findByEmployeeIdAndPrimaryDisbursalTrueAndStatus(EMPLOYEE_ID, BankAccountStatus.ACTIVE))
                .thenReturn(Optional.of(bankAccount("State Bank of India", "1234567890", "SBIN0000001")));

        String csv = payrollReportingService.generateBankDisbursementFile(RUN_ID);

        String[] lines = csv.strip().split("\n");
        assertThat(lines[0]).isEqualTo("EmployeeCode,EmployeeName,BankName,AccountNumber,IFSC,NetPay");
        assertThat(lines[1]).isEqualTo("EMP-001,Asha Rao,State Bank of India,1234567890,SBIN0000001,47200.00");
    }

    @Test
    void generateBankDisbursementFile_excludesHeldPayslips() {
        when(payrollRunRepository.findById(RUN_ID)).thenReturn(Optional.of(run));
        when(payslipRepository.findByPayrollRunId(RUN_ID))
                .thenReturn(List.of(payslipFor(employee, true, new BigDecimal("47200.00"))));

        String csv = payrollReportingService.generateBankDisbursementFile(RUN_ID);

        assertThat(csv.strip().split("\n")).hasSize(1); // header only
    }

    @Test
    void generateBankDisbursementFile_quotesFieldsContainingCommas() {
        when(payrollRunRepository.findById(RUN_ID)).thenReturn(Optional.of(run));
        when(payslipRepository.findByPayrollRunId(RUN_ID))
                .thenReturn(List.of(payslipFor(employee, false, new BigDecimal("47200.00"))));
        when(bankAccountRepository.findByEmployeeIdAndPrimaryDisbursalTrueAndStatus(EMPLOYEE_ID, BankAccountStatus.ACTIVE))
                .thenReturn(Optional.of(bankAccount("Bank, With Comma", "1234567890", "SBIN0000001")));

        String csv = payrollReportingService.generateBankDisbursementFile(RUN_ID);

        assertThat(csv).contains("\"Bank, With Comma\"");
    }

    @Test
    void generateBankDisbursementFile_whenRunMissing_throwsMasterDataNotFoundException() {
        when(payrollRunRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> payrollReportingService.generateBankDisbursementFile(99L))
                .isInstanceOf(MasterDataNotFoundException.class);
    }

    // ---- getPayslipSummary ----

    private SalaryHeadMaster head(String code, HeadType headType) {
        return new SalaryHeadMaster(code, code, headType, false, headType == HeadType.EARNING, true);
    }

    @Test
    void getPayslipSummary_computesPfBucketSplitAndAttendanceBreakdown() {
        Payslip payslip = payslipFor(employee, false, new BigDecimal("47200.00"));
        when(payrollRunRepository.findById(RUN_ID)).thenReturn(Optional.of(run));
        when(payslipRepository.findByPayrollRunIdAndEmployeeId(RUN_ID, EMPLOYEE_ID)).thenReturn(Optional.of(payslip));

        PayslipItem basic = new PayslipItem(payslip, head("BASIC", HeadType.EARNING), new BigDecimal("40000.00"));
        PayslipItem epfEe = new PayslipItem(payslip, head("EPF_EE", HeadType.DEDUCTION), new BigDecimal("4800.00"));
        PayslipItem epfEr = new PayslipItem(payslip, head("EPF_ER", HeadType.EMPLOYER_CONTRIBUTION), new BigDecimal("3600.00"));
        PayslipItem epsEr = new PayslipItem(payslip, head("EPS_ER", HeadType.EMPLOYER_CONTRIBUTION), new BigDecimal("1400.00"));
        when(payslipItemRepository.findByPayslipId(payslip.getId())).thenReturn(List.of(basic, epfEe, epfEr, epsEr));

        when(dailyAttendanceRepository.findByEmployeeIdAndAttendanceDateBetween(EMPLOYEE_ID, run.getStartDate(), run.getEndDate()))
                .thenReturn(List.of(
                        new DailyAttendance(employee, LocalDate.of(2026, 7, 27), AttendanceStatus.PRESENT),
                        new DailyAttendance(employee, LocalDate.of(2026, 7, 28), AttendanceStatus.ABSENT),
                        new DailyAttendance(employee, LocalDate.of(2026, 7, 29), AttendanceStatus.HALF_DAY)
                ));

        PayslipSummaryResponse summary = payrollReportingService.getPayslipSummary(RUN_ID, EMPLOYEE_ID);

        assertThat(summary.employeeCode()).isEqualTo("EMP-001");
        assertThat(summary.pfBucketSplit().employeeEpf()).isEqualByComparingTo("4800.00");
        assertThat(summary.pfBucketSplit().employerEpf()).isEqualByComparingTo("3600.00");
        assertThat(summary.pfBucketSplit().employerEps()).isEqualByComparingTo("1400.00");
        // Only deduction head is EPF_EE, so tax/other-deductions is zero.
        assertThat(summary.taxDeductions()).isEqualByComparingTo("0.00");
        assertThat(summary.attendance().presentDays()).isEqualTo(1);
        assertThat(summary.attendance().absentDays()).isEqualTo(1);
        assertThat(summary.attendance().halfDays()).isEqualTo(1);
        assertThat(summary.attendance().totalCycleDays()).isEqualTo(31);
        assertThat(summary.lineItems()).hasSize(4);
    }

    @Test
    void getPayslipSummary_whenPayslipMissing_throwsBusinessRuleViolationException() {
        when(payrollRunRepository.findById(RUN_ID)).thenReturn(Optional.of(run));
        when(payslipRepository.findByPayrollRunIdAndEmployeeId(RUN_ID, EMPLOYEE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> payrollReportingService.getPayslipSummary(RUN_ID, EMPLOYEE_ID))
                .isInstanceOf(BusinessRuleViolationException.class);
    }
}
