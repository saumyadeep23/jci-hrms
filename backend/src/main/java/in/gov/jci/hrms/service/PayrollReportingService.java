package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.AttendanceBreakdownResponse;
import in.gov.jci.hrms.dto.PayslipItemResponse;
import in.gov.jci.hrms.dto.PayslipSummaryResponse;
import in.gov.jci.hrms.dto.PfBucketSplitResponse;
import in.gov.jci.hrms.entity.AttendanceStatus;
import in.gov.jci.hrms.entity.BankAccountStatus;
import in.gov.jci.hrms.entity.DailyAttendance;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeBankAccount;
import in.gov.jci.hrms.entity.HeadType;
import in.gov.jci.hrms.entity.PayrollRun;
import in.gov.jci.hrms.entity.Payslip;
import in.gov.jci.hrms.entity.PayslipItem;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.DailyAttendanceRepository;
import in.gov.jci.hrms.repository.EmployeeBankAccountRepository;
import in.gov.jci.hrms.repository.PayrollRunRepository;
import in.gov.jci.hrms.repository.PayslipItemRepository;
import in.gov.jci.hrms.repository.PayslipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Two read-only reporting views over an already-computed payroll run (Phase
 * 5/9's payroll_runs/payslips/payslip_items). Neither method mutates
 * anything - both are pure projections for downstream consumption (a bank's
 * remittance system, a printable payslip).
 */
@Service
@Transactional(readOnly = true)
public class PayrollReportingService {

    private static final String RUN_ENTITY_NAME = "Payroll Run";
    private static final List<String> BANK_FILE_HEADERS =
            List.of("EmployeeCode", "EmployeeName", "BankName", "AccountNumber", "IFSC", "NetPay");

    private final PayrollRunRepository payrollRunRepository;
    private final PayslipRepository payslipRepository;
    private final PayslipItemRepository payslipItemRepository;
    private final DailyAttendanceRepository dailyAttendanceRepository;
    private final EmployeeBankAccountRepository bankAccountRepository;

    public PayrollReportingService(PayrollRunRepository payrollRunRepository, PayslipRepository payslipRepository,
                                    PayslipItemRepository payslipItemRepository,
                                    DailyAttendanceRepository dailyAttendanceRepository,
                                    EmployeeBankAccountRepository bankAccountRepository) {
        this.payrollRunRepository = payrollRunRepository;
        this.payslipRepository = payslipRepository;
        this.payslipItemRepository = payslipItemRepository;
        this.dailyAttendanceRepository = dailyAttendanceRepository;
        this.bankAccountRepository = bankAccountRepository;
    }

    /** Held payslips (isHold=true) are excluded - a held salary is, by definition, not being disbursed this cycle. */
    public String generateBankDisbursementFile(Long payrollRunId) {
        findRunOrThrow(payrollRunId);
        List<Payslip> payslips = payslipRepository.findByPayrollRunId(payrollRunId);

        StringBuilder csv = new StringBuilder();
        csv.append(String.join(",", BANK_FILE_HEADERS)).append('\n');
        for (Payslip payslip : payslips) {
            if (payslip.isHold()) {
                continue;
            }
            Employee employee = payslip.getEmployee();
            EmployeeBankAccount bankAccount = bankAccountRepository
                    .findByEmployeeIdAndPrimaryDisbursalTrueAndStatus(employee.getId(), BankAccountStatus.ACTIVE)
                    .orElse(null);
            csv.append(csvField(employee.getEmployeeCode())).append(',')
                    .append(csvField(employee.getFirstName() + " " + employee.getLastName())).append(',')
                    .append(csvField(bankAccount != null ? bankAccount.getBankName() : null)).append(',')
                    .append(csvField(bankAccount != null ? bankAccount.getBankAccountNumber() : null)).append(',')
                    .append(csvField(bankAccount != null ? bankAccount.getBankIfsc() : null)).append(',')
                    .append(payslip.getNetPay().toPlainString())
                    .append('\n');
        }
        return csv.toString();
    }

    public PayslipSummaryResponse getPayslipSummary(Long payrollRunId, Long employeeId) {
        PayrollRun run = findRunOrThrow(payrollRunId);
        Payslip payslip = payslipRepository.findByPayrollRunIdAndEmployeeId(payrollRunId, employeeId)
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "No payslip found for employee " + employeeId + " in payroll run " + payrollRunId));

        List<PayslipItem> items = payslipItemRepository.findByPayslipId(payslip.getId());
        Map<String, BigDecimal> amountByCode = items.stream()
                .collect(java.util.stream.Collectors.toMap(
                        item -> item.getSalaryHead().getCode(), PayslipItem::getAmount, (a, b) -> a));

        PfBucketSplitResponse pfBucketSplit = new PfBucketSplitResponse(
                amountByCode.getOrDefault("EPF_EE", BigDecimal.ZERO),
                amountByCode.getOrDefault("EPF_ER", BigDecimal.ZERO),
                amountByCode.getOrDefault("EPS_ER", BigDecimal.ZERO));

        BigDecimal otherDeductions = items.stream()
                .filter(item -> item.getSalaryHead().getHeadType() == HeadType.DEDUCTION)
                .filter(item -> !"EPF_EE".equals(item.getSalaryHead().getCode()))
                .map(PayslipItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Employee employee = payslip.getEmployee();
        return new PayslipSummaryResponse(
                run.getId(), run.getCycleYear(), run.getCycleMonth(), run.getStartDate(), run.getEndDate(),
                employee.getId(), employee.getEmployeeCode(), employee.getFirstName() + " " + employee.getLastName(),
                payslip.getBasicPay(), payslip.getTotalEarnings(), payslip.getTotalDeductions(),
                payslip.getEmployerContributions(), payslip.getNetPay(), otherDeductions,
                attendanceBreakdown(employeeId, run, payslip.getLopDays()), pfBucketSplit,
                items.stream().map(PayslipItemResponse::from).toList()
        );
    }

    private AttendanceBreakdownResponse attendanceBreakdown(Long employeeId, PayrollRun run, BigDecimal lopDays) {
        List<DailyAttendance> records =
                dailyAttendanceRepository.findByEmployeeIdAndAttendanceDateBetween(employeeId, run.getStartDate(), run.getEndDate());
        Map<AttendanceStatus, Long> counts = records.stream()
                .collect(java.util.stream.Collectors.groupingBy(DailyAttendance::getStatus, java.util.stream.Collectors.counting()));
        int totalCycleDays = (int) (ChronoUnit.DAYS.between(run.getStartDate(), run.getEndDate()) + 1);

        return new AttendanceBreakdownResponse(
                totalCycleDays,
                counts.getOrDefault(AttendanceStatus.PRESENT, 0L),
                counts.getOrDefault(AttendanceStatus.HALF_DAY, 0L),
                counts.getOrDefault(AttendanceStatus.ABSENT, 0L),
                counts.getOrDefault(AttendanceStatus.ON_LEAVE, 0L),
                counts.getOrDefault(AttendanceStatus.HOLIDAY, 0L),
                counts.getOrDefault(AttendanceStatus.WEEKLY_OFF, 0L),
                lopDays
        );
    }

    private String csvField(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private PayrollRun findRunOrThrow(Long id) {
        return payrollRunRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException(RUN_ENTITY_NAME, id));
    }
}
