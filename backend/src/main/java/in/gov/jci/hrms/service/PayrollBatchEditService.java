package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.EditPayrollLineDto;
import in.gov.jci.hrms.dto.PayrollEditResponse;
import in.gov.jci.hrms.dto.PayrollHeadLineResponse;
import in.gov.jci.hrms.entity.CityClass;
import in.gov.jci.hrms.entity.DaRateHistory;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.PayrollBatch;
import in.gov.jci.hrms.entity.PayrollBatchStatus;
import in.gov.jci.hrms.entity.PayrollEditLog;
import in.gov.jci.hrms.entity.PayrollHraRate;
import in.gov.jci.hrms.entity.PayrollMonthlyHeadItem;
import in.gov.jci.hrms.entity.PayrollMonthlyRecord;
import in.gov.jci.hrms.entity.PayrollMonthlyStatutoryItem;
import in.gov.jci.hrms.entity.PayrollStatutoryParameter;
import in.gov.jci.hrms.entity.PtaxSlab;
import in.gov.jci.hrms.entity.RegionalOffice;
import in.gov.jci.hrms.entity.RegularPayFixation;
import in.gov.jci.hrms.entity.SalaryHead;
import in.gov.jci.hrms.entity.SalaryHeadEffectType;
import in.gov.jci.hrms.entity.ScaleType;
import in.gov.jci.hrms.entity.StatutoryHead;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.DaRateHistoryRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.PayrollBatchRepository;
import in.gov.jci.hrms.repository.PayrollEditLogRepository;
import in.gov.jci.hrms.repository.PayrollHraRateRepository;
import in.gov.jci.hrms.repository.PayrollMonthlyHeadItemRepository;
import in.gov.jci.hrms.repository.PayrollMonthlyRecordRepository;
import in.gov.jci.hrms.repository.PayrollMonthlyStatutoryItemRepository;
import in.gov.jci.hrms.repository.PayrollStatutoryParameterRepository;
import in.gov.jci.hrms.repository.PtaxSlabRepository;
import in.gov.jci.hrms.repository.RegularPayFixationRepository;
import in.gov.jci.hrms.repository.SalaryHeadRepository;
import in.gov.jci.hrms.repository.StateMasterRepository;
import in.gov.jci.hrms.repository.StatutoryHeadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Manual reconciliation-sheet edits against a still-open (DRAFT/CALCULATED) PayrollMonthlyRecord -
 * editing BASIC (Head 1) cascades to DA/HRA/CPF(27)/EPF(stat 1)/Pension(stat 4)/JCPF(stat 3)/P.Tax(49);
 * editing CPF (Head 27) directly cascades to EPF(stat 1)/Pension(stat 4)/JCPF(stat 3) the same way -
 * mirroring the same formulas PayrollBatchComputationService.compute() uses (those are private there,
 * so reimplemented here against the same masters - DaRateHistory/PayrollHraRate/PtaxSlab/
 * PayrollStatutoryParameter - rather than risking a refactor of that already-tested engine). Per
 * resolveEmployerContributions()'s own javadoc there: EPF (stat head 1) always mirrors Head 27
 * unconditionally, Pension (stat head 4) is the EPS carve-out gated on isEpsEligible(), and JCPF (stat
 * head 3) is unconditionally EPF minus Pension. Every changed head, cascaded or not, is written to
 * payroll_edit_logs (V78) with the old/new amount for audit. All persisted amounts - cascaded or
 * directly typed by the officer - are rounded to whole rupees (round()), matching the compute engine's
 * own HALF_UP-to-integer rule; nothing here or in the payslip carries paise.
 */
@Service
@Transactional(readOnly = true)
public class PayrollBatchEditService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal DEFAULT_CPF_EMPLOYEE_RATE = new BigDecimal("12.00");
    private static final BigDecimal DEFAULT_EPS_BASE_RATE = new BigDecimal("8.33");
    private static final BigDecimal DEFAULT_EPS_WAGE_CEILING = new BigDecimal("15000.00");
    private static final BigDecimal DEFAULT_EPS_HIGHER_EXTRA_RATE = new BigDecimal("1.16");
    private static final String CASCADE_REASON = "Cascaded from Basic Pay modification";
    private static final String CPF_CASCADE_REASON = "Cascaded from CPF modification";

    private static final int HEAD_BASIC = 1;
    private static final int HEAD_DA_CDA = 7;
    private static final int HEAD_DA_IDA = 8;
    private static final int HEAD_HRA = 9;
    private static final int HEAD_CPF = 27;
    private static final int HEAD_PTAX = 49;
    private static final int STAT_HEAD_EMPLOYER_EPF = 1;
    private static final int STAT_HEAD_EMPLOYER_JCPF = 3;
    private static final int STAT_HEAD_EMPLOYER_PENSION = 4;

    private final PayrollBatchRepository payrollBatchRepository;
    private final PayrollMonthlyRecordRepository recordRepository;
    private final PayrollMonthlyHeadItemRepository headItemRepository;
    private final PayrollMonthlyStatutoryItemRepository statutoryItemRepository;
    private final PayrollEditLogRepository editLogRepository;
    private final SalaryHeadRepository salaryHeadRepository;
    private final StatutoryHeadRepository statutoryHeadRepository;
    private final RegularPayFixationRepository regularPayFixationRepository;
    private final DaRateHistoryRepository daRateHistoryRepository;
    private final PayrollHraRateRepository payrollHraRateRepository;
    private final PtaxSlabRepository ptaxSlabRepository;
    private final StateMasterRepository stateMasterRepository;
    private final PayrollStatutoryParameterRepository payrollStatutoryParameterRepository;
    private final EmployeeRepository employeeRepository;

    public PayrollBatchEditService(PayrollBatchRepository payrollBatchRepository,
                                    PayrollMonthlyRecordRepository recordRepository,
                                    PayrollMonthlyHeadItemRepository headItemRepository,
                                    PayrollMonthlyStatutoryItemRepository statutoryItemRepository,
                                    PayrollEditLogRepository editLogRepository,
                                    SalaryHeadRepository salaryHeadRepository,
                                    StatutoryHeadRepository statutoryHeadRepository,
                                    RegularPayFixationRepository regularPayFixationRepository,
                                    DaRateHistoryRepository daRateHistoryRepository,
                                    PayrollHraRateRepository payrollHraRateRepository,
                                    PtaxSlabRepository ptaxSlabRepository,
                                    StateMasterRepository stateMasterRepository,
                                    PayrollStatutoryParameterRepository payrollStatutoryParameterRepository,
                                    EmployeeRepository employeeRepository) {
        this.payrollBatchRepository = payrollBatchRepository;
        this.recordRepository = recordRepository;
        this.headItemRepository = headItemRepository;
        this.statutoryItemRepository = statutoryItemRepository;
        this.editLogRepository = editLogRepository;
        this.salaryHeadRepository = salaryHeadRepository;
        this.statutoryHeadRepository = statutoryHeadRepository;
        this.regularPayFixationRepository = regularPayFixationRepository;
        this.daRateHistoryRepository = daRateHistoryRepository;
        this.payrollHraRateRepository = payrollHraRateRepository;
        this.ptaxSlabRepository = ptaxSlabRepository;
        this.stateMasterRepository = stateMasterRepository;
        this.payrollStatutoryParameterRepository = payrollStatutoryParameterRepository;
        this.employeeRepository = employeeRepository;
    }

    /** Pure computation, no persistence - powers the frontend's live cascade preview before the officer confirms. */
    public PayrollEditResponse previewSalaryHead(Long batchId, Long tranId, EditPayrollLineDto dto) {
        PayrollMonthlyRecord record = loadRecordInBatch(batchId, tranId);
        Cascade cascade = computeCascade(record, dto.headCount(), dto.newAmount());
        return toResponse(record, cascade);
    }

    @Transactional
    public PayrollEditResponse editSalaryHead(Long batchId, Long tranId, EditPayrollLineDto dto, Long officerId) {
        PayrollBatch batch = payrollBatchRepository.findById(batchId)
                .orElseThrow(() -> new MasterDataNotFoundException("Payroll Batch", batchId));
        if (batch.getStatus() != PayrollBatchStatus.DRAFT && batch.getStatus() != PayrollBatchStatus.CALCULATED) {
            throw new BusinessRuleViolationException(
                    "Payroll batch " + batchId + " is " + batch.getStatus() + " - edits are locked once finalized");
        }
        PayrollMonthlyRecord record = loadRecordInBatch(batchId, tranId);
        Cascade cascade = computeCascade(record, dto.headCount(), dto.newAmount());

        Employee editedBy = officerId != null ? employeeRepository.findById(officerId).orElse(null) : null;
        String cascadeReason = dto.headCount() == HEAD_BASIC ? CASCADE_REASON : CPF_CASCADE_REASON;
        for (LineChange change : cascade.changes()) {
            persistHeadAmount(record, change.headCount(), change.newAmount(), change.statutory());
            String reason = change.headCount() == dto.headCount() && !change.statutory() ? dto.changeReason() : cascadeReason;
            editLogRepository.save(new PayrollEditLog(batch, record, record.getEmployee(), change.headCount(),
                    change.oldAmount(), change.newAmount(), reason, editedBy));
        }

        record.setBasicPay(cascade.newBasicPay());
        record.setGrossAmount(cascade.grossAmount());
        record.setTotalDeductions(cascade.totalDeductions());
        record.setNetAmount(cascade.netAmount());
        recordRepository.saveAndFlush(record);

        return toResponse(record, cascade);
    }

    private PayrollMonthlyRecord loadRecordInBatch(Long batchId, Long tranId) {
        PayrollMonthlyRecord record = recordRepository.findById(tranId)
                .orElseThrow(() -> new MasterDataNotFoundException("Payroll Monthly Record", tranId));
        if (!record.getBatch().getId().equals(batchId)) {
            throw new BusinessRuleViolationException("Record " + tranId + " does not belong to batch " + batchId);
        }
        return record;
    }

    private record LineChange(int headCount, boolean statutory, BigDecimal oldAmount, BigDecimal newAmount) {
    }

    private record Cascade(List<LineChange> changes, BigDecimal newBasicPay, BigDecimal grossAmount,
                            BigDecimal totalDeductions, BigDecimal netAmount) {
    }

    private Cascade computeCascade(PayrollMonthlyRecord record, int headCount, BigDecimal newAmount) {
        Employee employee = record.getEmployee();
        Map<Integer, SalaryHead> catalog = salaryHeadRepository.findAllByOrderByHeadCountAsc().stream()
                .collect(Collectors.toMap(SalaryHead::getHeadCount, h -> h));
        Map<Integer, BigDecimal> currentHeads = headItemRepository.findByRecord_TranId(record.getTranId()).stream()
                .collect(Collectors.toMap(PayrollMonthlyHeadItem::getHeadCount, PayrollMonthlyHeadItem::getAmount));
        Map<Integer, BigDecimal> currentStatItems = statutoryItemRepository.findByRecord_TranId(record.getTranId()).stream()
                .collect(Collectors.toMap(PayrollMonthlyStatutoryItem::getStatHeadCount, PayrollMonthlyStatutoryItem::getAmount));
        BigDecimal currentEpf = currentStatItems.getOrDefault(STAT_HEAD_EMPLOYER_EPF, BigDecimal.ZERO);
        BigDecimal currentPension = currentStatItems.getOrDefault(STAT_HEAD_EMPLOYER_PENSION, BigDecimal.ZERO);
        BigDecimal currentJcpf = currentStatItems.getOrDefault(STAT_HEAD_EMPLOYER_JCPF, BigDecimal.ZERO);

        List<LineChange> changes = new ArrayList<>();
        Map<Integer, BigDecimal> updatedHeads = new java.util.LinkedHashMap<>(currentHeads);
        BigDecimal newBasicPay = currentHeads.getOrDefault(HEAD_BASIC, record.getBasicPay());
        newAmount = round(newAmount);

        if (headCount == HEAD_BASIC) {
            BigDecimal oldBasic = currentHeads.getOrDefault(HEAD_BASIC, record.getBasicPay());
            newBasicPay = newAmount;
            changes.add(new LineChange(HEAD_BASIC, false, oldBasic, newAmount));
            updatedHeads.put(HEAD_BASIC, newAmount);

            RegularPayFixation fixation = regularPayFixationRepository.findByEmployeeIdAndCurrentTrue(employee.getId())
                    .orElseThrow(() -> new BusinessRuleViolationException(
                            "Employee " + employee.getId() + " has no current pay fixation; cannot cascade Basic Pay edit"));
            ScaleType scaleType = fixation.getGradeScale().getScaleType();
            int daHeadCount = scaleType == ScaleType.CDA ? HEAD_DA_CDA : HEAD_DA_IDA;
            LocalDate periodEnd = YearMonth.of(record.getYear(), record.getMonth()).atEndOfMonth();

            BigDecimal daPercentage = daRateHistoryRepository
                    .findTopByScaleTypeAndActiveTrueAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(scaleType, periodEnd)
                    .map(DaRateHistory::getDaPercentage)
                    .orElseThrow(() -> new BusinessRuleViolationException("No active DA rate configured for scale type " + scaleType));
            BigDecimal oldDa = currentHeads.getOrDefault(daHeadCount, BigDecimal.ZERO);
            BigDecimal newDa = round(newBasicPay.multiply(daPercentage).divide(HUNDRED, 10, RoundingMode.HALF_UP));
            changes.add(new LineChange(daHeadCount, false, oldDa, newDa));
            updatedHeads.put(daHeadCount, newDa);

            RegionalOffice office = employee.getRegionalOffice();
            CityClass cityClass = office != null ? office.getCityClass() : CityClass.Z;
            BigDecimal hraPercentage = payrollHraRateRepository.findActiveOn(periodEnd).stream()
                    .filter(rate -> rate.getCityClass().equals(cityClass.name()))
                    .findFirst()
                    .map(PayrollHraRate::getRatePercentage)
                    .orElseThrow(() -> new BusinessRuleViolationException("No active HRA rate configured for city class " + cityClass));
            BigDecimal oldHra = currentHeads.getOrDefault(HEAD_HRA, BigDecimal.ZERO);
            BigDecimal newHra = round(newBasicPay.multiply(hraPercentage).divide(HUNDRED, 10, RoundingMode.HALF_UP));
            changes.add(new LineChange(HEAD_HRA, false, oldHra, newHra));
            updatedHeads.put(HEAD_HRA, newHra);

            BigDecimal basicPlusDa = newBasicPay.add(newDa);
            BigDecimal newCpf = computeCpf(basicPlusDa);
            BigDecimal oldCpf = currentHeads.getOrDefault(HEAD_CPF, BigDecimal.ZERO);
            changes.add(new LineChange(HEAD_CPF, false, oldCpf, newCpf));
            updatedHeads.put(HEAD_CPF, newCpf);

            addCpfStatutoryCascade(changes, employee, basicPlusDa, newCpf, currentEpf, currentPension, currentJcpf);

            BigDecimal newGrossForPtax = sumByEffectType(updatedHeads, catalog, SalaryHeadEffectType.EARNING);
            BigDecimal newPtax = resolvePtax(office, newGrossForPtax, record.getMonth());
            BigDecimal oldPtax = currentHeads.getOrDefault(HEAD_PTAX, BigDecimal.ZERO);
            changes.add(new LineChange(HEAD_PTAX, false, oldPtax, newPtax));
            updatedHeads.put(HEAD_PTAX, newPtax);
        } else if (headCount == HEAD_CPF) {
            BigDecimal oldCpf = currentHeads.getOrDefault(HEAD_CPF, BigDecimal.ZERO);
            changes.add(new LineChange(HEAD_CPF, false, oldCpf, newAmount));
            updatedHeads.put(HEAD_CPF, newAmount);

            int daHeadCount = resolveCurrentDaHeadCount(employee);
            BigDecimal basicPlusDa = newBasicPay.add(currentHeads.getOrDefault(daHeadCount, BigDecimal.ZERO));
            addCpfStatutoryCascade(changes, employee, basicPlusDa, newAmount, currentEpf, currentPension, currentJcpf);
        } else {
            BigDecimal oldAmount = currentHeads.getOrDefault(headCount, BigDecimal.ZERO);
            changes.add(new LineChange(headCount, false, oldAmount, newAmount));
            updatedHeads.put(headCount, newAmount);
        }

        BigDecimal grossAmount = sumByEffectType(updatedHeads, catalog, SalaryHeadEffectType.EARNING);
        BigDecimal totalDeductions = sumByEffectType(updatedHeads, catalog, SalaryHeadEffectType.DEDUCTION);
        BigDecimal netAmount = grossAmount.subtract(totalDeductions);

        return new Cascade(changes, newBasicPay, grossAmount, totalDeductions, netAmount);
    }

    /**
     * EPF (stat head 1) mirrors the new CPF (Head 27) amount unconditionally; Pension (stat head 4) is
     * recomputed per EPS eligibility off basicPlusDa (resolvePension()); JCPF (stat head 3) is
     * unconditionally EPF minus Pension - see resolveEmployerContributions()'s own javadoc in
     * PayrollBatchComputationService, which this mirrors exactly.
     */
    private void addCpfStatutoryCascade(List<LineChange> changes, Employee employee, BigDecimal basicPlusDa,
                                         BigDecimal newCpf, BigDecimal currentEpf, BigDecimal currentPension,
                                         BigDecimal currentJcpf) {
        BigDecimal newPension = resolvePension(employee, basicPlusDa);
        BigDecimal newJcpf = newCpf.subtract(newPension);
        changes.add(new LineChange(STAT_HEAD_EMPLOYER_EPF, true, currentEpf, newCpf));
        changes.add(new LineChange(STAT_HEAD_EMPLOYER_PENSION, true, currentPension, newPension));
        changes.add(new LineChange(STAT_HEAD_EMPLOYER_JCPF, true, currentJcpf, newJcpf));
    }

    /** Mirrors PayrollBatchComputationService.computeCpf() exactly. */
    private BigDecimal computeCpf(BigDecimal basicPlusDa) {
        BigDecimal cpfRate = payrollStatutoryParameterRepository.findByParamKeyAndEffectiveToIsNull("CPF_EMP_RATE")
                .map(PayrollStatutoryParameter::getParamValue)
                .orElse(DEFAULT_CPF_EMPLOYEE_RATE);
        return round(basicPlusDa.multiply(cpfRate).divide(HUNDRED, 10, RoundingMode.HALF_UP));
    }

    /** Mirrors the pension half of PayrollBatchComputationService.resolveEmployerContributions() exactly. */
    private BigDecimal resolvePension(Employee employee, BigDecimal basicPlusDa) {
        if (!employee.isEpsEligible()) {
            return BigDecimal.ZERO;
        }
        BigDecimal epsBaseRate = payrollStatutoryParameterRepository.findByParamKeyAndEffectiveToIsNull("EPS_BASE_RATE")
                .map(PayrollStatutoryParameter::getParamValue)
                .orElse(DEFAULT_EPS_BASE_RATE);
        BigDecimal epsWageCeiling = payrollStatutoryParameterRepository.findByParamKeyAndEffectiveToIsNull("EPS_WAGE_CEILING")
                .map(PayrollStatutoryParameter::getParamValue)
                .orElse(DEFAULT_EPS_WAGE_CEILING);

        BigDecimal epsWageBase = basicPlusDa.min(epsWageCeiling);
        BigDecimal pension = round(epsWageBase.multiply(epsBaseRate).divide(HUNDRED, 10, RoundingMode.HALF_UP));

        if (employee.isEpsHigherPensionEligible() && basicPlusDa.compareTo(epsWageCeiling) > 0) {
            BigDecimal epsHigherExtraRate = payrollStatutoryParameterRepository.findByParamKeyAndEffectiveToIsNull("EPS_HIGHER_EXTRA_RATE")
                    .map(PayrollStatutoryParameter::getParamValue)
                    .orElse(DEFAULT_EPS_HIGHER_EXTRA_RATE);
            BigDecimal excessWages = basicPlusDa.subtract(epsWageCeiling);
            pension = pension.add(floorToInteger(excessWages.multiply(epsHigherExtraRate).divide(HUNDRED, 10, RoundingMode.HALF_UP)));
        }
        return pension;
    }

    /** Basic(1)/CPF(27)-editing both need the employee's current DA head (CDA vs IDA) off their active pay fixation. */
    private int resolveCurrentDaHeadCount(Employee employee) {
        RegularPayFixation fixation = regularPayFixationRepository.findByEmployeeIdAndCurrentTrue(employee.getId())
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "Employee " + employee.getId() + " has no current pay fixation; cannot cascade CPF edit"));
        return fixation.getGradeScale().getScaleType() == ScaleType.CDA ? HEAD_DA_CDA : HEAD_DA_IDA;
    }

    private BigDecimal sumByEffectType(Map<Integer, BigDecimal> heads, Map<Integer, SalaryHead> catalog, SalaryHeadEffectType effectType) {
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Integer, BigDecimal> entry : heads.entrySet()) {
            SalaryHead head = catalog.get(entry.getKey());
            if (head != null && head.getEffectType() == effectType && entry.getValue() != null) {
                total = total.add(entry.getValue());
            }
        }
        return total;
    }

    /** Mirrors PayrollBatchComputationService.resolvePtax() exactly. */
    private BigDecimal resolvePtax(RegionalOffice effectiveOffice, BigDecimal grossAmount, int salMonth) {
        if (effectiveOffice == null) {
            return BigDecimal.ZERO;
        }
        String stateCode = stateMasterRepository.findByStateNameIgnoreCase(effectiveOffice.getState())
                .map(in.gov.jci.hrms.entity.StateMaster::getStateCode)
                .orElse(null);
        if (stateCode == null) {
            return BigDecimal.ZERO;
        }
        for (PtaxSlab slab : ptaxSlabRepository.findByStateCodeOrderBySlabMinAsc(stateCode)) {
            boolean atOrAboveMin = grossAmount.compareTo(slab.getSlabMin()) >= 0;
            boolean atOrBelowMax = slab.getSlabMax() == null || grossAmount.compareTo(slab.getSlabMax()) <= 0;
            if (atOrAboveMin && atOrBelowMax) {
                if (slab.getSpecialMonth() != null && slab.getSpecialMonth() == salMonth && slab.getSpecialMonthTax() != null) {
                    return round(slab.getSpecialMonthTax());
                }
                return round(slab.getTaxAmount());
            }
        }
        return BigDecimal.ZERO;
    }

    private void persistHeadAmount(PayrollMonthlyRecord record, int headCount, BigDecimal newAmount, boolean statutory) {
        if (statutory) {
            List<PayrollMonthlyStatutoryItem> items = statutoryItemRepository.findByRecord_TranId(record.getTranId());
            PayrollMonthlyStatutoryItem existing = items.stream().filter(i -> i.getStatHeadCount() == headCount).findFirst().orElse(null);
            if (existing != null) {
                // flush() before the insert below - Hibernate's default flush ordering runs ALL pending
                // insertions before ALL pending deletions regardless of Java call order, which would
                // otherwise momentarily violate the (tran_id, stat_head_count) unique constraint.
                statutoryItemRepository.delete(existing);
                statutoryItemRepository.flush();
            }
            if (newAmount.signum() > 0) {
                statutoryItemRepository.save(new PayrollMonthlyStatutoryItem(record, headCount, newAmount));
            }
            return;
        }
        List<PayrollMonthlyHeadItem> items = headItemRepository.findByRecord_TranId(record.getTranId());
        PayrollMonthlyHeadItem existing = items.stream().filter(i -> i.getHeadCount() == headCount).findFirst().orElse(null);
        if (existing != null) {
            headItemRepository.delete(existing);
            headItemRepository.flush();
        }
        if (newAmount.signum() > 0) {
            headItemRepository.save(new PayrollMonthlyHeadItem(record, headCount, newAmount));
        }
    }

    private PayrollEditResponse toResponse(PayrollMonthlyRecord record, Cascade cascade) {
        Map<Integer, SalaryHead> salaryCatalog = salaryHeadRepository.findAllByOrderByHeadCountAsc().stream()
                .collect(Collectors.toMap(SalaryHead::getHeadCount, h -> h));
        Map<Integer, StatutoryHead> statutoryCatalog = statutoryHeadRepository.findAllByOrderByStatHeadCountAsc().stream()
                .collect(Collectors.toMap(StatutoryHead::getStatHeadCount, h -> h));
        List<PayrollHeadLineResponse> lines = cascade.changes().stream()
                .map(change -> {
                    if (change.statutory()) {
                        StatutoryHead statHead = statutoryCatalog.get(change.headCount());
                        String shortName = statHead != null ? statHead.getStatHeadShortName() : String.valueOf(change.headCount());
                        String description = statHead != null ? statHead.getStatHeadDescr() : "";
                        return new PayrollHeadLineResponse(change.headCount(), shortName, description, "STATUTORY",
                                change.newAmount().setScale(2, RoundingMode.HALF_UP));
                    }
                    SalaryHead head = salaryCatalog.get(change.headCount());
                    String shortName = head != null ? head.getShortName() : String.valueOf(change.headCount());
                    String description = head != null ? head.getDescription() : "";
                    String category = head != null ? head.getEffectType().name() : "EARNING";
                    return new PayrollHeadLineResponse(change.headCount(), shortName, description, category,
                            change.newAmount().setScale(2, RoundingMode.HALF_UP));
                })
                .toList();
        return new PayrollEditResponse(record.getTranId(), lines, cascade.grossAmount(), cascade.totalDeductions(), cascade.netAmount());
    }

    /** Whole rupees only (HALF_UP) - matches PayrollBatchComputationService.round()'s own rule; no salary head amount carries paise, at compute time or edit time. */
    private BigDecimal round(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP);
    }

    /** Matches PayrollBatchComputationService.floorToInteger()'s own rule - used only for the EPS higher-pension extra contribution. */
    private BigDecimal floorToInteger(BigDecimal value) {
        return value.setScale(0, RoundingMode.FLOOR);
    }
}
