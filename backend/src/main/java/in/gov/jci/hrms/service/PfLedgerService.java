package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.CpfBalanceLedgerRequest;
import in.gov.jci.hrms.dto.CpfBalanceLedgerResponse;
import in.gov.jci.hrms.dto.NonRefundableWithdrawalRequest;
import in.gov.jci.hrms.dto.PfDiversionResponse;
import in.gov.jci.hrms.dto.RefundableLoanDiversionRequest;
import in.gov.jci.hrms.entity.CpfBalanceLedger;
import in.gov.jci.hrms.entity.DiversionStatus;
import in.gov.jci.hrms.entity.DiversionType;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeLoan;
import in.gov.jci.hrms.entity.PfDiversion;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.CpfBalanceLedgerRepository;
import in.gov.jci.hrms.repository.EmployeeLoanRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.PfDiversionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Refundable loans against PF draw from the employee bucket only - they're
 * secured against the employee's own contributions. Non-refundable
 * withdrawals distribute across all three buckets as the caller specifies;
 * this service validates each bucket amount against its own ledger balance
 * (not just the total) and deducts on success. "Repayment locking" for a
 * non-refundable withdrawal means the deduction is permanent and one-shot -
 * there is no repayment schedule for it, unlike a refundable loan (which is
 * repaid through LoanRepaymentService against its linked EmployeeLoan).
 */
@Service
@Transactional(readOnly = true)
public class PfLedgerService {

    private final CpfBalanceLedgerRepository cpfBalanceLedgerRepository;
    private final PfDiversionRepository pfDiversionRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeLoanRepository employeeLoanRepository;

    public PfLedgerService(CpfBalanceLedgerRepository cpfBalanceLedgerRepository, PfDiversionRepository pfDiversionRepository,
                            EmployeeRepository employeeRepository, EmployeeLoanRepository employeeLoanRepository) {
        this.cpfBalanceLedgerRepository = cpfBalanceLedgerRepository;
        this.pfDiversionRepository = pfDiversionRepository;
        this.employeeRepository = employeeRepository;
        this.employeeLoanRepository = employeeLoanRepository;
    }

    @Transactional
    public CpfBalanceLedgerResponse createLedger(CpfBalanceLedgerRequest request) {
        Employee employee = employeeRepository.findById(request.employeeId())
                .orElseThrow(() -> new EmployeeNotFoundException(request.employeeId()));

        CpfBalanceLedger ledger = new CpfBalanceLedger(employee, request.employeeFundBalance(),
                request.employerFundBalance(), request.vpfBalance());
        try {
            return CpfBalanceLedgerResponse.from(cpfBalanceLedgerRepository.saveAndFlush(ledger));
        } catch (DataIntegrityViolationException ex) {
            throw new MasterDataConflictException("A CPF ledger already exists for employee " + request.employeeId());
        }
    }

    public CpfBalanceLedgerResponse getLedgerByEmployeeId(Long employeeId) {
        return CpfBalanceLedgerResponse.from(findLedgerOrThrow(employeeId));
    }

    @Transactional
    public PfDiversionResponse createRefundableLoanDiversion(RefundableLoanDiversionRequest request) {
        CpfBalanceLedger ledger = findLedgerOrThrow(request.employeeId());
        EmployeeLoan linkedLoan = employeeLoanRepository.findById(request.linkedLoanId())
                .orElseThrow(() -> new MasterDataNotFoundException("Employee Loan", request.linkedLoanId()));

        if (ledger.getEmployeeFundBalance().compareTo(request.amount()) < 0) {
            throw new BusinessRuleViolationException(
                    "Insufficient employee PF balance for employee " + request.employeeId()
                            + ": available " + ledger.getEmployeeFundBalance() + ", requested " + request.amount());
        }
        ledger.setEmployeeFundBalance(ledger.getEmployeeFundBalance().subtract(request.amount()));

        PfDiversion diversion = new PfDiversion(ledger.getEmployee(), DiversionType.REFUNDABLE_LOAN, request.amount(),
                request.amount(), BigDecimal.ZERO, BigDecimal.ZERO, linkedLoan, request.sanctionDate());
        return PfDiversionResponse.from(pfDiversionRepository.save(diversion));
    }

    @Transactional
    public PfDiversionResponse createNonRefundableWithdrawal(NonRefundableWithdrawalRequest request) {
        CpfBalanceLedger ledger = findLedgerOrThrow(request.employeeId());

        requireSufficientBalance("employee", ledger.getEmployeeFundBalance(), request.empBucketAmount());
        requireSufficientBalance("employer", ledger.getEmployerFundBalance(), request.erBucketAmount());
        requireSufficientBalance("VPF", ledger.getVpfBalance(), request.vpfBucketAmount());

        ledger.setEmployeeFundBalance(ledger.getEmployeeFundBalance().subtract(request.empBucketAmount()));
        ledger.setEmployerFundBalance(ledger.getEmployerFundBalance().subtract(request.erBucketAmount()));
        ledger.setVpfBalance(ledger.getVpfBalance().subtract(request.vpfBucketAmount()));

        BigDecimal totalAmount = request.empBucketAmount().add(request.erBucketAmount()).add(request.vpfBucketAmount());
        PfDiversion diversion = new PfDiversion(ledger.getEmployee(), DiversionType.NON_REFUNDABLE_WITHDRAWAL, totalAmount,
                request.empBucketAmount(), request.erBucketAmount(), request.vpfBucketAmount(), null, request.sanctionDate());
        return PfDiversionResponse.from(pfDiversionRepository.save(diversion));
    }

    @Transactional
    public PfDiversionResponse settle(Long diversionId) {
        PfDiversion diversion = pfDiversionRepository.findById(diversionId)
                .orElseThrow(() -> new MasterDataNotFoundException("PF Diversion", diversionId));
        if (diversion.getStatus() != DiversionStatus.ACTIVE) {
            throw new BusinessRuleViolationException("PF Diversion " + diversionId + " is not ACTIVE");
        }
        diversion.setStatus(DiversionStatus.SETTLED);
        return PfDiversionResponse.from(diversion);
    }

    private void requireSufficientBalance(String bucketName, BigDecimal available, BigDecimal requested) {
        if (available.compareTo(requested) < 0) {
            throw new BusinessRuleViolationException(
                    "Insufficient " + bucketName + " PF balance: available " + available + ", requested " + requested);
        }
    }

    private CpfBalanceLedger findLedgerOrThrow(Long employeeId) {
        return cpfBalanceLedgerRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new MasterDataNotFoundException("CPF Balance Ledger for employee", employeeId));
    }
}
