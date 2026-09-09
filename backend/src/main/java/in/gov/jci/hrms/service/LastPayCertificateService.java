package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.CityClass;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeEmploymentCategory;
import in.gov.jci.hrms.entity.EmployeeLoan;
import in.gov.jci.hrms.entity.EmployeeMovementRecord;
import in.gov.jci.hrms.entity.LeaveBalance;
import in.gov.jci.hrms.entity.LeaveEntitlementBalance;
import in.gov.jci.hrms.entity.LeaveType;
import in.gov.jci.hrms.entity.LoanStatus;
import in.gov.jci.hrms.entity.LoanTypeCode;
import in.gov.jci.hrms.entity.MovementLpcRecord;
import in.gov.jci.hrms.entity.RegularPayFixation;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.EmployeeEmploymentCategoryRepository;
import in.gov.jci.hrms.repository.EmployeeLoanRepository;
import in.gov.jci.hrms.repository.EmployeeMovementRecordRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.LeaveBalanceRepository;
import in.gov.jci.hrms.repository.LeaveEntitlementBalanceRepository;
import in.gov.jci.hrms.repository.LeaveTypeRepository;
import in.gov.jci.hrms.repository.MovementLpcRecordRepository;
import in.gov.jci.hrms.repository.RegularPayFixationRepository;
import in.gov.jci.hrms.service.pdf.LastPayCertificatePdfGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

/**
 * Computes and persists the line-item figures behind one movement's Last Pay Certificate,
 * reusing PayrollComputationService's own DA/HRA/EPF formulas rather than duplicating them.
 * Generation is idempotent (getOrCreate) - once issued, an LPC's figures are frozen (the
 * uq_movement_lpc_records_movement_id constraint backs this), re-printable exactly as originally
 * certified regardless of how the employee's live payroll/leave figures change afterward.
 */
@Service
@Transactional(readOnly = true)
public class LastPayCertificateService {

    private static final String EL_CODE = "EL";
    private static final String HPL_CODE = "HPL";
    private static final Set<LoanStatus> OUTSTANDING_STATUSES = Set.of(LoanStatus.SANCTIONED, LoanStatus.DISBURSED, LoanStatus.ACTIVE);

    private final MovementLpcRecordRepository lpcRecordRepository;
    private final EmployeeMovementRecordRepository movementRecordRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeEmploymentCategoryRepository employmentCategoryRepository;
    private final EmployeeLoanRepository employeeLoanRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveEntitlementBalanceRepository entitlementBalanceRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final PayrollComputationService payrollComputationService;
    private final RegularPayFixationRepository regularPayFixationRepository;
    private final LastPayCertificatePdfGenerator pdfGenerator;

    public LastPayCertificateService(MovementLpcRecordRepository lpcRecordRepository,
                                      EmployeeMovementRecordRepository movementRecordRepository, EmployeeRepository employeeRepository,
                                      EmployeeEmploymentCategoryRepository employmentCategoryRepository,
                                      EmployeeLoanRepository employeeLoanRepository, LeaveTypeRepository leaveTypeRepository,
                                      LeaveEntitlementBalanceRepository entitlementBalanceRepository,
                                      LeaveBalanceRepository leaveBalanceRepository, PayrollComputationService payrollComputationService,
                                      RegularPayFixationRepository regularPayFixationRepository,
                                      LastPayCertificatePdfGenerator pdfGenerator) {
        this.lpcRecordRepository = lpcRecordRepository;
        this.movementRecordRepository = movementRecordRepository;
        this.employeeRepository = employeeRepository;
        this.employmentCategoryRepository = employmentCategoryRepository;
        this.employeeLoanRepository = employeeLoanRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.entitlementBalanceRepository = entitlementBalanceRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.payrollComputationService = payrollComputationService;
        this.regularPayFixationRepository = regularPayFixationRepository;
        this.pdfGenerator = pdfGenerator;
    }

    /** Idempotently generates (or reuses) the movement's LPC record, then renders it to PDF. */
    @Transactional
    public byte[] generatePdf(Long movementId, Long generatedByEmployeeId) {
        return pdfGenerator.generate(getOrCreate(movementId, generatedByEmployeeId));
    }

    /** ESS: downloads an already-generated LPC for the caller's own record - never triggers first-time generation, that stays an HR/Finance action (generatePdf()). */
    public byte[] generatePdfForEmployee(Long movementId, Long callerEmployeeId) {
        MovementLpcRecord lpc = lpcRecordRepository.findByMovementId(movementId)
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "No Last Pay Certificate has been generated yet for this movement - please contact HR/Finance"));
        if (callerEmployeeId != null && !callerEmployeeId.equals(lpc.getEmployee().getId())) {
            throw new BusinessRuleViolationException("You may only download your own Last Pay Certificate");
        }
        return pdfGenerator.generate(lpc);
    }

    /** Returns the existing certificate if one was already generated for this movement, otherwise computes and persists a new one. */
    @Transactional
    public MovementLpcRecord getOrCreate(Long movementId, Long generatedByEmployeeId) {
        return lpcRecordRepository.findByMovementId(movementId)
                .orElseGet(() -> create(movementId, generatedByEmployeeId));
    }

    private MovementLpcRecord create(Long movementId, Long generatedByEmployeeId) {
        EmployeeMovementRecord movement = movementRecordRepository.findById(movementId)
                .orElseThrow(() -> new MasterDataNotFoundException("Movement Record", movementId));
        if (movement.getReleaseDate() == null || movement.getReleaseSession() == null) {
            throw new BusinessRuleViolationException("Movement " + movementId + " has not been released yet - cannot generate an LPC");
        }
        Employee employee = movement.getEmployee();

        BigDecimal basicPay = currentBasicPay(employee);
        // V60: employee.getPayScale()/pay_scale_master is gone - resolve via the employee's
        // current regular_pay_fixations row instead, same graceful "no fixation -> DA 0" fallback.
        var scaleType = regularPayFixationRepository.findByEmployeeIdAndCurrentTrue(employee.getId())
                .map(RegularPayFixation::getGradeScale)
                .map(gradeScale -> gradeScale.getScaleType())
                .orElse(null);
        BigDecimal daPercentage = scaleType != null
                ? payrollComputationService.resolveDaPercentage(scaleType, movement.getReleaseDate())
                : BigDecimal.ZERO;
        BigDecimal da = payrollComputationService.computeDearnessAllowance(basicPay, daPercentage);
        CityClass releasingCityClass = movement.getFromOffice().getCityClass();
        BigDecimal hra = payrollComputationService.computeHouseRentAllowance(basicPay, da, releasingCityClass);
        BigDecimal cpfSubscription = payrollComputationService.computeEpfEps(basicPay, da).employeeEpf();

        int year = movement.getReleaseDate().getYear();
        int elBalance = elBalanceDays(employee.getId(), year);
        int hplBalance = hplBalanceDays(employee.getId(), year);

        BigDecimal cpfAdvanceBalance = outstandingLoanBalance(employee.getId(), LoanTypeCode.CPF_SECURED);
        BigDecimal festivalAdvanceBalance = outstandingLoanBalance(employee.getId(), LoanTypeCode.FESTIVAL);

        String lpcNumber = "LPC/" + movement.getId() + "/" + year;
        MovementLpcRecord lpc = new MovementLpcRecord(movement, employee, lpcNumber, basicPay, da, hra, cpfSubscription,
                elBalance, hplBalance, movement.getReleaseDate(), movement.getReleaseSession());
        lpc.setCpfAdvanceBalance(cpfAdvanceBalance);
        lpc.setFestivalAdvanceBalance(festivalAdvanceBalance);

        Employee signatory = generatedByEmployeeId != null ? employeeRepository.findById(generatedByEmployeeId).orElse(null) : null;
        lpc.setGeneratedBy(signatory);

        MovementLpcRecord saved = lpcRecordRepository.saveAndFlush(lpc);

        movement.setLpcIssueDate(LocalDate.now(ZoneId.of("Asia/Kolkata")));
        movement.setLpcSignatoryName(signatory != null ? signatory.getFullName() : null);
        movement.setLpcSignatoryDesignation(signatory != null && signatory.getDesignation() != null
                ? signatory.getDesignation().getTitle() : null);

        return saved;
    }

    private BigDecimal currentBasicPay(Employee employee) {
        return employmentCategoryRepository.findByEmployeeId(employee.getId())
                .map(EmployeeEmploymentCategory::getRegularBasicPay)
                .orElse(BigDecimal.ZERO);
    }

    private int elBalanceDays(Long employeeId, int year) {
        LeaveType el = leaveTypeRepository.findByCode(EL_CODE).orElse(null);
        if (el == null) {
            return 0;
        }
        return entitlementBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(employeeId, el.getId(), year)
                .map(LeaveEntitlementBalance::getCurrentBalance)
                .map(BigDecimal::intValue)
                .orElse(0);
    }

    private int hplBalanceDays(Long employeeId, int year) {
        LeaveType hpl = leaveTypeRepository.findByCode(HPL_CODE).orElse(null);
        if (hpl == null) {
            return 0;
        }
        return leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(employeeId, hpl.getId(), year)
                .map(this::availableBalance)
                .map(BigDecimal::intValue)
                .orElse(0);
    }

    private BigDecimal availableBalance(LeaveBalance balance) {
        return balance.getCreditedDays().subtract(balance.getUsedDays()).subtract(balance.getReservedDays())
                .setScale(0, RoundingMode.HALF_UP);
    }

    private BigDecimal outstandingLoanBalance(Long employeeId, LoanTypeCode code) {
        List<EmployeeLoan> loans = employeeLoanRepository.findByEmployeeId(employeeId);
        return loans.stream()
                .filter(loan -> loan.getLoanType().getCode() == code)
                .filter(loan -> OUTSTANDING_STATUSES.contains(loan.getStatus()))
                .map(EmployeeLoan::getOutstandingPrincipal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
