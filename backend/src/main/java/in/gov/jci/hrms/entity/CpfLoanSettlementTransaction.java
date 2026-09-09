package in.gov.jci.hrms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One direct, out-of-payroll cash/instrument settlement receipt against a CPF Trust loan - maps onto
 * cpf_loan_settlement_transactions, a table already live on the shared dev database (pre-existing, same
 * situation as V74's other tables - see CpfTrustMemberLedgerEntry's own javadoc). Recorded by
 * CpfLoanSettlementService.processCashSettlement() whenever a member pays down or forecloses a DISBURSED
 * loan directly (e.g. at separation, or by choice) rather than waiting out the remaining payroll recovery
 * installments.
 */
@Entity
@Table(name = "cpf_loan_settlement_transactions")
public class CpfLoanSettlementTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "receipt_voucher_no", nullable = false, unique = true, length = 50)
    private String receiptVoucherNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_id", nullable = false)
    private CpfLoanApplication loan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "fin_year", nullable = false, length = 9)
    private String finYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_type", nullable = false, length = 20, columnDefinition = "VARCHAR")
    private CpfLoanSettlementMode settlementType;

    @Column(name = "instrument_or_challan_no", nullable = false, length = 100)
    private String instrumentOrChallanNo;

    @Column(name = "instrument_date", nullable = false)
    private LocalDate instrumentDate;

    @Column(name = "bank_realization_date", nullable = false)
    private LocalDate bankRealizationDate;

    @Column(name = "trust_bank_account_code", nullable = false, length = 30)
    private String trustBankAccountCode;

    @Column(name = "principal_paid", nullable = false, precision = 12, scale = 2)
    private BigDecimal principalPaid = BigDecimal.ZERO;

    @Column(name = "interest_paid", nullable = false, precision = 12, scale = 2)
    private BigDecimal interestPaid = BigDecimal.ZERO;

    @Column(name = "total_amount_paid", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmountPaid;

    @Column(name = "is_early_foreclosure", nullable = false)
    private boolean earlyForeclosure = false;

    @Column(name = "elapsed_months", nullable = false)
    private int elapsedMonths = 0;

    @Column(name = "original_projected_interest", nullable = false, precision = 12, scale = 2)
    private BigDecimal originalProjectedInterest = BigDecimal.ZERO;

    @Column(name = "recomputed_statutory_interest", nullable = false, precision = 12, scale = 2)
    private BigDecimal recomputedStatutoryInterest = BigDecimal.ZERO;

    @Column(name = "interest_rebate_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal interestRebateAmount = BigDecimal.ZERO;

    @Column(name = "challan_doc_ref", length = 100)
    private String challanDocRef;

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "received_by")
    private Employee receivedBy;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    protected CpfLoanSettlementTransaction() {
    }

    public CpfLoanSettlementTransaction(String receiptVoucherNo, CpfLoanApplication loan, Employee employee, String finYear,
                                         CpfLoanSettlementMode settlementType, String instrumentOrChallanNo, LocalDate instrumentDate,
                                         LocalDate bankRealizationDate, String trustBankAccountCode, BigDecimal totalAmountPaid) {
        this.receiptVoucherNo = receiptVoucherNo;
        this.loan = loan;
        this.employee = employee;
        this.finYear = finYear;
        this.settlementType = settlementType;
        this.instrumentOrChallanNo = instrumentOrChallanNo;
        this.instrumentDate = instrumentDate;
        this.bankRealizationDate = bankRealizationDate;
        this.trustBankAccountCode = trustBankAccountCode;
        this.totalAmountPaid = totalAmountPaid;
    }

    public Long getId() {
        return id;
    }

    public String getReceiptVoucherNo() {
        return receiptVoucherNo;
    }

    public CpfLoanApplication getLoan() {
        return loan;
    }

    public Employee getEmployee() {
        return employee;
    }

    public String getFinYear() {
        return finYear;
    }

    public CpfLoanSettlementMode getSettlementType() {
        return settlementType;
    }

    public String getInstrumentOrChallanNo() {
        return instrumentOrChallanNo;
    }

    public LocalDate getInstrumentDate() {
        return instrumentDate;
    }

    public LocalDate getBankRealizationDate() {
        return bankRealizationDate;
    }

    public String getTrustBankAccountCode() {
        return trustBankAccountCode;
    }

    public BigDecimal getPrincipalPaid() {
        return principalPaid;
    }

    public void setPrincipalPaid(BigDecimal principalPaid) {
        this.principalPaid = principalPaid;
    }

    public BigDecimal getInterestPaid() {
        return interestPaid;
    }

    public void setInterestPaid(BigDecimal interestPaid) {
        this.interestPaid = interestPaid;
    }

    public BigDecimal getTotalAmountPaid() {
        return totalAmountPaid;
    }

    public boolean isEarlyForeclosure() {
        return earlyForeclosure;
    }

    public void setEarlyForeclosure(boolean earlyForeclosure) {
        this.earlyForeclosure = earlyForeclosure;
    }

    public int getElapsedMonths() {
        return elapsedMonths;
    }

    public void setElapsedMonths(int elapsedMonths) {
        this.elapsedMonths = elapsedMonths;
    }

    public BigDecimal getOriginalProjectedInterest() {
        return originalProjectedInterest;
    }

    public void setOriginalProjectedInterest(BigDecimal originalProjectedInterest) {
        this.originalProjectedInterest = originalProjectedInterest;
    }

    public BigDecimal getRecomputedStatutoryInterest() {
        return recomputedStatutoryInterest;
    }

    public void setRecomputedStatutoryInterest(BigDecimal recomputedStatutoryInterest) {
        this.recomputedStatutoryInterest = recomputedStatutoryInterest;
    }

    public BigDecimal getInterestRebateAmount() {
        return interestRebateAmount;
    }

    public void setInterestRebateAmount(BigDecimal interestRebateAmount) {
        this.interestRebateAmount = interestRebateAmount;
    }

    public String getChallanDocRef() {
        return challanDocRef;
    }

    public void setChallanDocRef(String challanDocRef) {
        this.challanDocRef = challanDocRef;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public Employee getReceivedBy() {
        return receivedBy;
    }

    public void setReceivedBy(Employee receivedBy) {
        this.receivedBy = receivedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
