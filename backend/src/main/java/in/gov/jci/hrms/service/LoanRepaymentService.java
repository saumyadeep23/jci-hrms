package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.LoanRepaymentRequest;
import in.gov.jci.hrms.dto.LoanRepaymentResponse;
import in.gov.jci.hrms.entity.EmployeeLoan;
import in.gov.jci.hrms.entity.LoanRepayment;
import in.gov.jci.hrms.entity.LoanStatus;
import in.gov.jci.hrms.entity.PayrollRun;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.LoanRepaymentRepository;
import in.gov.jci.hrms.repository.PayrollRunRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Records periodic repayments (PAYROLL_DEDUCTION, or CASH_DEPOSIT for
 * ad-hoc cash recovery outside the payroll cycle) against a DISBURSED or
 * ACTIVE loan. Interest for the period is computed on the loan's current
 * outstanding principal (reducing balance, via LoanService); the rest of
 * the payment reduces principal. Foreclosure is a distinct, one-shot
 * full-balance payoff handled by LoanService.foreclose(), not this class.
 */
@Service
@Transactional(readOnly = true)
public class LoanRepaymentService {

    private final LoanRepaymentRepository loanRepaymentRepository;
    private final PayrollRunRepository payrollRunRepository;
    private final LoanService loanService;

    public LoanRepaymentService(LoanRepaymentRepository loanRepaymentRepository, PayrollRunRepository payrollRunRepository,
                                 LoanService loanService) {
        this.loanRepaymentRepository = loanRepaymentRepository;
        this.payrollRunRepository = payrollRunRepository;
        this.loanService = loanService;
    }

    @Transactional
    public LoanRepaymentResponse recordRepayment(Long loanId, LoanRepaymentRequest request) {
        EmployeeLoan loan = loanService.findOrThrow(loanId);
        if (loan.getStatus() != LoanStatus.DISBURSED && loan.getStatus() != LoanStatus.ACTIVE) {
            throw new BusinessRuleViolationException(
                    "Employee Loan " + loanId + " cannot accept repayments from status " + loan.getStatus());
        }
        if (loan.getRemainingInstallments() <= 0) {
            throw new BusinessRuleViolationException("Employee Loan " + loanId + " has no remaining installments");
        }

        BigDecimal interestComponent = loanService.computeReducingBalanceInterest(loan.getOutstandingPrincipal(), loan.getInterestRate());
        if (request.amount().compareTo(interestComponent) < 0) {
            throw new BusinessRuleViolationException(
                    "Repayment amount " + request.amount() + " is less than the accrued interest of " + interestComponent);
        }
        BigDecimal principalComponent = request.amount().subtract(interestComponent);
        if (principalComponent.compareTo(loan.getOutstandingPrincipal()) > 0) {
            principalComponent = loan.getOutstandingPrincipal();
        }

        LoanRepayment repayment = new LoanRepayment(loan, request.repaymentSource(), request.amount(),
                principalComponent, interestComponent, request.paymentDate());
        repayment.setTransactionReference(request.transactionReference());
        if (request.payrollRunId() != null) {
            PayrollRun payrollRun = payrollRunRepository.findById(request.payrollRunId())
                    .orElseThrow(() -> new MasterDataNotFoundException("Payroll Run", request.payrollRunId()));
            repayment.setPayrollRun(payrollRun);
        }
        loanRepaymentRepository.save(repayment);

        loan.setOutstandingPrincipal(loan.getOutstandingPrincipal().subtract(principalComponent));
        loan.setRemainingInstallments(loan.getRemainingInstallments() - 1);
        if (loan.getRemainingInstallments() <= 0 || loan.getOutstandingPrincipal().compareTo(BigDecimal.ZERO) <= 0) {
            loan.setStatus(LoanStatus.CLOSED);
        } else {
            loan.setStatus(LoanStatus.ACTIVE);
        }

        return LoanRepaymentResponse.from(repayment);
    }

    public LoanRepaymentResponse getById(Long id) {
        LoanRepayment repayment = loanRepaymentRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException("Loan Repayment", id));
        return LoanRepaymentResponse.from(repayment);
    }

    public Page<LoanRepaymentResponse> list(Pageable pageable) {
        return loanRepaymentRepository.findAll(pageable).map(LoanRepaymentResponse::from);
    }
}
