package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.CpfLoanRecoveryPolicyRequest;
import in.gov.jci.hrms.dto.CpfLoanRecoveryPolicyResponse;
import in.gov.jci.hrms.entity.CpfLoanRecoveryPolicy;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.repository.CpfLoanRecoveryPolicyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * CPF Trust loan recovery policy (spec Parts 15/19/39) - repayment-commencement mode and the
 * principal-to-interest-installment ratio, DB-driven per Part 2's "no hardcoded business constants"
 * (no Java literal "12" anywhere - see CpfLoanApplicationService.resolveInterestInstallmentRatio()).
 * Same effective-dated, single-current-row versioning convention as CpfPayrollDeductionCapService.
 */
@Service
@Transactional(readOnly = true)
public class CpfLoanRecoveryPolicyService {

    /** The only commencement mode this codebase actually implements today (CpfLoanPayrollRecoveryResolverService) - matches the CHECK constraint on cpf_loan_recovery_policy.commencement_mode (V84). */
    public static final String NEXT_PAYROLL_MONTH_AFTER_DISBURSEMENT = "NEXT_PAYROLL_MONTH_AFTER_DISBURSEMENT";

    private final CpfLoanRecoveryPolicyRepository repository;

    public CpfLoanRecoveryPolicyService(CpfLoanRecoveryPolicyRepository repository) {
        this.repository = repository;
    }

    public CpfLoanRecoveryPolicyResponse getCurrent() {
        return CpfLoanRecoveryPolicyResponse.from(currentOrThrow());
    }

    public CpfLoanRecoveryPolicy currentOrThrow() {
        return repository.findByEffectiveToIsNull()
                .orElseThrow(() -> new BusinessRuleViolationException("No active CPF loan recovery policy is configured."));
    }

    public List<CpfLoanRecoveryPolicyResponse> history() {
        return repository.findAllByOrderByEffectiveFromDesc().stream().map(CpfLoanRecoveryPolicyResponse::from).toList();
    }

    @Transactional
    public CpfLoanRecoveryPolicyResponse revise(CpfLoanRecoveryPolicyRequest request) {
        if (!NEXT_PAYROLL_MONTH_AFTER_DISBURSEMENT.equals(request.commencementMode())) {
            throw new BusinessRuleViolationException(
                    "Commencement mode " + request.commencementMode() + " is not implemented - only " + NEXT_PAYROLL_MONTH_AFTER_DISBURSEMENT + " is live today.");
        }
        repository.findByEffectiveToIsNull().ifPresent(current -> current.setEffectiveTo(request.effectiveFrom().minusDays(1)));
        var policy = new CpfLoanRecoveryPolicy(request.commencementMode(), request.principalInstallmentsPerInterestInstallment(),
                request.effectiveFrom(), request.remarks());
        return CpfLoanRecoveryPolicyResponse.from(repository.save(policy));
    }
}
