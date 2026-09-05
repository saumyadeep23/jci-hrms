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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "medical_claim_items")
@EntityListeners(AuditableEntityListener.class)
public class MedicalClaimItem implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medical_claim_id", nullable = false)
    private MedicalClaim medicalClaim;

    @Enumerated(EnumType.STRING)
    @Column(name = "expense_type", nullable = false, length = 20)
    private ExpenseType expenseType;

    @Column(name = "claimed_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal claimedAmount;

    @Column(name = "allowed_amount", precision = 12, scale = 2)
    private BigDecimal allowedAmount;

    @Column(name = "bill_number", nullable = false, length = 100)
    private String billNumber;

    @Column(name = "bill_date", nullable = false)
    private LocalDate billDate;

    @Column(name = "remarks")
    private String remarks;

    protected MedicalClaimItem() {
    }

    public MedicalClaimItem(MedicalClaim medicalClaim, ExpenseType expenseType, BigDecimal claimedAmount,
                             String billNumber, LocalDate billDate) {
        this.medicalClaim = medicalClaim;
        this.expenseType = expenseType;
        this.claimedAmount = claimedAmount;
        this.billNumber = billNumber;
        this.billDate = billDate;
    }

    public Long getId() {
        return id;
    }

    public MedicalClaim getMedicalClaim() {
        return medicalClaim;
    }

    public ExpenseType getExpenseType() {
        return expenseType;
    }

    public BigDecimal getClaimedAmount() {
        return claimedAmount;
    }

    public BigDecimal getAllowedAmount() {
        return allowedAmount;
    }

    public void setAllowedAmount(BigDecimal allowedAmount) {
        this.allowedAmount = allowedAmount;
    }

    public String getBillNumber() {
        return billNumber;
    }

    public LocalDate getBillDate() {
        return billDate;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    @Override
    public String auditEntityName() {
        return "MedicalClaimItem";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("medicalClaimId", medicalClaim != null ? medicalClaim.getId() : null);
        snapshot.put("expenseType", expenseType);
        snapshot.put("claimedAmount", claimedAmount);
        snapshot.put("allowedAmount", allowedAmount);
        snapshot.put("billNumber", billNumber);
        snapshot.put("billDate", billDate);
        snapshot.put("remarks", remarks);
        return snapshot;
    }
}
