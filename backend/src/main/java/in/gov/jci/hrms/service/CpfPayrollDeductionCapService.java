package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.CpfPayrollDeductionCapRequest;
import in.gov.jci.hrms.dto.CpfPayrollDeductionCapResponse;
import in.gov.jci.hrms.entity.CpfPayrollDeductionCap;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.repository.CpfPayrollDeductionCapRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Payroll Deduction Cap (Part 18) - see CpfPayrollDeductionCap's own javadoc for its lighter-weight versioning compared to CpfWithdrawalRuleService. */
@Service
@Transactional(readOnly = true)
public class CpfPayrollDeductionCapService {

    private final CpfPayrollDeductionCapRepository repository;

    public CpfPayrollDeductionCapService(CpfPayrollDeductionCapRepository repository) {
        this.repository = repository;
    }

    public CpfPayrollDeductionCapResponse getCurrent() {
        return repository.findByEffectiveToIsNull().map(CpfPayrollDeductionCapResponse::from)
                .orElseThrow(() -> new BusinessRuleViolationException("No active CPF payroll deduction cap is configured."));
    }

    public List<CpfPayrollDeductionCapResponse> history() {
        return repository.findAllByOrderByEffectiveFromDesc().stream().map(CpfPayrollDeductionCapResponse::from).toList();
    }

    @Transactional
    public CpfPayrollDeductionCapResponse revise(CpfPayrollDeductionCapRequest request) {
        repository.findByEffectiveToIsNull().ifPresent(current -> current.setEffectiveTo(request.effectiveFrom().minusDays(1)));
        var cap = new CpfPayrollDeductionCap(request.normalPercent(), request.cooperativePercent(), request.applicability(),
                request.legalReference(), request.effectiveFrom(), request.remarks());
        return CpfPayrollDeductionCapResponse.from(repository.save(cap));
    }
}
