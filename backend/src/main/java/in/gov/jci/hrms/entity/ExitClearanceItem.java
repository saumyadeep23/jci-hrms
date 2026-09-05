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

import java.math.BigDecimal;
import java.time.Instant;

/** One row per ExitClearanceDepartment for a given ExitClearanceRequest - V58 migration. No created_at/updated_at: provisioned once by ExitClearanceService.initiateExit(), only clearedAt/clearedByUserId are set thereafter. */
@Entity
@Table(name = "exit_clearance_items")
public class ExitClearanceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clearance_request_id", nullable = false)
    private ExitClearanceRequest clearanceRequest;

    @Enumerated(EnumType.STRING)
    @Column(name = "department_code", nullable = false, length = 50)
    private ExitClearanceDepartment departmentCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private ExitClearanceItemStatus status = ExitClearanceItemStatus.PENDING;

    @Column(name = "dues_recovery_amount", precision = 12, scale = 2)
    private BigDecimal duesRecoveryAmount = BigDecimal.ZERO;

    @Column(name = "remarks")
    private String remarks;

    @Column(name = "cleared_by_user_id")
    private Long clearedByUserId;

    @Column(name = "cleared_at")
    private Instant clearedAt;

    protected ExitClearanceItem() {
    }

    public ExitClearanceItem(ExitClearanceRequest clearanceRequest, ExitClearanceDepartment departmentCode) {
        this.clearanceRequest = clearanceRequest;
        this.departmentCode = departmentCode;
    }

    public Long getId() {
        return id;
    }

    public ExitClearanceRequest getClearanceRequest() {
        return clearanceRequest;
    }

    public ExitClearanceDepartment getDepartmentCode() {
        return departmentCode;
    }

    public ExitClearanceItemStatus getStatus() {
        return status;
    }

    public void setStatus(ExitClearanceItemStatus status) {
        this.status = status;
    }

    public BigDecimal getDuesRecoveryAmount() {
        return duesRecoveryAmount;
    }

    public void setDuesRecoveryAmount(BigDecimal duesRecoveryAmount) {
        this.duesRecoveryAmount = duesRecoveryAmount;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public Long getClearedByUserId() {
        return clearedByUserId;
    }

    public void setClearedByUserId(Long clearedByUserId) {
        this.clearedByUserId = clearedByUserId;
    }

    public Instant getClearedAt() {
        return clearedAt;
    }

    public void setClearedAt(Instant clearedAt) {
        this.clearedAt = clearedAt;
    }
}
