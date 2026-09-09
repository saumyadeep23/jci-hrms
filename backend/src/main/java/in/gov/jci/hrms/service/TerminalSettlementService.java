package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.TerminalSettlementBeneficiaryRequest;
import in.gov.jci.hrms.dto.TerminalSettlementCalculation;
import in.gov.jci.hrms.entity.BankAccountStatus;
import in.gov.jci.hrms.entity.BeneficiaryType;
import in.gov.jci.hrms.entity.CpfBalanceLedger;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeBankAccount;
import in.gov.jci.hrms.entity.EmployeeEmploymentCategory;
import in.gov.jci.hrms.entity.EmployeeNominee;
import in.gov.jci.hrms.entity.ExitClearanceItemStatus;
import in.gov.jci.hrms.entity.ExitClearanceRequest;
import in.gov.jci.hrms.entity.GradeScaleMaster;
import in.gov.jci.hrms.entity.LeaveBalance;
import in.gov.jci.hrms.entity.LeaveEntitlementBalance;
import in.gov.jci.hrms.entity.LeaveLedgerEntry;
import in.gov.jci.hrms.entity.LeaveLedgerSource;
import in.gov.jci.hrms.entity.LeaveType;
import in.gov.jci.hrms.entity.RegularPayFixation;
import in.gov.jci.hrms.entity.ScaleType;
import in.gov.jci.hrms.entity.SeparationType;
import in.gov.jci.hrms.entity.TerminalSettlement;
import in.gov.jci.hrms.entity.TerminalSettlementBeneficiary;
import in.gov.jci.hrms.entity.TerminalSettlementStatus;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.DaRateNotFoundException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.CpfBalanceLedgerRepository;
import in.gov.jci.hrms.repository.DaRateHistoryRepository;
import in.gov.jci.hrms.repository.EmployeeBankAccountRepository;
import in.gov.jci.hrms.repository.EmployeeEmploymentCategoryRepository;
import in.gov.jci.hrms.repository.EmployeeNomineeRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.ExitClearanceItemRepository;
import in.gov.jci.hrms.repository.ExitClearanceRequestRepository;
import in.gov.jci.hrms.repository.LeaveBalanceRepository;
import in.gov.jci.hrms.repository.LeaveEntitlementBalanceRepository;
import in.gov.jci.hrms.repository.LeaveLedgerEntryRepository;
import in.gov.jci.hrms.repository.LeaveTypeRepository;
import in.gov.jci.hrms.repository.RegularPayFixationRepository;
import in.gov.jci.hrms.repository.TerminalSettlementBeneficiaryRepository;
import in.gov.jci.hrms.repository.TerminalSettlementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Optional;

/**
 * Greenfield Terminal Settlement engine: Gratuity Act/CCS(Pension) Rules
 * gratuity slabs, DoPT Rule 39 EL/HPL leave encashment (bypassing
 * LeaveType.isEncashable, which governs ordinary discretionary encashment,
 * not a terminal settlement's statutory entitlement), and CPF Trust ledger
 * payout. preview()/generate() share one calculate() so a previewed figure
 * and a persisted one can never disagree.
 */
@Service
@Transactional(readOnly = true)
public class TerminalSettlementService {

    private static final BigDecimal EL_ANNUAL_CAP_DAYS = new BigDecimal("300.0");
    private static final BigDecimal GRATUITY_CAP_STANDARD = new BigDecimal("2000000.00");
    private static final BigDecimal GRATUITY_CAP_ELEVATED = new BigDecimal("2500000.00");
    private static final BigDecimal GRATUITY_CAP_DA_THRESHOLD = new BigDecimal("50.00");
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal THIRTY = new BigDecimal("30");
    private static final BigDecimal TWENTY_SIX = new BigDecimal("26");
    private static final BigDecimal FIFTEEN = new BigDecimal("15");
    private static final BigDecimal TWO = new BigDecimal("2");
    private static final int MAX_DEATH_GRATUITY_HALF_YEARS = 66;

    private final EmployeeRepository employeeRepository;
    private final RegularPayFixationRepository regularPayFixationRepository;
    private final EmployeeEmploymentCategoryRepository employmentCategoryRepository;
    private final DaRateHistoryRepository daRateHistoryRepository;
    private final PayrollComputationService payrollComputationService;
    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveEntitlementBalanceRepository leaveEntitlementBalanceRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final CpfBalanceLedgerRepository cpfBalanceLedgerRepository;
    private final ExitClearanceItemRepository exitClearanceItemRepository;
    private final ExitClearanceRequestRepository exitClearanceRequestRepository;
    private final EmployeeBankAccountRepository employeeBankAccountRepository;
    private final EmployeeNomineeRepository employeeNomineeRepository;
    private final LeaveLedgerEntryRepository leaveLedgerEntryRepository;
    private final TerminalSettlementRepository terminalSettlementRepository;
    private final TerminalSettlementBeneficiaryRepository beneficiaryRepository;

    public TerminalSettlementService(EmployeeRepository employeeRepository,
                                      RegularPayFixationRepository regularPayFixationRepository,
                                      EmployeeEmploymentCategoryRepository employmentCategoryRepository,
                                      DaRateHistoryRepository daRateHistoryRepository,
                                      PayrollComputationService payrollComputationService,
                                      LeaveTypeRepository leaveTypeRepository,
                                      LeaveEntitlementBalanceRepository leaveEntitlementBalanceRepository,
                                      LeaveBalanceRepository leaveBalanceRepository,
                                      CpfBalanceLedgerRepository cpfBalanceLedgerRepository,
                                      ExitClearanceItemRepository exitClearanceItemRepository,
                                      ExitClearanceRequestRepository exitClearanceRequestRepository,
                                      EmployeeBankAccountRepository employeeBankAccountRepository,
                                      EmployeeNomineeRepository employeeNomineeRepository,
                                      LeaveLedgerEntryRepository leaveLedgerEntryRepository,
                                      TerminalSettlementRepository terminalSettlementRepository,
                                      TerminalSettlementBeneficiaryRepository beneficiaryRepository) {
        this.employeeRepository = employeeRepository;
        this.regularPayFixationRepository = regularPayFixationRepository;
        this.employmentCategoryRepository = employmentCategoryRepository;
        this.daRateHistoryRepository = daRateHistoryRepository;
        this.payrollComputationService = payrollComputationService;
        this.leaveTypeRepository = leaveTypeRepository;
        this.leaveEntitlementBalanceRepository = leaveEntitlementBalanceRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.cpfBalanceLedgerRepository = cpfBalanceLedgerRepository;
        this.exitClearanceItemRepository = exitClearanceItemRepository;
        this.exitClearanceRequestRepository = exitClearanceRequestRepository;
        this.employeeBankAccountRepository = employeeBankAccountRepository;
        this.employeeNomineeRepository = employeeNomineeRepository;
        this.leaveLedgerEntryRepository = leaveLedgerEntryRepository;
        this.terminalSettlementRepository = terminalSettlementRepository;
        this.beneficiaryRepository = beneficiaryRepository;
    }

    /** GET /api/v1/settlements/preview/:employeeId - never persists anything. */
    public TerminalSettlementCalculation preview(Long employeeId, SeparationType separationType, LocalDate separationDate,
                                                  Long clearanceRequestId, BigDecimal cpfAccruedInterestOverride) {
        Employee employee = findEmployeeOrThrow(employeeId);
        return calculate(employee, separationType, separationDate, clearanceRequestId, cpfAccruedInterestOverride);
    }

    /**
     * POST /api/v1/settlements/generate/:employeeId - persists a new DRAFT
     * TerminalSettlement row (always a new row, never an update-in-place -
     * see the entity javadoc) plus its default beneficiary allocation:
     * a single SELF/100% row for a living separation, nothing yet for a
     * DECEASED case (HR adds nominees via addBeneficiaries()).
     */
    @Transactional
    public TerminalSettlement generate(Long employeeId, SeparationType separationType, LocalDate separationDate,
                                        Long clearanceRequestId, BigDecimal cpfAccruedInterestOverride) {
        Employee employee = findEmployeeOrThrow(employeeId);
        TerminalSettlementCalculation c = calculate(employee, separationType, separationDate, clearanceRequestId, cpfAccruedInterestOverride);

        ExitClearanceRequest clearanceRequest = clearanceRequestId != null
                ? exitClearanceRequestRepository.findById(clearanceRequestId)
                        .orElseThrow(() -> new MasterDataNotFoundException("ExitClearanceRequest", clearanceRequestId))
                : null;

        TerminalSettlement settlement = new TerminalSettlement(
                employee, clearanceRequest, c.separationType(), c.separationDate(), c.lastBasicPay(), c.daRatePercentage(),
                c.daAmount(), c.qualifyingServiceYears(), c.qualifyingServiceMonths(), c.elBalanceAtRetirement(),
                c.hplBalanceAtRetirement(), c.elDaysEncashed(), c.hplDaysEncashed(), c.leaveEncashmentElAmount(),
                c.leaveEncashmentHplAmount(), c.totalLeaveEncashment(), c.gratuityAmount(), c.isDeathGratuity(),
                c.cpfEmployeeBalance(), c.cpfEmployerBalance(), c.cpfVpfBalance(), c.cpfAccruedInterest(),
                c.totalCpfPayable(), c.grossTerminalDues(), c.totalRecoveriesDeductions(), c.netTerminalPayable());
        settlement = terminalSettlementRepository.save(settlement);

        if (separationType == SeparationType.DECEASED) {
            seedNomineeBeneficiaries(settlement, employee, c.netTerminalPayable());
            return settlement;
        }

        EmployeeBankAccount account = employeeBankAccountRepository
                .findByEmployeeIdAndPrimaryDisbursalTrueAndStatus(employeeId, BankAccountStatus.ACTIVE)
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "Employee " + employeeId + " has no active, primary-disbursal bank account on file - "
                                + "add one before a terminal settlement can be generated"));

        TerminalSettlementBeneficiary self = new TerminalSettlementBeneficiary(
                settlement, BeneficiaryType.SELF, employee.getFullName(), "SELF",
                HUNDRED.setScale(2, RoundingMode.HALF_UP), c.netTerminalPayable(),
                account.getBankAccountNumber(), account.getBankIfsc(), account.getBankName(), employee.getPanNumber());
        beneficiaryRepository.save(self);

        return settlement;
    }

    /**
     * DECEASED case: seeds one NOMINEE beneficiary per registered
     * employee_nominees row (name/relationship/sharePercentage), with
     * allocatedAmount computed from netPayable - not an approval-ready
     * allocation on its own (bank_account_no/bank_ifsc are left null,
     * V59 having relaxed those columns for exactly this case, and the
     * nominee register carries no bank details at all), just a seed HR
     * completes via replaceBeneficiaries() before approve() will accept it.
     * A share split that doesn't sum to exactly 100% (stale/incomplete
     * nominee data) still seeds as-is - approve()'s own validation is what
     * actually gates this, same as a manually-entered split would be.
     */
    private void seedNomineeBeneficiaries(TerminalSettlement settlement, Employee employee, BigDecimal netPayable) {
        List<EmployeeNominee> nominees = employeeNomineeRepository.findByEmployeeId(employee.getId());
        for (EmployeeNominee nominee : nominees) {
            BigDecimal allocated = netPayable.multiply(nominee.getSharePercentage())
                    .divide(HUNDRED, 2, RoundingMode.HALF_UP);
            beneficiaryRepository.save(new TerminalSettlementBeneficiary(
                    settlement, BeneficiaryType.NOMINEE, nominee.getName(), nominee.getRelationship().name(),
                    nominee.getSharePercentage(), allocated, null, null, null, null));
        }
    }

    public TerminalSettlement getById(Long id) {
        return terminalSettlementRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException("TerminalSettlement", id));
    }

    public List<TerminalSettlementBeneficiary> beneficiaries(Long settlementId) {
        return beneficiaryRepository.findBySettlementId(settlementId);
    }

    /** POST /api/v1/settlements/:id/beneficiaries - replaces the whole list; rejects a split that doesn't sum to 100% share / net payable. */
    @Transactional
    public List<TerminalSettlementBeneficiary> replaceBeneficiaries(Long settlementId, List<TerminalSettlementBeneficiaryRequest> requests) {
        TerminalSettlement settlement = getById(settlementId);
        validateAllocation(requests, settlement.getNetTerminalPayable());

        beneficiaryRepository.deleteBySettlementId(settlementId);
        return requests.stream()
                .map(r -> beneficiaryRepository.save(new TerminalSettlementBeneficiary(
                        settlement, r.beneficiaryType(), r.beneficiaryName(), r.relationship(), r.sharePercentage(),
                        r.allocatedAmount(), r.bankAccountNo(), r.bankIfsc(), r.bankName(), r.panNumber())))
                .toList();
    }

    /**
     * POST /api/v1/settlements/:id/approve - requires a valid (100% share,
     * sum == net payable, every row's bank details on file) beneficiary
     * allocation already on file. Also debits the EL/HPL days this
     * settlement encashed and writes the matching leave_ledger_entries
     * audit rows (source = TERMINAL_ENCASHMENT) - the one point where this
     * settlement actually touches live leave balances, so it must never
     * run twice for the same settlement.
     */
    @Transactional
    public TerminalSettlement approve(Long settlementId) {
        TerminalSettlement settlement = getById(settlementId);
        if (settlement.getStatus() == TerminalSettlementStatus.APPROVED || settlement.getStatus() == TerminalSettlementStatus.DISBURSED) {
            throw new BusinessRuleViolationException("Settlement " + settlementId + " is already " + settlement.getStatus());
        }
        List<TerminalSettlementBeneficiary> existing = beneficiaryRepository.findBySettlementId(settlementId);
        if (existing.isEmpty()) {
            throw new BusinessRuleViolationException("Settlement " + settlementId + " has no beneficiary allocation on file");
        }
        validateShareAndAmountSums(
                existing.stream().map(TerminalSettlementBeneficiary::getSharePercentage).toList(),
                existing.stream().map(TerminalSettlementBeneficiary::getAllocatedAmount).toList(),
                settlement.getNetTerminalPayable());
        for (TerminalSettlementBeneficiary beneficiary : existing) {
            if (isBlank(beneficiary.getBankAccountNo()) || isBlank(beneficiary.getBankIfsc())) {
                throw new BusinessRuleViolationException(
                        "Beneficiary \"" + beneficiary.getBeneficiaryName() + "\" is missing bank account/IFSC details");
            }
        }

        debitEncashedLeave(settlement);

        settlement.setStatus(TerminalSettlementStatus.APPROVED);
        return settlement;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Debits exactly what this settlement already computed and froze (elDaysEncashed/hplDaysEncashed) - never recomputed against the live balance, so a balance that moved between generate() and approve() can't silently change what gets debited. */
    private void debitEncashedLeave(TerminalSettlement settlement) {
        Long employeeId = settlement.getEmployee().getId();
        int year = settlement.getSeparationDate().getYear();

        if (settlement.getElDaysEncashed().compareTo(BigDecimal.ZERO) > 0) {
            LeaveType elType = leaveTypeRepository.findByCode("EL")
                    .orElseThrow(() -> new MasterDataNotFoundException("LeaveType", "EL"));
            LeaveEntitlementBalance balance = leaveEntitlementBalanceRepository
                    .findByEmployeeIdAndLeaveTypeIdAndYear(employeeId, elType.getId(), year)
                    .orElseThrow(() -> new BusinessRuleViolationException(
                            "Employee " + employeeId + " has no EL entitlement balance for " + year + " to debit against"));
            BigDecimal amount = settlement.getElDaysEncashed();
            // Mirrors LeaveEncashmentService.finalizeEncashment()'s exact field set for an EL
            // encashment debit, minus the encashableReserved decrement - that field only applies
            // to that service's own reserve-then-finalize application workflow, which a terminal
            // settlement never goes through.
            balance.setEncashableCurrent(balance.getEncashableCurrent().subtract(amount));
            balance.setEncashableEncashed(balance.getEncashableEncashed().add(amount));
            balance.setEncashedDays(balance.getEncashedDays().add(amount));
            balance.setCurrentBalance(balance.getCurrentBalance().subtract(amount));
            leaveEntitlementBalanceRepository.save(balance);

            leaveLedgerEntryRepository.save(new LeaveLedgerEntry(settlement.getEmployee(), elType, settlement.getSeparationDate(),
                    amount.negate(), amount + " EL days debited - terminal settlement #" + settlement.getId(),
                    LeaveLedgerSource.TERMINAL_ENCASHMENT));
        }

        if (settlement.getHplDaysEncashed().compareTo(BigDecimal.ZERO) > 0) {
            LeaveType hplType = leaveTypeRepository.findByCode("HPL")
                    .orElseThrow(() -> new MasterDataNotFoundException("LeaveType", "HPL"));
            LeaveBalance balance = leaveBalanceRepository
                    .findByEmployeeIdAndLeaveTypeIdAndYear(employeeId, hplType.getId(), year)
                    .orElseThrow(() -> new BusinessRuleViolationException(
                            "Employee " + employeeId + " has no HPL balance for " + year + " to debit against"));
            BigDecimal amount = settlement.getHplDaysEncashed();
            balance.setUsedDays(balance.getUsedDays().add(amount));
            leaveBalanceRepository.save(balance);

            leaveLedgerEntryRepository.save(new LeaveLedgerEntry(settlement.getEmployee(), hplType, settlement.getSeparationDate(),
                    amount.negate(), amount + " HPL days debited (DoPT Rule 39 shortfall top-up) - terminal settlement #" + settlement.getId(),
                    LeaveLedgerSource.TERMINAL_ENCASHMENT));
        }
    }

    private void validateAllocation(List<TerminalSettlementBeneficiaryRequest> requests, BigDecimal netPayable) {
        validateShareAndAmountSums(
                requests.stream().map(TerminalSettlementBeneficiaryRequest::sharePercentage).toList(),
                requests.stream().map(TerminalSettlementBeneficiaryRequest::allocatedAmount).toList(),
                netPayable);
    }

    private void validateShareAndAmountSums(List<BigDecimal> shares, List<BigDecimal> amounts, BigDecimal netPayable) {
        BigDecimal shareSum = shares.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (shareSum.setScale(2, RoundingMode.HALF_UP).compareTo(HUNDRED.setScale(2, RoundingMode.HALF_UP)) != 0) {
            throw new BusinessRuleViolationException("Beneficiary share percentages must sum to exactly 100.00, got " + shareSum);
        }
        BigDecimal amountSum = amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (amountSum.setScale(2, RoundingMode.HALF_UP).compareTo(netPayable.setScale(2, RoundingMode.HALF_UP)) != 0) {
            throw new BusinessRuleViolationException(
                    "Beneficiary allocated amounts must sum to the net terminal payable (" + netPayable + "), got " + amountSum);
        }
    }

    // -------------------------------------------------------------------
    // Calculation core
    // -------------------------------------------------------------------

    private TerminalSettlementCalculation calculate(Employee employee, SeparationType separationType, LocalDate separationDate,
                                                      Long clearanceRequestId, BigDecimal cpfAccruedInterestOverride) {
        Long employeeId = employee.getId();

        EmoulmentsResult emoluments = resolveEmoluments(employee, separationDate);
        BigDecimal monthlyEmoluments = emoluments.basicPay().add(emoluments.daAmount());

        Period period = Period.between(employee.getDateOfJoining(), separationDate);
        int years = period.getYears();
        int months = period.getMonths();
        int roundedYears = months >= 6 ? years + 1 : years;

        boolean isDeathGratuity = separationType == SeparationType.DECEASED;
        BigDecimal gratuity = isDeathGratuity
                ? computeDeathGratuity(monthlyEmoluments, years, months)
                : computeRetirementGratuity(monthlyEmoluments, roundedYears);
        BigDecimal gratuityCap = emoluments.daPercentage().compareTo(GRATUITY_CAP_DA_THRESHOLD) >= 0
                ? GRATUITY_CAP_ELEVATED : GRATUITY_CAP_STANDARD;
        gratuity = gratuity.min(gratuityCap).setScale(2, RoundingMode.HALF_UP);

        LeaveEncashmentResult leave = resolveLeaveEncashment(employeeId, separationDate.getYear(), emoluments, monthlyEmoluments);

        CpfResult cpf = resolveCpf(employeeId, cpfAccruedInterestOverride);

        BigDecimal duesRecovery = clearanceRequestId != null
                ? coalesce(exitClearanceItemRepository.sumDuesRecoveryForClearanceRequest(clearanceRequestId, ExitClearanceItemStatus.REJECTED_WITH_DUES))
                : BigDecimal.ZERO;

        BigDecimal gross = gratuity.add(leave.total()).add(cpf.totalPayable()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal net = gross.subtract(duesRecovery).setScale(2, RoundingMode.HALF_UP);

        return new TerminalSettlementCalculation(
                employeeId, separationType, separationDate, emoluments.basicPay(), emoluments.daPercentage(), emoluments.daAmount(),
                monthlyEmoluments, years, months, roundedYears,
                leave.elBalance(), leave.hplBalance(), leave.elDaysEncashed(), leave.hplDaysEncashed(),
                leave.elCash(), leave.hplCash(), leave.total(),
                gratuity, isDeathGratuity, gratuityCap,
                cpf.employeeBalance(), cpf.employerBalance(), cpf.vpfBalance(), cpf.accruedInterest(), cpf.totalPayable(),
                gross, duesRecovery, net);
    }

    private record EmoulmentsResult(BigDecimal basicPay, BigDecimal daPercentage, BigDecimal daAmount) {
    }

    /**
     * Basic pay: the fixation whose is_current = true, or the one closed
     * out with effectiveTo = separationDate (EmployeeReleaseService may
     * already have run) - falling back to
     * employee_employment_categories.regular_basic_pay only if neither
     * exists, same COALESCE precedence as Manpower4TierReportService/
     * IncrementProcessingService. DA rate's ScaleType comes from the
     * fixation's own grade scale when available, else IDA (GradeScaleMaster's
     * own default) - V60 dropped the old employee.getPayScale()/
     * pay_scale_master fallback that used to sit between those two; an
     * employee with no current fixation and no scale to resolve at all
     * falls straight to IDA now, a last-resort default, not a confirmed
     * policy.
     */
    private EmoulmentsResult resolveEmoluments(Employee employee, LocalDate separationDate) {
        List<RegularPayFixation> applicable = regularPayFixationRepository.findApplicableForSettlement(employee.getId(), separationDate);
        RegularPayFixation fixation = applicable.isEmpty() ? null : applicable.get(0);

        BigDecimal basicPay;
        ScaleType scaleType;
        if (fixation != null) {
            basicPay = fixation.getBasicPay();
            scaleType = fixation.getGradeScale() != null ? fixation.getGradeScale().getScaleType() : null;
        } else {
            EmployeeEmploymentCategory category = employmentCategoryRepository.findByEmployeeId(employee.getId())
                    .orElseThrow(() -> new MasterDataNotFoundException("EmployeeEmploymentCategory", employee.getId()));
            basicPay = category.getRegularBasicPay();
            scaleType = null;
        }
        if (basicPay == null) {
            throw new BusinessRuleViolationException("Employee " + employee.getId() + " has no basic pay on record to base a terminal settlement on");
        }
        ScaleType resolvedScaleType = scaleType != null ? scaleType : ScaleType.IDA;

        BigDecimal daPercentage = daRateHistoryRepository
                .findTopByScaleTypeAndActiveTrueAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(resolvedScaleType, separationDate)
                .map(rate -> rate.getDaPercentage())
                .orElseThrow(() -> new DaRateNotFoundException(resolvedScaleType, separationDate));
        BigDecimal daAmount = payrollComputationService.computeDearnessAllowance(basicPay, daPercentage);

        return new EmoulmentsResult(basicPay, daPercentage, daAmount);
    }

    /**
     * CCS (Pension) Rule 45 death gratuity slabs (qualifying service in
     * completed years+months, not the increment-style >=6-months-rounds-up
     * figure used for retirement gratuity below - the slabs themselves are
     * threshold cutoffs, not a rounding rule).
     */
    private BigDecimal computeDeathGratuity(BigDecimal monthlyEmoluments, int years, int months) {
        int totalMonths = years * 12 + months;
        if (totalMonths < 12) {
            return monthlyEmoluments.multiply(TWO);
        }
        if (totalMonths < 60) {
            return monthlyEmoluments.multiply(new BigDecimal("6"));
        }
        if (totalMonths < 132) {
            return monthlyEmoluments.multiply(new BigDecimal("12"));
        }
        if (totalMonths < 240) {
            return monthlyEmoluments.multiply(new BigDecimal("20"));
        }
        int halfYears = Math.min(totalMonths / 6, MAX_DEATH_GRATUITY_HALF_YEARS);
        BigDecimal gratuity = monthlyEmoluments.divide(TWO, 10, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(halfYears));
        return gratuity.min(monthlyEmoluments.multiply(new BigDecimal("33")));
    }

    /** Retirement/resignation/VRS gratuity: 15 days' emoluments (basic+DA, a 26-day month) per completed (>=6-months-rounds-up) year of qualifying service. */
    private BigDecimal computeRetirementGratuity(BigDecimal monthlyEmoluments, int roundedYears) {
        return monthlyEmoluments.divide(TWENTY_SIX, 10, RoundingMode.HALF_UP)
                .multiply(FIFTEEN)
                .multiply(BigDecimal.valueOf(roundedYears));
    }

    private record LeaveEncashmentResult(BigDecimal elBalance, BigDecimal hplBalance, BigDecimal elDaysEncashed,
                                          BigDecimal hplDaysEncashed, BigDecimal elCash, BigDecimal hplCash, BigDecimal total) {
    }

    /**
     * DoPT Rule 39: EL encashment up to 300 days; any shortfall below 300
     * is topped up from HPL (at half the ordinary rate, since HPL itself is
     * a half-pay leave). This applies at terminal settlement regardless of
     * LeaveType.HPL.isEncashable being false in this dataset - that flag
     * governs the ordinary discretionary LeaveEncashmentApplication
     * workflow, not this statutory entitlement.
     */
    private LeaveEncashmentResult resolveLeaveEncashment(Long employeeId, int year, EmoulmentsResult emoluments, BigDecimal monthlyEmoluments) {
        LeaveType elType = leaveTypeRepository.findByCode("EL")
                .orElseThrow(() -> new MasterDataNotFoundException("LeaveType", "EL"));
        LeaveType hplType = leaveTypeRepository.findByCode("HPL")
                .orElseThrow(() -> new MasterDataNotFoundException("LeaveType", "HPL"));

        // Reads the encashable sub-ledger specifically (not the overall
        // getAvailableBalance()), matching LeaveEncashmentService.
        // finalizeEncashment()'s exact precedent for what an EL encashment
        // draws against - approve() below debits the same fields.
        BigDecimal elBalance = leaveEntitlementBalanceRepository
                .findByEmployeeIdAndLeaveTypeIdAndYear(employeeId, elType.getId(), year)
                .map(LeaveEntitlementBalance::getEncashableAvailable)
                .orElse(BigDecimal.ZERO);
        BigDecimal hplBalance = leaveBalanceRepository
                .findByEmployeeIdAndLeaveTypeIdAndYear(employeeId, hplType.getId(), year)
                .map(LeaveBalance::getAvailableDays)
                .orElse(BigDecimal.ZERO);

        BigDecimal elDaysEncashed = elBalance.min(EL_ANNUAL_CAP_DAYS);
        BigDecimal shortfall = EL_ANNUAL_CAP_DAYS.subtract(elDaysEncashed);
        BigDecimal hplDaysEncashed = hplBalance.min(shortfall);

        BigDecimal elCash = monthlyEmoluments.divide(THIRTY, 10, RoundingMode.HALF_UP)
                .multiply(elDaysEncashed).setScale(2, RoundingMode.HALF_UP);
        BigDecimal halfEmoluments = emoluments.basicPay().divide(TWO, 10, RoundingMode.HALF_UP)
                .add(emoluments.daAmount().divide(TWO, 10, RoundingMode.HALF_UP));
        BigDecimal hplCash = halfEmoluments.divide(THIRTY, 10, RoundingMode.HALF_UP)
                .multiply(hplDaysEncashed).setScale(2, RoundingMode.HALF_UP);

        return new LeaveEncashmentResult(elBalance, hplBalance, elDaysEncashed, hplDaysEncashed, elCash, hplCash, elCash.add(hplCash));
    }

    private record CpfResult(BigDecimal employeeBalance, BigDecimal employerBalance, BigDecimal vpfBalance,
                              BigDecimal accruedInterest, BigDecimal totalPayable) {
    }

    private CpfResult resolveCpf(Long employeeId, BigDecimal accruedInterestOverride) {
        Optional<CpfBalanceLedger> ledger = cpfBalanceLedgerRepository.findByEmployeeId(employeeId);
        BigDecimal employeeBalance = ledger.map(CpfBalanceLedger::getEmployeeFundBalance).orElse(BigDecimal.ZERO);
        BigDecimal employerBalance = ledger.map(CpfBalanceLedger::getEmployerFundBalance).orElse(BigDecimal.ZERO);
        BigDecimal vpfBalance = ledger.map(CpfBalanceLedger::getVpfBalance).orElse(BigDecimal.ZERO);
        BigDecimal accruedInterest = accruedInterestOverride != null ? accruedInterestOverride : BigDecimal.ZERO;
        BigDecimal total = employeeBalance.add(employerBalance).add(vpfBalance).add(accruedInterest).setScale(2, RoundingMode.HALF_UP);
        return new CpfResult(employeeBalance, employerBalance, vpfBalance, accruedInterest, total);
    }

    private static BigDecimal coalesce(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private Employee findEmployeeOrThrow(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new MasterDataNotFoundException("Employee", employeeId));
    }
}
