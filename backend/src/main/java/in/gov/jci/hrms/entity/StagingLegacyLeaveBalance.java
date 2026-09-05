package in.gov.jci.hrms.entity;

import in.gov.jci.hrms.audit.Auditable;
import in.gov.jci.hrms.audit.AuditableEntityListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** No soft-delete columns - staging rows are transient scratch data for one migration pass, never edited in place after creation except status/rejection_reason. */
@Entity
@Table(name = "staging_legacy_leave_balances")
@EntityListeners(AuditableEntityListener.class)
public class StagingLegacyLeaveBalance implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_code", nullable = false, length = 50)
    private String employeeCode;

    @Column(name = "leave_type_code", nullable = false, length = 20)
    private String leaveTypeCode;

    @Column(name = "opening_balance", nullable = false, precision = 4, scale = 1)
    private BigDecimal openingBalance;

    @Column(name = "as_on_date", nullable = false)
    private LocalDate asOnDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10, columnDefinition = "VARCHAR")
    private StagingRowStatus status = StagingRowStatus.PENDING;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StagingLegacyLeaveBalance() {
    }

    public StagingLegacyLeaveBalance(String employeeCode, String leaveTypeCode, BigDecimal openingBalance, LocalDate asOnDate) {
        this.employeeCode = employeeCode;
        this.leaveTypeCode = leaveTypeCode;
        this.openingBalance = openingBalance;
        this.asOnDate = asOnDate;
    }

    public Long getId() {
        return id;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public String getLeaveTypeCode() {
        return leaveTypeCode;
    }

    public BigDecimal getOpeningBalance() {
        return openingBalance;
    }

    public LocalDate getAsOnDate() {
        return asOnDate;
    }

    public StagingRowStatus getStatus() {
        return status;
    }

    public void setStatus(StagingRowStatus status) {
        this.status = status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public String auditEntityName() {
        return "StagingLegacyLeaveBalance";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("employeeCode", employeeCode);
        snapshot.put("leaveTypeCode", leaveTypeCode);
        snapshot.put("openingBalance", openingBalance);
        snapshot.put("asOnDate", asOnDate);
        snapshot.put("status", status);
        snapshot.put("rejectionReason", rejectionReason);
        return snapshot;
    }
}
