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

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** One per separation event - provisions 7 ExitClearanceItem rows (one per ExitClearanceDepartment) at creation, see ExitClearanceService.initiateExit(). V58 migration. */
@Entity
@Table(name = "exit_clearance_requests")
@EntityListeners(AuditableEntityListener.class)
public class ExitClearanceRequest implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "separation_type", nullable = false, length = 50)
    private SeparationType separationType;

    @Column(name = "initiated_date", nullable = false)
    private LocalDate initiatedDate = LocalDate.now();

    @Column(name = "target_release_date", nullable = false)
    private LocalDate targetReleaseDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private ExitClearanceStatus status = ExitClearanceStatus.INITIATED;

    @Column(name = "release_order_ref_no", length = 100)
    private String releaseOrderRefNo;

    @Column(name = "release_order_date")
    private LocalDate releaseOrderDate;

    @Column(name = "remarks")
    private String remarks;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ExitClearanceRequest() {
    }

    public ExitClearanceRequest(Employee employee, SeparationType separationType, LocalDate targetReleaseDate, String remarks) {
        this.employee = employee;
        this.separationType = separationType;
        this.targetReleaseDate = targetReleaseDate;
        this.remarks = remarks;
    }

    public Long getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public SeparationType getSeparationType() {
        return separationType;
    }

    public LocalDate getInitiatedDate() {
        return initiatedDate;
    }

    public LocalDate getTargetReleaseDate() {
        return targetReleaseDate;
    }

    public void setTargetReleaseDate(LocalDate targetReleaseDate) {
        this.targetReleaseDate = targetReleaseDate;
    }

    public ExitClearanceStatus getStatus() {
        return status;
    }

    public void setStatus(ExitClearanceStatus status) {
        this.status = status;
    }

    public String getReleaseOrderRefNo() {
        return releaseOrderRefNo;
    }

    public void setReleaseOrderRefNo(String releaseOrderRefNo) {
        this.releaseOrderRefNo = releaseOrderRefNo;
    }

    public LocalDate getReleaseOrderDate() {
        return releaseOrderDate;
    }

    public void setReleaseOrderDate(LocalDate releaseOrderDate) {
        this.releaseOrderDate = releaseOrderDate;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String auditEntityName() {
        return "ExitClearanceRequest";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("employeeId", employee != null ? employee.getId() : null);
        snapshot.put("separationType", separationType);
        snapshot.put("targetReleaseDate", targetReleaseDate);
        snapshot.put("status", status);
        snapshot.put("releaseOrderRefNo", releaseOrderRefNo);
        snapshot.put("releaseOrderDate", releaseOrderDate);
        return snapshot;
    }
}
