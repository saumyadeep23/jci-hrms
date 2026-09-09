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
 * One immutable transaction row in a member's CPF Trust passbook - maps onto
 * cpf_trust_member_ledger_entries, a table already live on the shared dev database (see V74's own header
 * comment); column shapes here match that live table exactly.
 *
 * Never updated in place once inserted (unlike CpfBalanceLedger, which is a current-snapshot-only cache
 * with no history). Each row carries the running balance AFTER its own credits/debits are applied,
 * computed by whichever service inserts it (CpfLedgerSyncService for PAYROLL_MONTHLY/LOAN_REPAYMENT,
 * IncomingFundTransferService for TRANSFER_IN, CpfInterestComputationService for ANNUAL_INTEREST) by
 * reading the member's immediately preceding row - see
 * CpfTrustMemberLedgerEntryRepository.findFirstByEmployee_IdOrderByValueDateDescIdDesc.
 *
 * payrollRun references the legacy payroll_runs table (matching the live schema's own column, and
 * LoanRepayment.payrollRun's existing precedent) even though the actual CPF/VPF/JCPF amounts synced here
 * are read from the newer PayrollBatch-linked payroll_monthly_head_items/payroll_monthly_statutory_items -
 * see CpfLedgerSyncService's own javadoc for how the two are reconciled.
 */
@Entity
@Table(name = "cpf_trust_member_ledger_entries")
public class CpfTrustMemberLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "fin_year", nullable = false, length = 9)
    private String finYear;

    @Column(name = "sal_month")
    private Integer salMonth;

    @Column(name = "sal_year")
    private Integer salYear;

    @Column(name = "value_date", nullable = false)
    private LocalDate valueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 30, columnDefinition = "VARCHAR")
    private CpfLedgerEntryType entryType;

    @Column(name = "ee_share_credit", nullable = false, precision = 12, scale = 2)
    private BigDecimal eeShareCredit = BigDecimal.ZERO;

    @Column(name = "ee_share_debit", nullable = false, precision = 12, scale = 2)
    private BigDecimal eeShareDebit = BigDecimal.ZERO;

    @Column(name = "er_share_credit", nullable = false, precision = 12, scale = 2)
    private BigDecimal erShareCredit = BigDecimal.ZERO;

    @Column(name = "er_share_debit", nullable = false, precision = 12, scale = 2)
    private BigDecimal erShareDebit = BigDecimal.ZERO;

    @Column(name = "vpf_credit", nullable = false, precision = 12, scale = 2)
    private BigDecimal vpfCredit = BigDecimal.ZERO;

    @Column(name = "vpf_debit", nullable = false, precision = 12, scale = 2)
    private BigDecimal vpfDebit = BigDecimal.ZERO;

    @Column(name = "interest_credit", nullable = false, precision = 12, scale = 2)
    private BigDecimal interestCredit = BigDecimal.ZERO;

    @Column(name = "total_credit", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalCredit = BigDecimal.ZERO;

    @Column(name = "total_debit", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalDebit = BigDecimal.ZERO;

    @Column(name = "running_ee_balance", nullable = false, precision = 14, scale = 2)
    private BigDecimal runningEeBalance;

    @Column(name = "running_er_balance", nullable = false, precision = 14, scale = 2)
    private BigDecimal runningErBalance;

    @Column(name = "running_vpf_balance", nullable = false, precision = 14, scale = 2)
    private BigDecimal runningVpfBalance;

    @Column(name = "running_total_balance", nullable = false, precision = 14, scale = 2)
    private BigDecimal runningTotalBalance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payroll_run_id")
    private PayrollRun payrollRun;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id")
    private EmployeeIncomingFundTransfer transfer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id")
    private CpfLoanApplication loan;

    /** Para 60(2), EPF Scheme: true when rateApplied/rateSourceFinYear come from the preceding FY because this FY's rate isn't notified yet - see CpfRateResolutionService. */
    @Column(name = "is_provisional_rate", nullable = false)
    private boolean provisionalRate = false;

    @Column(name = "rate_applied", precision = 5, scale = 2)
    private BigDecimal rateApplied;

    @Column(name = "rate_source_fin_year", length = 9)
    private String rateSourceFinYear;

    @Column(name = "reference_doc_no", length = 100)
    private String referenceDocNo;

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    protected CpfTrustMemberLedgerEntry() {
    }

    public CpfTrustMemberLedgerEntry(Employee employee, String finYear, LocalDate valueDate, CpfLedgerEntryType entryType,
                                      BigDecimal runningEeBalance, BigDecimal runningErBalance, BigDecimal runningVpfBalance,
                                      BigDecimal runningTotalBalance) {
        this.employee = employee;
        this.finYear = finYear;
        this.valueDate = valueDate;
        this.entryType = entryType;
        this.runningEeBalance = runningEeBalance;
        this.runningErBalance = runningErBalance;
        this.runningVpfBalance = runningVpfBalance;
        this.runningTotalBalance = runningTotalBalance;
    }

    public Long getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public String getFinYear() {
        return finYear;
    }

    public Integer getSalMonth() {
        return salMonth;
    }

    public void setSalMonth(Integer salMonth) {
        this.salMonth = salMonth;
    }

    public Integer getSalYear() {
        return salYear;
    }

    public void setSalYear(Integer salYear) {
        this.salYear = salYear;
    }

    public LocalDate getValueDate() {
        return valueDate;
    }

    public CpfLedgerEntryType getEntryType() {
        return entryType;
    }

    public BigDecimal getEeShareCredit() {
        return eeShareCredit;
    }

    public void setEeShareCredit(BigDecimal eeShareCredit) {
        this.eeShareCredit = eeShareCredit;
    }

    public BigDecimal getEeShareDebit() {
        return eeShareDebit;
    }

    public void setEeShareDebit(BigDecimal eeShareDebit) {
        this.eeShareDebit = eeShareDebit;
    }

    public BigDecimal getErShareCredit() {
        return erShareCredit;
    }

    public void setErShareCredit(BigDecimal erShareCredit) {
        this.erShareCredit = erShareCredit;
    }

    public BigDecimal getErShareDebit() {
        return erShareDebit;
    }

    public void setErShareDebit(BigDecimal erShareDebit) {
        this.erShareDebit = erShareDebit;
    }

    public BigDecimal getVpfCredit() {
        return vpfCredit;
    }

    public void setVpfCredit(BigDecimal vpfCredit) {
        this.vpfCredit = vpfCredit;
    }

    public BigDecimal getVpfDebit() {
        return vpfDebit;
    }

    public void setVpfDebit(BigDecimal vpfDebit) {
        this.vpfDebit = vpfDebit;
    }

    public BigDecimal getInterestCredit() {
        return interestCredit;
    }

    public void setInterestCredit(BigDecimal interestCredit) {
        this.interestCredit = interestCredit;
    }

    public BigDecimal getTotalCredit() {
        return totalCredit;
    }

    public void setTotalCredit(BigDecimal totalCredit) {
        this.totalCredit = totalCredit;
    }

    public BigDecimal getTotalDebit() {
        return totalDebit;
    }

    public void setTotalDebit(BigDecimal totalDebit) {
        this.totalDebit = totalDebit;
    }

    public BigDecimal getRunningEeBalance() {
        return runningEeBalance;
    }

    public BigDecimal getRunningErBalance() {
        return runningErBalance;
    }

    public BigDecimal getRunningVpfBalance() {
        return runningVpfBalance;
    }

    public BigDecimal getRunningTotalBalance() {
        return runningTotalBalance;
    }

    public PayrollRun getPayrollRun() {
        return payrollRun;
    }

    public void setPayrollRun(PayrollRun payrollRun) {
        this.payrollRun = payrollRun;
    }

    public EmployeeIncomingFundTransfer getTransfer() {
        return transfer;
    }

    public void setTransfer(EmployeeIncomingFundTransfer transfer) {
        this.transfer = transfer;
    }

    public CpfLoanApplication getLoan() {
        return loan;
    }

    public void setLoan(CpfLoanApplication loan) {
        this.loan = loan;
    }

    public boolean isProvisionalRate() {
        return provisionalRate;
    }

    public void setProvisionalRate(boolean provisionalRate) {
        this.provisionalRate = provisionalRate;
    }

    public BigDecimal getRateApplied() {
        return rateApplied;
    }

    public void setRateApplied(BigDecimal rateApplied) {
        this.rateApplied = rateApplied;
    }

    public String getRateSourceFinYear() {
        return rateSourceFinYear;
    }

    public void setRateSourceFinYear(String rateSourceFinYear) {
        this.rateSourceFinYear = rateSourceFinYear;
    }

    public String getReferenceDocNo() {
        return referenceDocNo;
    }

    public void setReferenceDocNo(String referenceDocNo) {
        this.referenceDocNo = referenceDocNo;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
