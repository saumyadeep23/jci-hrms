package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.CpfRuleSimulatorRequest;
import in.gov.jci.hrms.dto.CpfRuleSimulatorResponse;
import in.gov.jci.hrms.entity.CpfWithdrawalRuleDetail;
import in.gov.jci.hrms.entity.CpfWithdrawalRuleVersion;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.repository.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Part 24's "Rule Simulator" - an administrator-only, read-only what-if tool. Can target a specific
 * DRAFT/PENDING rule version (ruleVersionId) so Trust officials can test a PROPOSED rule before it is ever
 * approved, or the currently-active APPROVED version when ruleVersionId is omitted. Never persists
 * anything - see CpfApplicationService for the actual apply/sanction/disburse lifecycle this mirrors the
 * math of.
 */
@Service
@Transactional(readOnly = true)
public class CpfRuleSimulatorService {

    private final CpfWithdrawalRuleService ruleService;
    private final CpfWithdrawalRuleEngine ruleEngine;
    private final EmployeeRepository employeeRepository;

    public CpfRuleSimulatorService(CpfWithdrawalRuleService ruleService, CpfWithdrawalRuleEngine ruleEngine, EmployeeRepository employeeRepository) {
        this.ruleService = ruleService;
        this.ruleEngine = ruleEngine;
        this.employeeRepository = employeeRepository;
    }

    public CpfRuleSimulatorResponse simulate(CpfRuleSimulatorRequest request) {
        CpfWithdrawalRuleVersion version = request.ruleVersionId() != null
                ? ruleService.findEntity(UUID.fromString(request.ruleVersionId()))
                        .orElseThrow(() -> new BusinessRuleViolationException("Rule version " + request.ruleVersionId() + " not found"))
                : ruleService.resolveActiveVersion(request.purposeCode(), LocalDate.now())
                        .orElseThrow(() -> new BusinessRuleViolationException("No APPROVED, currently-effective rule for purpose " + request.purposeCode()
                                + " - and no explicit ruleVersionId was supplied to simulate a proposed one."));
        CpfWithdrawalRuleDetail detail = ruleService.requireDetail(version);

        Map<String, BigDecimal> headBalances = Map.of(
                "HEAD_A", nz(request.headABalance()), "HEAD_B", nz(request.headBBalance()), "HEAD_C", nz(request.headCBalance()));

        boolean serviceEligible = true;
        String serviceReason = "No employee specified - service eligibility not evaluated.";
        boolean frequencyEligible = true;
        String frequencyReason = "No employee specified - frequency/concurrency not evaluated.";
        if (request.employeeCode() != null && !request.employeeCode().isBlank()) {
            Employee employee = employeeRepository.findByEmployeeCode(request.employeeCode())
                    .orElseThrow(() -> new BusinessRuleViolationException("No employee found with code " + request.employeeCode()));
            var serviceResult = ruleEngine.evaluateServiceEligibility(detail, employee, LocalDate.now());
            serviceEligible = serviceResult.eligible();
            serviceReason = serviceResult.reason();
            var frequencyResult = ruleEngine.evaluateFrequency(detail, request.employeeCode(), version.getPurpose().getId(), LocalDate.now());
            frequencyEligible = frequencyResult.eligible();
            frequencyReason = frequencyResult.reason();
        }

        var ceiling = ruleEngine.evaluateCeiling(detail, new CpfWithdrawalRuleEngine.CeilingEvaluationContext(
                request.basicPlusDa(), headBalances, request.propertyCost(), request.existingPayrollDeductions(), BigDecimal.ZERO));

        BigDecimal requested = request.requestedAmount() != null ? request.requestedAmount() : ceiling.finalAmount();
        BigDecimal effectiveAmount = requested.min(ceiling.finalAmount()).max(BigDecimal.ZERO);
        var allocation = ruleEngine.allocateDebitAcrossHeads(detail, effectiveAmount, headBalances);

        // See CpfApplicationService.sanction()'s own comment: interest_method is populated uniformly on
        // every live seed row, so gate the EMI projection on the withdrawal type's actual is_refundable
        // flag too, not merely on interest_method being non-null.
        BigDecimal projectedEmi = null;
        if (version.getPurpose().getType().isRefundable() && detail.getInterestMethod() != null && !detail.getInterestMethod().isBlank()) {
            Integer tenure = request.loanTenureMonths() != null ? request.loanTenureMonths() : detail.getDefaultTenureMonths();
            if (tenure != null && tenure > 0) {
                projectedEmi = effectiveAmount.divide(BigDecimal.valueOf(tenure), 2, RoundingMode.HALF_UP);
            }
        }

        boolean taxLikely = detail.getTaxServiceThresholdMonths() != null && request.employeeCode() != null
                && !request.employeeCode().isBlank() && !serviceEligibleForTaxExemption(detail, request);

        return new CpfRuleSimulatorResponse(version.getId().toString(), version.getVersionTag(), version.getStatus().name(),
                serviceEligible, serviceReason, frequencyEligible, frequencyReason, ceiling.totalEligibleBalance(), ceiling.finalAmount(),
                allocation.debitByHeadCode(), projectedEmi, taxLikely, ceiling.trace());
    }

    /** Rough, display-only flag (Part 19: the CPF module only determines "tax likely", never calculates the actual TDS - that stays with the central PayrollTdsEngine). */
    private boolean serviceEligibleForTaxExemption(CpfWithdrawalRuleDetail detail, CpfRuleSimulatorRequest request) {
        return employeeRepository.findByEmployeeCode(request.employeeCode())
                .map(e -> {
                    if (e.getDateOfJoining() == null) {
                        return false;
                    }
                    long months = java.time.temporal.ChronoUnit.MONTHS.between(e.getDateOfJoining(), LocalDate.now());
                    return months >= detail.getTaxServiceThresholdMonths();
                })
                .orElse(false);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
