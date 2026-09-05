package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.CategoryStatus;
import in.gov.jci.hrms.dto.LegacyMigrationStatusResponse;
import in.gov.jci.hrms.dto.RejectedRowSummary;
import in.gov.jci.hrms.dto.SalaryHistoryEntryResponse;
import in.gov.jci.hrms.dto.ServiceBookEventResponse;
import in.gov.jci.hrms.dto.StageLeaveBalancesRequest;
import in.gov.jci.hrms.dto.StageLoansRequest;
import in.gov.jci.hrms.dto.StageSalaryHistoryRequest;
import in.gov.jci.hrms.dto.StageServiceBookRequest;
import in.gov.jci.hrms.dto.StagingAcknowledgmentResponse;
import in.gov.jci.hrms.dto.StagingLoanRequest;
import in.gov.jci.hrms.dto.StagingLoanTransactionRequest;
import in.gov.jci.hrms.dto.StagingSalaryHeadRequest;
import in.gov.jci.hrms.dto.StagingSalaryMonthRequest;
import in.gov.jci.hrms.dto.ValidateAndPromoteRequest;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeLoan;
import in.gov.jci.hrms.entity.EmployeeServiceBook;
import in.gov.jci.hrms.entity.HeadCategory;
import in.gov.jci.hrms.entity.LeaveBalance;
import in.gov.jci.hrms.entity.LeaveType;
import in.gov.jci.hrms.entity.LoanRepayment;
import in.gov.jci.hrms.entity.LoanStatus;
import in.gov.jci.hrms.entity.LoanType;
import in.gov.jci.hrms.entity.LoanTypeCode;
import in.gov.jci.hrms.entity.PayrollRun;
import in.gov.jci.hrms.entity.PayrollRunStatus;
import in.gov.jci.hrms.entity.PayrollRunType;
import in.gov.jci.hrms.entity.Payslip;
import in.gov.jci.hrms.entity.PayslipItem;
import in.gov.jci.hrms.entity.RegionalOffice;
import in.gov.jci.hrms.entity.RepaymentSource;
import in.gov.jci.hrms.entity.SalaryHeadMaster;
import in.gov.jci.hrms.entity.StagingLegacyLeaveBalance;
import in.gov.jci.hrms.entity.StagingLegacyLoan;
import in.gov.jci.hrms.entity.StagingLegacyLoanTransaction;
import in.gov.jci.hrms.entity.StagingLegacySalaryHead;
import in.gov.jci.hrms.entity.StagingLegacySalaryMonth;
import in.gov.jci.hrms.entity.StagingLegacyServiceBook;
import in.gov.jci.hrms.entity.StagingRowStatus;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.DepartmentRepository;
import in.gov.jci.hrms.repository.DesignationRepository;
import in.gov.jci.hrms.repository.EmployeeLoanRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.EmployeeServiceBookRepository;
import in.gov.jci.hrms.repository.LeaveBalanceRepository;
import in.gov.jci.hrms.repository.LeaveTypeRepository;
import in.gov.jci.hrms.repository.LoanRepaymentRepository;
import in.gov.jci.hrms.repository.LoanTypeRepository;
import in.gov.jci.hrms.repository.PayrollRunRepository;
import in.gov.jci.hrms.repository.PayslipItemRepository;
import in.gov.jci.hrms.repository.PayslipRepository;
import in.gov.jci.hrms.repository.RegionalOfficeRepository;
import in.gov.jci.hrms.repository.SalaryHeadMasterRepository;
import in.gov.jci.hrms.repository.StagingLegacyLeaveBalanceRepository;
import in.gov.jci.hrms.repository.StagingLegacyLoanRepository;
import in.gov.jci.hrms.repository.StagingLegacyLoanTransactionRepository;
import in.gov.jci.hrms.repository.StagingLegacySalaryHeadRepository;
import in.gov.jci.hrms.repository.StagingLegacySalaryMonthRepository;
import in.gov.jci.hrms.repository.StagingLegacyServiceBookRepository;
import in.gov.jci.hrms.repository.UnifiedSalaryHeadHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Phase 9 (SRS Addendum 4.19): non-blocking, row-level legacy data migration
 * (NFR-MIG.1). Each promote* method only ever touches PENDING rows, so
 * validateAndPromote() is safe to call repeatedly - already-PROMOTED/
 * REJECTED rows are simply skipped on a re-run.
 *
 * <p>Two deliberate gaps carried over from the legacy source, both a
 * consequence of the staging schema not modeling anything richer:
 * <ul>
 *   <li>Migrated payslips always carry employerContributions=ZERO -
 *   head_category only distinguishes EARNING/EMPLOYEE_DEDUCTION, there is no
 *   employer-side category to migrate.
 *   <li>Service-book from_designation/from_department/from_ro staging
 *   columns are captured but never surfacea - employee_service_book only has
 *   a single (resulting) department/designation/regional_office FK set, so
 *   only the to_* labels are resolved and stored.
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class LegacyMigrationService {

    private static final BigDecimal RECONCILIATION_TOLERANCE = new BigDecimal("0.05");
    private static final int CUTOFF_YEARS = 5;
    private static final Set<LoanStatus> LEGACY_LOAN_STATUSES =
            EnumSet.of(LoanStatus.ACTIVE, LoanStatus.CLOSED, LoanStatus.FORECLOSED, LoanStatus.WRITTEN_OFF);

    private final StagingLegacyLeaveBalanceRepository leaveBalanceStagingRepository;
    private final StagingLegacyLoanRepository loanStagingRepository;
    private final StagingLegacyLoanTransactionRepository loanTransactionStagingRepository;
    private final StagingLegacySalaryMonthRepository salaryMonthStagingRepository;
    private final StagingLegacySalaryHeadRepository salaryHeadStagingRepository;
    private final StagingLegacyServiceBookRepository serviceBookStagingRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LoanTypeRepository loanTypeRepository;
    private final EmployeeLoanRepository employeeLoanRepository;
    private final LoanRepaymentRepository loanRepaymentRepository;
    private final PayrollRunRepository payrollRunRepository;
    private final PayslipRepository payslipRepository;
    private final PayslipItemRepository payslipItemRepository;
    private final SalaryHeadMasterRepository salaryHeadMasterRepository;
    private final DepartmentRepository departmentRepository;
    private final DesignationRepository designationRepository;
    private final RegionalOfficeRepository regionalOfficeRepository;
    private final EmployeeServiceBookRepository employeeServiceBookRepository;
    private final UnifiedSalaryHeadHistoryRepository unifiedSalaryHeadHistoryRepository;
    private final PayrollComputationService payrollComputationService;

    public LegacyMigrationService(StagingLegacyLeaveBalanceRepository leaveBalanceStagingRepository,
                                   StagingLegacyLoanRepository loanStagingRepository,
                                   StagingLegacyLoanTransactionRepository loanTransactionStagingRepository,
                                   StagingLegacySalaryMonthRepository salaryMonthStagingRepository,
                                   StagingLegacySalaryHeadRepository salaryHeadStagingRepository,
                                   StagingLegacyServiceBookRepository serviceBookStagingRepository,
                                   EmployeeRepository employeeRepository,
                                   LeaveTypeRepository leaveTypeRepository,
                                   LeaveBalanceRepository leaveBalanceRepository,
                                   LoanTypeRepository loanTypeRepository,
                                   EmployeeLoanRepository employeeLoanRepository,
                                   LoanRepaymentRepository loanRepaymentRepository,
                                   PayrollRunRepository payrollRunRepository,
                                   PayslipRepository payslipRepository,
                                   PayslipItemRepository payslipItemRepository,
                                   SalaryHeadMasterRepository salaryHeadMasterRepository,
                                   DepartmentRepository departmentRepository,
                                   DesignationRepository designationRepository,
                                   RegionalOfficeRepository regionalOfficeRepository,
                                   EmployeeServiceBookRepository employeeServiceBookRepository,
                                   UnifiedSalaryHeadHistoryRepository unifiedSalaryHeadHistoryRepository,
                                   PayrollComputationService payrollComputationService) {
        this.leaveBalanceStagingRepository = leaveBalanceStagingRepository;
        this.loanStagingRepository = loanStagingRepository;
        this.loanTransactionStagingRepository = loanTransactionStagingRepository;
        this.salaryMonthStagingRepository = salaryMonthStagingRepository;
        this.salaryHeadStagingRepository = salaryHeadStagingRepository;
        this.serviceBookStagingRepository = serviceBookStagingRepository;
        this.employeeRepository = employeeRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.loanTypeRepository = loanTypeRepository;
        this.employeeLoanRepository = employeeLoanRepository;
        this.loanRepaymentRepository = loanRepaymentRepository;
        this.payrollRunRepository = payrollRunRepository;
        this.payslipRepository = payslipRepository;
        this.payslipItemRepository = payslipItemRepository;
        this.salaryHeadMasterRepository = salaryHeadMasterRepository;
        this.departmentRepository = departmentRepository;
        this.designationRepository = designationRepository;
        this.regionalOfficeRepository = regionalOfficeRepository;
        this.employeeServiceBookRepository = employeeServiceBookRepository;
        this.unifiedSalaryHeadHistoryRepository = unifiedSalaryHeadHistoryRepository;
        this.payrollComputationService = payrollComputationService;
    }

    // ---------------------------------------------------------------
    // Staging (bulk ingestion, FR-MIG staging endpoints)
    // ---------------------------------------------------------------

    @Transactional
    public StagingAcknowledgmentResponse stageLeaveBalances(StageLeaveBalancesRequest request) {
        List<StagingLegacyLeaveBalance> rows = request.rows().stream()
                .map(r -> new StagingLegacyLeaveBalance(r.employeeCode(), r.leaveTypeCode(), r.openingBalance(), r.asOnDate()))
                .toList();
        leaveBalanceStagingRepository.saveAll(rows);
        return new StagingAcknowledgmentResponse(rows.size());
    }

    @Transactional
    public StagingAcknowledgmentResponse stageLoans(StageLoansRequest request) {
        int count = 0;
        for (StagingLoanRequest r : request.rows()) {
            LoanStatus loanStatus = parseLegacyLoanStatus(r.loanStatus());
            StagingLegacyLoan loan = new StagingLegacyLoan(r.employeeCode(), r.loanTypeCode(), r.loanAccountNumber(),
                    r.principalAmount(), r.interestRate(), r.sanctionDate(), r.tenureMonths(), loanStatus);
            loan.setDisbursementDate(r.disbursementDate());
            loan.setOutstandingPrincipal(r.outstandingPrincipal());
            loan.setRemainingInstallments(r.remainingInstallments());
            loan = loanStagingRepository.save(loan);
            count++;

            if (r.transactions() != null) {
                for (StagingLoanTransactionRequest t : r.transactions()) {
                    loanTransactionStagingRepository.save(new StagingLegacyLoanTransaction(
                            loan.getId(), r.loanAccountNumber(), t.paymentDate(),
                            t.principalComponent(), t.interestComponent(), t.totalAmount()));
                }
            }
        }
        return new StagingAcknowledgmentResponse(count);
    }

    @Transactional
    public StagingAcknowledgmentResponse stageSalaryHistory(StageSalaryHistoryRequest request) {
        int count = 0;
        for (StagingSalaryMonthRequest r : request.rows()) {
            StagingLegacySalaryMonth month = new StagingLegacySalaryMonth(r.employeeCode(), r.salaryYear(), r.salaryMonth(),
                    r.basicPay(), r.grossEarnings(), r.totalDeductions(), r.netPay());
            month = salaryMonthStagingRepository.save(month);
            count++;

            for (StagingSalaryHeadRequest h : r.heads()) {
                HeadCategory category = parseHeadCategory(h.headCategory());
                salaryHeadStagingRepository.save(
                        new StagingLegacySalaryHead(month.getId(), h.salaryHeadCode(), category, h.amount()));
            }
        }
        return new StagingAcknowledgmentResponse(count);
    }

    @Transactional
    public StagingAcknowledgmentResponse stageServiceBook(StageServiceBookRequest request) {
        List<StagingLegacyServiceBook> rows = request.rows().stream().map(r -> {
            StagingLegacyServiceBook row = new StagingLegacyServiceBook(r.employeeCode(), r.eventDate(), r.eventType());
            row.setOrderNumber(r.orderNumber());
            row.setOrderDate(r.orderDate());
            row.setFromDesignation(r.fromDesignation());
            row.setToDesignation(r.toDesignation());
            row.setFromDepartment(r.fromDepartment());
            row.setToDepartment(r.toDepartment());
            row.setFromRo(r.fromRo());
            row.setToRo(r.toRo());
            row.setBasicPay(r.basicPay());
            row.setDescription(r.description());
            return row;
        }).toList();
        serviceBookStagingRepository.saveAll(rows);
        return new StagingAcknowledgmentResponse(rows.size());
    }

    private LoanStatus parseLegacyLoanStatus(String raw) {
        LoanStatus status;
        try {
            status = LoanStatus.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleViolationException("Unrecognized loan status: " + raw);
        }
        if (!LEGACY_LOAN_STATUSES.contains(status)) {
            throw new BusinessRuleViolationException(
                    "Loan status " + raw + " is not valid for legacy migration (expected ACTIVE, CLOSED, FORECLOSED, or WRITTEN_OFF)");
        }
        return status;
    }

    private HeadCategory parseHeadCategory(String raw) {
        try {
            return HeadCategory.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleViolationException("Unrecognized salary head category: " + raw);
        }
    }

    // ---------------------------------------------------------------
    // Validate & promote (FR-MIG.1 - FR-MIG.10)
    // ---------------------------------------------------------------

    @Transactional
    public LegacyMigrationStatusResponse validateAndPromote(ValidateAndPromoteRequest request) {
        LocalDate cutoffDate = request.cutoffDate() != null ? request.cutoffDate() : LocalDate.now();
        promoteLeaveBalances();
        promoteLoans();
        promoteLoanTransactions();
        promoteSalaryHistory(cutoffDate, request.hrAdminUsername());
        promoteServiceBook();
        return getStatus();
    }

    /** FR-MIG.1: validate real employee/leave type, reject duplicates, else promote as an opening balance. */
    private void promoteLeaveBalances() {
        for (StagingLegacyLeaveBalance row : leaveBalanceStagingRepository.findByStatus(StagingRowStatus.PENDING)) {
            Optional<Employee> employee = employeeRepository.findByEmployeeCode(row.getEmployeeCode());
            if (employee.isEmpty()) {
                reject(row::setStatus, row::setRejectionReason, "No employee found with code " + row.getEmployeeCode());
                continue;
            }
            Optional<LeaveType> leaveType = leaveTypeRepository.findByCode(row.getLeaveTypeCode());
            if (leaveType.isEmpty()) {
                reject(row::setStatus, row::setRejectionReason, "No leave type found with code " + row.getLeaveTypeCode());
                continue;
            }
            int year = row.getAsOnDate().getYear();
            if (leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(employee.get().getId(), leaveType.get().getId(), year).isPresent()) {
                reject(row::setStatus, row::setRejectionReason, "A leave balance already exists for employee "
                        + row.getEmployeeCode() + ", leave type " + row.getLeaveTypeCode() + ", year " + year);
                continue;
            }

            leaveBalanceRepository.save(new LeaveBalance(employee.get(), leaveType.get(), year, row.getOpeningBalance()));
            row.setStatus(StagingRowStatus.PROMOTED);
        }
    }

    /** FR-MIG.2 & FR-MIG.3: migrate sanction record for every status; ACTIVE loans require an outstanding balance. */
    private void promoteLoans() {
        for (StagingLegacyLoan row : loanStagingRepository.findByStatus(StagingRowStatus.PENDING)) {
            Optional<Employee> employee = employeeRepository.findByEmployeeCode(row.getEmployeeCode());
            if (employee.isEmpty()) {
                reject(row::setStatus, row::setRejectionReason, "No employee found with code " + row.getEmployeeCode());
                continue;
            }

            LoanTypeCode loanTypeCode;
            try {
                loanTypeCode = LoanTypeCode.valueOf(row.getLoanTypeCode());
            } catch (IllegalArgumentException e) {
                reject(row::setStatus, row::setRejectionReason, "Unrecognized loan type code: " + row.getLoanTypeCode());
                continue;
            }
            Optional<LoanType> loanType = loanTypeRepository.findByCode(loanTypeCode);
            if (loanType.isEmpty()) {
                reject(row::setStatus, row::setRejectionReason, "No loan type configured for code " + row.getLoanTypeCode());
                continue;
            }

            if (employeeLoanRepository.findByLoanAccountNumber(row.getLoanAccountNumber()).isPresent()) {
                reject(row::setStatus, row::setRejectionReason, "Loan account number " + row.getLoanAccountNumber() + " already exists");
                continue;
            }

            if (row.getLoanStatus() == LoanStatus.ACTIVE
                    && (row.getOutstandingPrincipal() == null || row.getRemainingInstallments() == null)) {
                reject(row::setStatus, row::setRejectionReason, "ACTIVE loan " + row.getLoanAccountNumber()
                        + " is missing outstanding_principal/remaining_installments");
                continue;
            }

            EmployeeLoan loan = new EmployeeLoan(loanType.get(), employee.get(), row.getLoanAccountNumber(),
                    row.getPrincipalAmount(), row.getInterestRate(), row.getTenureMonths(), row.getSanctionDate());
            loan.setStatus(row.getLoanStatus());
            loan.setOutstandingPrincipal(row.getOutstandingPrincipal() != null ? row.getOutstandingPrincipal() : BigDecimal.ZERO);
            loan.setRemainingInstallments(row.getRemainingInstallments() != null ? row.getRemainingInstallments() : 0);
            employeeLoanRepository.save(loan);
            row.setStatus(StagingRowStatus.PROMOTED);
        }
    }

    /** FR-MIG.3 (transaction history) & FR-MIG.4 (orphan guard): only for loans that promoted as ACTIVE. Must run after promoteLoans(). */
    private void promoteLoanTransactions() {
        for (StagingLegacyLoanTransaction row : loanTransactionStagingRepository.findByStatus(StagingRowStatus.PENDING)) {
            Optional<StagingLegacyLoan> parent = loanStagingRepository.findById(row.getStagingLoanId());
            if (parent.isEmpty()) {
                reject(row::setStatus, row::setRejectionReason, "Parent loan staging row " + row.getStagingLoanId() + " not found");
                continue;
            }
            if (parent.get().getStatus() != StagingRowStatus.PROMOTED) {
                reject(row::setStatus, row::setRejectionReason, "Parent loan " + row.getLoanAccountNumber()
                        + " failed validation: " + parent.get().getRejectionReason());
                continue;
            }
            if (parent.get().getLoanStatus() != LoanStatus.ACTIVE) {
                reject(row::setStatus, row::setRejectionReason, "Repayment history is only migrated for ACTIVE loans; loan "
                        + row.getLoanAccountNumber() + " is " + parent.get().getLoanStatus());
                continue;
            }

            Optional<EmployeeLoan> loan = employeeLoanRepository.findByLoanAccountNumber(row.getLoanAccountNumber());
            if (loan.isEmpty()) {
                reject(row::setStatus, row::setRejectionReason, "Promoted loan " + row.getLoanAccountNumber() + " could not be located");
                continue;
            }

            LoanRepayment repayment = new LoanRepayment(loan.get(), RepaymentSource.LEGACY_IMPORT, row.getTotalAmount(),
                    row.getPrincipalComponent(), row.getInterestComponent(), row.getPaymentDate());
            loanRepaymentRepository.save(repayment);
            row.setStatus(StagingRowStatus.PROMOTED);
        }
    }

    /**
     * FR-MIG.5-FR-MIG.9: reject stale/incomplete/unreconciled months, else
     * promote as a shared HISTORIC_MIGRATED PayrollRun (one per calendar
     * month, since payroll_runs has UNIQUE(cycle_year, cycle_month)) created
     * directly in FINALIZED status - this is what "locked against reopening"
     * (FR-MIG.8) and "HR-Admin authorization" (FR-MIG.9) reduce to, since
     * PayrollRunService has no reopen path to guard against otherwise.
     */
    private void promoteSalaryHistory(LocalDate cutoffDate, String hrAdminUsername) {
        LocalDate earliestAllowed = cutoffDate.minusYears(CUTOFF_YEARS);

        for (StagingLegacySalaryMonth row : salaryMonthStagingRepository.findByStatus(StagingRowStatus.PENDING)) {
            Optional<Employee> employee = employeeRepository.findByEmployeeCode(row.getEmployeeCode());
            if (employee.isEmpty()) {
                reject(row::setStatus, row::setRejectionReason, "No employee found with code " + row.getEmployeeCode());
                continue;
            }

            LocalDate monthEnd = LocalDate.of(row.getSalaryYear(), row.getSalaryMonth(), 1).plusMonths(1).minusDays(1);
            if (monthEnd.isBefore(earliestAllowed)) {
                reject(row::setStatus, row::setRejectionReason, "Salary month " + row.getSalaryYear() + "-" + row.getSalaryMonth()
                        + " is older than the " + CUTOFF_YEARS + "-year migration cutoff (" + earliestAllowed + ")");
                continue;
            }

            List<StagingLegacySalaryHead> heads = salaryHeadStagingRepository.findByStagingSalaryMonthId(row.getId());
            if (heads.isEmpty()) {
                reject(row::setStatus, row::setRejectionReason, "Salary month " + row.getSalaryYear() + "-" + row.getSalaryMonth()
                        + " has no head-wise breakdown");
                continue;
            }

            Map<StagingLegacySalaryHead, SalaryHeadMaster> resolvedHeads = new LinkedHashMap<>();
            String unresolvedCode = null;
            for (StagingLegacySalaryHead head : heads) {
                Optional<SalaryHeadMaster> headMaster = salaryHeadMasterRepository.findByCode(head.getSalaryHeadCode());
                if (headMaster.isEmpty()) {
                    unresolvedCode = head.getSalaryHeadCode();
                    break;
                }
                resolvedHeads.put(head, headMaster.get());
            }
            if (unresolvedCode != null) {
                reject(row::setStatus, row::setRejectionReason, "Unrecognized salary head code: " + unresolvedCode);
                continue;
            }

            BigDecimal earningsSum = heads.stream()
                    .filter(h -> h.getHeadCategory() == HeadCategory.EARNING)
                    .map(StagingLegacySalaryHead::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal deductionsSum = heads.stream()
                    .filter(h -> h.getHeadCategory() == HeadCategory.EMPLOYEE_DEDUCTION)
                    .map(StagingLegacySalaryHead::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal computedGross = earningsSum.add(row.getBasicPay());
            BigDecimal grossDiff = computedGross.subtract(row.getGrossEarnings()).abs();
            if (grossDiff.compareTo(RECONCILIATION_TOLERANCE) > 0) {
                reject(row::setStatus, row::setRejectionReason, "Earning heads + basic pay (" + computedGross
                        + ") does not reconcile with gross_earnings (" + row.getGrossEarnings() + "); difference " + grossDiff);
                continue;
            }
            BigDecimal deductionDiff = deductionsSum.subtract(row.getTotalDeductions()).abs();
            if (deductionDiff.compareTo(RECONCILIATION_TOLERANCE) > 0) {
                reject(row::setStatus, row::setRejectionReason, "Deduction heads (" + deductionsSum
                        + ") does not reconcile with total_deductions (" + row.getTotalDeductions() + "); difference " + deductionDiff);
                continue;
            }

            PayrollRun run = payrollRunRepository.findByCycleYearAndCycleMonth(row.getSalaryYear(), row.getSalaryMonth())
                    .orElseGet(() -> createMigratedPayrollRun(row.getSalaryYear(), row.getSalaryMonth(), hrAdminUsername));

            if (payslipRepository.findByPayrollRunIdAndEmployeeId(run.getId(), employee.get().getId()).isPresent()) {
                reject(row::setStatus, row::setRejectionReason, "A payslip already exists for employee "
                        + row.getEmployeeCode() + " in " + row.getSalaryYear() + "-" + row.getSalaryMonth());
                continue;
            }

            Payslip payslip = new Payslip(run, employee.get(), row.getBasicPay(), row.getGrossEarnings(),
                    row.getTotalDeductions(), BigDecimal.ZERO, row.getNetPay(), BigDecimal.ZERO, false);
            payslip = payslipRepository.save(payslip);

            SalaryHeadMaster basicHead = salaryHeadMasterRepository.findByCode("BASIC")
                    .orElseThrow(() -> new BusinessRuleViolationException("Salary head master is missing the BASIC code"));
            payslipItemRepository.save(new PayslipItem(payslip, basicHead, row.getBasicPay()));

            for (Map.Entry<StagingLegacySalaryHead, SalaryHeadMaster> entry : resolvedHeads.entrySet()) {
                payslipItemRepository.save(new PayslipItem(payslip, entry.getValue(), entry.getKey().getAmount()));
                entry.getKey().setStatus(StagingRowStatus.PROMOTED);
            }

            row.setStatus(StagingRowStatus.PROMOTED);
        }
    }

    private PayrollRun createMigratedPayrollRun(int salaryYear, int salaryMonth, String hrAdminUsername) {
        PayrollComputationService.CycleDates dates = payrollComputationService.deriveCycleDates(salaryYear, salaryMonth);
        PayrollRun run = new PayrollRun(salaryYear, salaryMonth, dates.startDate(), dates.endDate());
        run.setRunType(PayrollRunType.HISTORIC_MIGRATED);
        run.setMigrated(true);
        run.setStatus(PayrollRunStatus.FINALIZED);
        run.setFinalizedBy(hrAdminUsername);
        run.setFinalizedAt(Instant.now());
        return payrollRunRepository.save(run);
    }

    /** FR-MIG.10: backfill career events, resolving to_* labels against master data on a best-effort basis. */
    private void promoteServiceBook() {
        for (StagingLegacyServiceBook row : serviceBookStagingRepository.findByStatus(StagingRowStatus.PENDING)) {
            Optional<Employee> employee = employeeRepository.findByEmployeeCode(row.getEmployeeCode());
            if (employee.isEmpty()) {
                reject(row::setStatus, row::setRejectionReason, "No employee found with code " + row.getEmployeeCode());
                continue;
            }

            EmployeeServiceBook event = new EmployeeServiceBook(employee.get(), row.getEventDate(), row.getEventType());
            event.setOrderNumber(row.getOrderNumber());
            event.setOrderDate(row.getOrderDate());
            event.setBasicPay(row.getBasicPay());
            event.setEventDescription(row.getDescription());
            event.setDepartment(resolveDepartment(row.getToDepartment()));
            event.setDesignation(resolveDesignation(row.getToDesignation()));
            event.setRegionalOffice(resolveRegionalOffice(row.getToRo()));
            event.setMigrated(true);
            employeeServiceBookRepository.save(event);
            row.setStatus(StagingRowStatus.PROMOTED);
        }
    }

    private Department resolveDepartment(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return departmentRepository.findByName(name).orElse(null);
    }

    private Designation resolveDesignation(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        return designationRepository.findByTitle(title).orElse(null);
    }

    private RegionalOffice resolveRegionalOffice(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return regionalOfficeRepository.findByName(name).orElse(null);
    }

    private void reject(Consumer<StagingRowStatus> statusSetter, Consumer<String> reasonSetter, String reason) {
        statusSetter.accept(StagingRowStatus.REJECTED);
        reasonSetter.accept(reason);
    }

    // ---------------------------------------------------------------
    // Status & read views
    // ---------------------------------------------------------------

    public LegacyMigrationStatusResponse getStatus() {
        List<CategoryStatus> categories = List.of(
                leaveBalanceCategoryStatus(),
                loanCategoryStatus(),
                loanTransactionCategoryStatus(),
                salaryMonthCategoryStatus(),
                salaryHeadCategoryStatus(),
                serviceBookCategoryStatus()
        );
        return new LegacyMigrationStatusResponse(categories);
    }

    private CategoryStatus leaveBalanceCategoryStatus() {
        List<RejectedRowSummary> rejected = leaveBalanceStagingRepository.findByStatus(StagingRowStatus.REJECTED).stream()
                .map(r -> new RejectedRowSummary(r.getId(), r.getEmployeeCode() + "/" + r.getLeaveTypeCode(), r.getRejectionReason()))
                .toList();
        return new CategoryStatus("leave_balances",
                leaveBalanceStagingRepository.countByStatus(StagingRowStatus.PENDING),
                leaveBalanceStagingRepository.countByStatus(StagingRowStatus.PROMOTED),
                rejected.size(), rejected);
    }

    private CategoryStatus loanCategoryStatus() {
        List<RejectedRowSummary> rejected = loanStagingRepository.findByStatus(StagingRowStatus.REJECTED).stream()
                .map(r -> new RejectedRowSummary(r.getId(), r.getLoanAccountNumber(), r.getRejectionReason()))
                .toList();
        return new CategoryStatus("loans",
                loanStagingRepository.countByStatus(StagingRowStatus.PENDING),
                loanStagingRepository.countByStatus(StagingRowStatus.PROMOTED),
                rejected.size(), rejected);
    }

    private CategoryStatus loanTransactionCategoryStatus() {
        List<RejectedRowSummary> rejected = loanTransactionStagingRepository.findByStatus(StagingRowStatus.REJECTED).stream()
                .map(r -> new RejectedRowSummary(r.getId(), r.getLoanAccountNumber() + "@" + r.getPaymentDate(), r.getRejectionReason()))
                .toList();
        return new CategoryStatus("loan_transactions",
                loanTransactionStagingRepository.countByStatus(StagingRowStatus.PENDING),
                loanTransactionStagingRepository.countByStatus(StagingRowStatus.PROMOTED),
                rejected.size(), rejected);
    }

    private CategoryStatus salaryMonthCategoryStatus() {
        List<RejectedRowSummary> rejected = salaryMonthStagingRepository.findByStatus(StagingRowStatus.REJECTED).stream()
                .map(r -> new RejectedRowSummary(r.getId(), r.getEmployeeCode() + "/" + r.getSalaryYear() + "-" + r.getSalaryMonth(), r.getRejectionReason()))
                .toList();
        return new CategoryStatus("salary_months",
                salaryMonthStagingRepository.countByStatus(StagingRowStatus.PENDING),
                salaryMonthStagingRepository.countByStatus(StagingRowStatus.PROMOTED),
                rejected.size(), rejected);
    }

    private CategoryStatus salaryHeadCategoryStatus() {
        List<RejectedRowSummary> rejected = salaryHeadStagingRepository.findByStatus(StagingRowStatus.REJECTED).stream()
                .map(r -> new RejectedRowSummary(r.getId(), r.getStagingSalaryMonthId() + "/" + r.getSalaryHeadCode(), r.getRejectionReason()))
                .toList();
        return new CategoryStatus("salary_heads",
                salaryHeadStagingRepository.countByStatus(StagingRowStatus.PENDING),
                salaryHeadStagingRepository.countByStatus(StagingRowStatus.PROMOTED),
                rejected.size(), rejected);
    }

    private CategoryStatus serviceBookCategoryStatus() {
        List<RejectedRowSummary> rejected = serviceBookStagingRepository.findByStatus(StagingRowStatus.REJECTED).stream()
                .map(r -> new RejectedRowSummary(r.getId(), r.getEmployeeCode() + "@" + r.getEventDate(), r.getRejectionReason()))
                .toList();
        return new CategoryStatus("service_book",
                serviceBookStagingRepository.countByStatus(StagingRowStatus.PENDING),
                serviceBookStagingRepository.countByStatus(StagingRowStatus.PROMOTED),
                rejected.size(), rejected);
    }

    public List<ServiceBookEventResponse> getServiceBookTimeline(Long employeeId) {
        if (!employeeRepository.existsById(employeeId)) {
            throw new MasterDataNotFoundException("Employee", employeeId);
        }
        return employeeServiceBookRepository.findByEmployeeIdOrderByEventDateAscIdAsc(employeeId).stream()
                .map(ServiceBookEventResponse::from)
                .toList();
    }

    /** "Micro-level 5-year salary history" - filters the unified view to the trailing 5 years from today. */
    public List<SalaryHistoryEntryResponse> getSalaryHistory(Long employeeId) {
        if (!employeeRepository.existsById(employeeId)) {
            throw new MasterDataNotFoundException("Employee", employeeId);
        }
        LocalDate cutoff = LocalDate.now().minusYears(CUTOFF_YEARS);
        return unifiedSalaryHeadHistoryRepository
                .findByEmployeeIdOrderByCycleYearAscCycleMonthAscSalaryHeadCodeAsc(employeeId).stream()
                .filter(e -> e.getCycleYear() > cutoff.getYear()
                        || (e.getCycleYear() == cutoff.getYear() && e.getCycleMonth() >= cutoff.getMonthValue()))
                .map(SalaryHistoryEntryResponse::from)
                .toList();
    }
}
