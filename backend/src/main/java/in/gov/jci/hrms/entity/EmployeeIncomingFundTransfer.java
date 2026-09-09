package in.gov.jci.hrms.entity;

import in.gov.jci.hrms.audit.Auditable;
import in.gov.jci.hrms.audit.AuditableEntityListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A prior employer's/Trust's PF+Pension corpus being transferred in for a newly-joined employee. Maps
 * onto employee_incoming_fund_transfers - a table already live on the shared dev database (created by
 * other tooling before this entity existed, same situation as payroll_salary_heads before V66; see V74's
 * own header comment) - column shapes here match that live table exactly, not the original field spec
 * this task started from.
 *
 * IncomingFundTransferService.recordIncomingTransfer() creates this (status SUBMITTED), a Trust officer
 * verifies bank realization and credits it (verifyAndCreditTrustLedger(), status CREDITED_TO_LEDGER),
 * which posts a TRANSFER_IN row in cpf_trust_member_ledger_entries and a PRIOR_SERVICE_CREDIT row in
 * employee_service_book. pastServiceRecordId optionally links this to the employee's own declared
 * EmployeePastServiceRecord for that same past employer - not required, since a transfer can arrive
 * before/without that record existing yet.
 */
@Entity
@Table(name = "employee_incoming_fund_transfers")
@EntityListeners(AuditableEntityListener.class)
public class EmployeeIncomingFundTransfer implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transfer_reference_no", nullable = false, unique = true, length = 50)
    private String transferReferenceNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "past_service_record_id")
    private EmployeePastServiceRecord pastServiceRecord;

    @Column(name = "source_organization_name", nullable = false, length = 200)
    private String sourceOrganizationName;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_organization_type", nullable = false, length = 50, columnDefinition = "VARCHAR")
    private PastServiceOrganizationType sourceOrganizationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "transfer_type", nullable = false, length = 30, columnDefinition = "VARCHAR")
    private IncomingTransferType transferType;

    @Column(name = "relieving_date", nullable = false)
    private LocalDate relievingDate;

    @Column(name = "jci_joining_date", nullable = false)
    private LocalDate jciJoiningDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", nullable = false, length = 20, columnDefinition = "VARCHAR")
    private IncomingTransferPaymentMode paymentMode;

    @Column(name = "instrument_or_utr_no", nullable = false, length = 100)
    private String instrumentOrUtrNo;

    @Column(name = "instrument_date", nullable = false)
    private LocalDate instrumentDate;

    @Column(name = "bank_realization_date", nullable = false)
    private LocalDate bankRealizationDate;

    @Column(name = "bank_account_code", nullable = false, length = 30)
    private String bankAccountCode;

    @Column(name = "ee_cpf_principal", nullable = false, precision = 12, scale = 2)
    private BigDecimal eeCpfPrincipal = BigDecimal.ZERO;

    @Column(name = "ee_cpf_interest", nullable = false, precision = 12, scale = 2)
    private BigDecimal eeCpfInterest = BigDecimal.ZERO;

    @Column(name = "er_jcpf_principal", nullable = false, precision = 12, scale = 2)
    private BigDecimal erJcpfPrincipal = BigDecimal.ZERO;

    @Column(name = "er_jcpf_interest", nullable = false, precision = 12, scale = 2)
    private BigDecimal erJcpfInterest = BigDecimal.ZERO;

    @Column(name = "vpf_principal", nullable = false, precision = 12, scale = 2)
    private BigDecimal vpfPrincipal = BigDecimal.ZERO;

    @Column(name = "vpf_interest", nullable = false, precision = 12, scale = 2)
    private BigDecimal vpfInterest = BigDecimal.ZERO;

    @Column(name = "total_cpf_transferred", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalCpfTransferred;

    /** Short scheme code (EPS-95, NPS, ...) - length 10, matching the live column. */
    @Column(name = "pension_scheme", nullable = false, length = 10)
    private String pensionScheme;

    @Column(name = "pension_corpus_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal pensionCorpusAmount = BigDecimal.ZERO;

    @Column(name = "pran_or_ppo_no", length = 50)
    private String pranOrPpoNo;

    @Column(name = "past_qualifying_service_years", nullable = false)
    private int pastQualifyingServiceYears = 0;

    @Column(name = "past_qualifying_service_days", nullable = false)
    private int pastQualifyingServiceDays = 0;

    @Column(name = "gratuity_transferred_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal gratuityTransferredAmount = BigDecimal.ZERO;

    @Column(name = "is_gratuity_service_counted", nullable = false)
    private boolean gratuityServiceCounted = true;

    @Column(name = "annexure_k_doc_ref", length = 100)
    private String annexureKDocRef;

    @Column(name = "sanction_order_no", length = 100)
    private String sanctionOrderNo;

    @Column(name = "sanction_date")
    private LocalDate sanctionDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30, columnDefinition = "VARCHAR")
    private IncomingTransferStatus status = IncomingTransferStatus.SUBMITTED;

    @Column(name = "credited_at")
    private Instant creditedAt;

    @Column(name = "rejection_remarks", columnDefinition = "TEXT")
    private String rejectionRemarks;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "credited_by")
    private Employee creditedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private Employee createdBy;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected EmployeeIncomingFundTransfer() {
    }

    public EmployeeIncomingFundTransfer(String transferReferenceNo, Employee employee, String sourceOrganizationName,
                                         PastServiceOrganizationType sourceOrganizationType, IncomingTransferType transferType,
                                         LocalDate relievingDate, LocalDate jciJoiningDate, IncomingTransferPaymentMode paymentMode,
                                         String instrumentOrUtrNo, LocalDate instrumentDate, LocalDate bankRealizationDate,
                                         String bankAccountCode, BigDecimal totalCpfTransferred, String pensionScheme) {
        this.transferReferenceNo = transferReferenceNo;
        this.employee = employee;
        this.sourceOrganizationName = sourceOrganizationName;
        this.sourceOrganizationType = sourceOrganizationType;
        this.transferType = transferType;
        this.relievingDate = relievingDate;
        this.jciJoiningDate = jciJoiningDate;
        this.paymentMode = paymentMode;
        this.instrumentOrUtrNo = instrumentOrUtrNo;
        this.instrumentDate = instrumentDate;
        this.bankRealizationDate = bankRealizationDate;
        this.bankAccountCode = bankAccountCode;
        this.totalCpfTransferred = totalCpfTransferred;
        this.pensionScheme = pensionScheme;
    }

    public Long getId() {
        return id;
    }

    public String getTransferReferenceNo() {
        return transferReferenceNo;
    }

    public Employee getEmployee() {
        return employee;
    }

    public EmployeePastServiceRecord getPastServiceRecord() {
        return pastServiceRecord;
    }

    public void setPastServiceRecord(EmployeePastServiceRecord pastServiceRecord) {
        this.pastServiceRecord = pastServiceRecord;
    }

    public String getSourceOrganizationName() {
        return sourceOrganizationName;
    }

    public PastServiceOrganizationType getSourceOrganizationType() {
        return sourceOrganizationType;
    }

    public IncomingTransferType getTransferType() {
        return transferType;
    }

    public LocalDate getRelievingDate() {
        return relievingDate;
    }

    public LocalDate getJciJoiningDate() {
        return jciJoiningDate;
    }

    public IncomingTransferPaymentMode getPaymentMode() {
        return paymentMode;
    }

    public String getInstrumentOrUtrNo() {
        return instrumentOrUtrNo;
    }

    public LocalDate getInstrumentDate() {
        return instrumentDate;
    }

    public LocalDate getBankRealizationDate() {
        return bankRealizationDate;
    }

    public String getBankAccountCode() {
        return bankAccountCode;
    }

    public BigDecimal getEeCpfPrincipal() {
        return eeCpfPrincipal;
    }

    public void setEeCpfPrincipal(BigDecimal eeCpfPrincipal) {
        this.eeCpfPrincipal = eeCpfPrincipal;
    }

    public BigDecimal getEeCpfInterest() {
        return eeCpfInterest;
    }

    public void setEeCpfInterest(BigDecimal eeCpfInterest) {
        this.eeCpfInterest = eeCpfInterest;
    }

    public BigDecimal getErJcpfPrincipal() {
        return erJcpfPrincipal;
    }

    public void setErJcpfPrincipal(BigDecimal erJcpfPrincipal) {
        this.erJcpfPrincipal = erJcpfPrincipal;
    }

    public BigDecimal getErJcpfInterest() {
        return erJcpfInterest;
    }

    public void setErJcpfInterest(BigDecimal erJcpfInterest) {
        this.erJcpfInterest = erJcpfInterest;
    }

    public BigDecimal getVpfPrincipal() {
        return vpfPrincipal;
    }

    public void setVpfPrincipal(BigDecimal vpfPrincipal) {
        this.vpfPrincipal = vpfPrincipal;
    }

    public BigDecimal getVpfInterest() {
        return vpfInterest;
    }

    public void setVpfInterest(BigDecimal vpfInterest) {
        this.vpfInterest = vpfInterest;
    }

    public BigDecimal getTotalCpfTransferred() {
        return totalCpfTransferred;
    }

    public String getPensionScheme() {
        return pensionScheme;
    }

    public BigDecimal getPensionCorpusAmount() {
        return pensionCorpusAmount;
    }

    public void setPensionCorpusAmount(BigDecimal pensionCorpusAmount) {
        this.pensionCorpusAmount = pensionCorpusAmount;
    }

    public String getPranOrPpoNo() {
        return pranOrPpoNo;
    }

    public void setPranOrPpoNo(String pranOrPpoNo) {
        this.pranOrPpoNo = pranOrPpoNo;
    }

    public int getPastQualifyingServiceYears() {
        return pastQualifyingServiceYears;
    }

    public void setPastQualifyingServiceYears(int pastQualifyingServiceYears) {
        this.pastQualifyingServiceYears = pastQualifyingServiceYears;
    }

    public int getPastQualifyingServiceDays() {
        return pastQualifyingServiceDays;
    }

    public void setPastQualifyingServiceDays(int pastQualifyingServiceDays) {
        this.pastQualifyingServiceDays = pastQualifyingServiceDays;
    }

    public BigDecimal getGratuityTransferredAmount() {
        return gratuityTransferredAmount;
    }

    public void setGratuityTransferredAmount(BigDecimal gratuityTransferredAmount) {
        this.gratuityTransferredAmount = gratuityTransferredAmount;
    }

    public boolean isGratuityServiceCounted() {
        return gratuityServiceCounted;
    }

    public void setGratuityServiceCounted(boolean gratuityServiceCounted) {
        this.gratuityServiceCounted = gratuityServiceCounted;
    }

    public String getAnnexureKDocRef() {
        return annexureKDocRef;
    }

    public void setAnnexureKDocRef(String annexureKDocRef) {
        this.annexureKDocRef = annexureKDocRef;
    }

    public String getSanctionOrderNo() {
        return sanctionOrderNo;
    }

    public void setSanctionOrderNo(String sanctionOrderNo) {
        this.sanctionOrderNo = sanctionOrderNo;
    }

    public LocalDate getSanctionDate() {
        return sanctionDate;
    }

    public void setSanctionDate(LocalDate sanctionDate) {
        this.sanctionDate = sanctionDate;
    }

    public IncomingTransferStatus getStatus() {
        return status;
    }

    public void setStatus(IncomingTransferStatus status) {
        this.status = status;
    }

    public Instant getCreditedAt() {
        return creditedAt;
    }

    public void setCreditedAt(Instant creditedAt) {
        this.creditedAt = creditedAt;
    }

    public String getRejectionRemarks() {
        return rejectionRemarks;
    }

    public void setRejectionRemarks(String rejectionRemarks) {
        this.rejectionRemarks = rejectionRemarks;
    }

    public Employee getCreditedBy() {
        return creditedBy;
    }

    public void setCreditedBy(Employee creditedBy) {
        this.creditedBy = creditedBy;
    }

    public Employee getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Employee createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String auditEntityName() {
        return "EmployeeIncomingFundTransfer";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("transferReferenceNo", transferReferenceNo);
        snapshot.put("employeeId", employee != null ? employee.getId() : null);
        snapshot.put("sourceOrganizationName", sourceOrganizationName);
        snapshot.put("totalCpfTransferred", totalCpfTransferred);
        snapshot.put("status", status);
        snapshot.put("creditedAt", creditedAt);
        return snapshot;
    }
}
