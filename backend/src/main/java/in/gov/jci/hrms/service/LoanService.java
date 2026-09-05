package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.EmployeeLoanRequest;
import in.gov.jci.hrms.dto.EmployeeLoanResponse;
import in.gov.jci.hrms.dto.LoanForecloseRequest;
import in.gov.jci.hrms.dto.LoanRepaymentResponse;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeLoan;
import in.gov.jci.hrms.entity.LoanRepayment;
import in.gov.jci.hrms.entity.LoanStatus;
import in.gov.jci.hrms.entity.LoanType;
import in.gov.jci.hrms.entity.RepaymentSource;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.EmployeeLoanRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.LoanRepaymentRepository;
import in.gov.jci.hrms.repository.LoanTypeRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Owns the loan lifecycle (Sanction -> Disburse -> [repayments] -> Closed /
 * Foreclosed) and the FR-LOAN.3 reducing-balance interest formula, which
 * LoanRepaymentService reuses when recording each repayment. Loan type
 * rates/terms seeded in V10 are placeholders, not confirmed JCI loan
 * policy - see the migration's comment.
 */
@Service
@Transactional(readOnly = true)
public class LoanService {

    private static final String ENTITY_NAME = "Employee Loan";
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal TWELVE = new BigDecimal("12");

    private final EmployeeLoanRepository employeeLoanRepository;
    private final LoanRepaymentRepository loanRepaymentRepository;
    private final LoanTypeRepository loanTypeRepository;
    private final EmployeeRepository employeeRepository;

    public LoanService(EmployeeLoanRepository employeeLoanRepository, LoanRepaymentRepository loanRepaymentRepository,
                        LoanTypeRepository loanTypeRepository, EmployeeRepository employeeRepository) {
        this.employeeLoanRepository = employeeLoanRepository;
        this.loanRepaymentRepository = loanRepaymentRepository;
        this.loanTypeRepository = loanTypeRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional
    public EmployeeLoanResponse sanction(EmployeeLoanRequest request) {
        LoanType loanType = loanTypeRepository.findById(request.loanTypeId())
                .orElseThrow(() -> new MasterDataNotFoundException("Loan Type", request.loanTypeId()));
        Employee employee = employeeRepository.findById(request.employeeId())
                .orElseThrow(() -> new EmployeeNotFoundException(request.employeeId()));

        if (request.totalInstallments() > loanType.getMaxInstallments()) {
            throw new BusinessRuleViolationException(
                    "totalInstallments (" + request.totalInstallments() + ") exceeds " + loanType.getCode()
                            + "'s maximum of " + loanType.getMaxInstallments());
        }

        EmployeeLoan loan = new EmployeeLoan(loanType, employee, request.loanAccountNumber(), request.principalAmount(),
                loanType.getInterestRateAnnual(), request.totalInstallments(), request.sanctionDate());

        return EmployeeLoanResponse.from(save(loan));
    }

    public EmployeeLoanResponse getById(Long id) {
        return EmployeeLoanResponse.from(findOrThrow(id));
    }

    public Page<EmployeeLoanResponse> list(Pageable pageable) {
        return employeeLoanRepository.findAll(pageable).map(EmployeeLoanResponse::from);
    }

    @Transactional
    public EmployeeLoanResponse disburse(Long id) {
        EmployeeLoan loan = findOrThrow(id);
        requireStatus(loan, LoanStatus.SANCTIONED);

        loan.setStatus(LoanStatus.DISBURSED);
        return EmployeeLoanResponse.from(loan);
    }

    /**
     * FR-LOAN.3: interest for a period is charged on the outstanding
     * principal at the START of that period (reducing balance), not on the
     * original principal (which would be flat-rate).
     */
    public BigDecimal computeReducingBalanceInterest(BigDecimal outstandingPrincipal, BigDecimal annualRatePercent) {
        return outstandingPrincipal
                .multiply(annualRatePercent)
                .divide(HUNDRED, 10, RoundingMode.HALF_UP)
                .divide(TWELVE, 2, RoundingMode.HALF_UP);
    }

    /**
     * Pays off the entire remaining balance in one shot: final period's
     * interest on the current outstanding principal, plus the full
     * outstanding principal itself.
     */
    @Transactional
    public LoanRepaymentResponse foreclose(Long id, LoanForecloseRequest request) {
        EmployeeLoan loan = findOrThrow(id);
        if (loan.getStatus() != LoanStatus.DISBURSED && loan.getStatus() != LoanStatus.ACTIVE) {
            throw new BusinessRuleViolationException(
                    ENTITY_NAME + " " + id + " cannot be foreclosed from status " + loan.getStatus());
        }

        BigDecimal interestComponent = computeReducingBalanceInterest(loan.getOutstandingPrincipal(), loan.getInterestRate());
        BigDecimal principalComponent = loan.getOutstandingPrincipal();
        BigDecimal amount = interestComponent.add(principalComponent);

        LoanRepayment repayment = new LoanRepayment(loan, RepaymentSource.FORECLOSURE, amount, principalComponent,
                interestComponent, request.paymentDate());
        repayment.setTransactionReference(request.transactionReference());
        loanRepaymentRepository.save(repayment);

        loan.setOutstandingPrincipal(BigDecimal.ZERO);
        loan.setRemainingInstallments(0);
        loan.setStatus(LoanStatus.FORECLOSED);

        return LoanRepaymentResponse.from(repayment);
    }

    EmployeeLoan findOrThrow(Long id) {
        return employeeLoanRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException(ENTITY_NAME, id));
    }

    private void requireStatus(EmployeeLoan loan, LoanStatus expected) {
        if (loan.getStatus() != expected) {
            throw new BusinessRuleViolationException(
                    ENTITY_NAME + " " + loan.getId() + " must be " + expected + " but is " + loan.getStatus());
        }
    }

    private EmployeeLoan save(EmployeeLoan loan) {
        try {
            return employeeLoanRepository.saveAndFlush(loan);
        } catch (DataIntegrityViolationException ex) {
            throw new MasterDataConflictException(
                    ENTITY_NAME + " account number already in use: " + loan.getLoanAccountNumber());
        }
    }
}
