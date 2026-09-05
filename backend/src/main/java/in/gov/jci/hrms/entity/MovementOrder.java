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

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** A published Transfer/Promotion/Transfer-cum-Promotion order. One order can cover several employees (each an EmployeeMovementRecord). */
@Entity
@Table(name = "movement_orders")
@EntityListeners(AuditableEntityListener.class)
public class MovementOrder implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 30)
    private MovementOrderType orderType;

    @Column(name = "order_ref_no", nullable = false, length = 100)
    private String orderRefNo;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Column(name = "sanctioned_by_role", nullable = false, length = 100)
    private String sanctionedByRole = "Competent Authority";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "signed_by_employee_id")
    private Employee signedByEmployee;

    /** V48 (additive) - the order's own intended effective date, distinct from orderDate (when issued) and the movement record's own joining_date (when actually reported). Nullable. */
    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private MovementOrderStatus status = MovementOrderStatus.PUBLISHED;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MovementOrder() {
    }

    public MovementOrder(MovementOrderType orderType, String orderRefNo, LocalDate orderDate) {
        this.orderType = orderType;
        this.orderRefNo = orderRefNo;
        this.orderDate = orderDate;
    }

    public Long getId() {
        return id;
    }

    public MovementOrderType getOrderType() {
        return orderType;
    }

    public String getOrderRefNo() {
        return orderRefNo;
    }

    public LocalDate getOrderDate() {
        return orderDate;
    }

    public String getSanctionedByRole() {
        return sanctionedByRole;
    }

    public void setSanctionedByRole(String sanctionedByRole) {
        if (sanctionedByRole != null) {
            this.sanctionedByRole = sanctionedByRole;
        }
    }

    public Employee getSignedByEmployee() {
        return signedByEmployee;
    }

    public void setSignedByEmployee(Employee signedByEmployee) {
        this.signedByEmployee = signedByEmployee;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(LocalDate effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    public MovementOrderStatus getStatus() {
        return status;
    }

    public void setStatus(MovementOrderStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public String auditEntityName() {
        return "MovementOrder";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("orderType", orderType);
        snapshot.put("orderRefNo", orderRefNo);
        snapshot.put("orderDate", orderDate);
        snapshot.put("status", status);
        return snapshot;
    }
}
