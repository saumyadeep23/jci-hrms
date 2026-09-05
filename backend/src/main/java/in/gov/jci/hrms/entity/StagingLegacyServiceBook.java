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

/** from_designation/to_designation/from_department/to_department/from_ro/to_ro are free-text legacy labels, resolved to master-data FKs on a best-effort basis during promotion (nulled out if unresolvable) - see LegacyMigrationService. */
@Entity
@Table(name = "staging_legacy_service_book")
@EntityListeners(AuditableEntityListener.class)
public class StagingLegacyServiceBook implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_code", nullable = false, length = 50)
    private String employeeCode;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "order_number", length = 100)
    private String orderNumber;

    @Column(name = "order_date")
    private LocalDate orderDate;

    @Column(name = "from_designation", length = 150)
    private String fromDesignation;

    @Column(name = "to_designation", length = 150)
    private String toDesignation;

    @Column(name = "from_department", length = 150)
    private String fromDepartment;

    @Column(name = "to_department", length = 150)
    private String toDepartment;

    @Column(name = "from_ro", length = 150)
    private String fromRo;

    @Column(name = "to_ro", length = 150)
    private String toRo;

    @Column(name = "basic_pay", precision = 12, scale = 2)
    private BigDecimal basicPay;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10, columnDefinition = "VARCHAR")
    private StagingRowStatus status = StagingRowStatus.PENDING;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StagingLegacyServiceBook() {
    }

    public StagingLegacyServiceBook(String employeeCode, LocalDate eventDate, String eventType) {
        this.employeeCode = employeeCode;
        this.eventDate = eventDate;
        this.eventType = eventType;
    }

    public Long getId() {
        return id;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public LocalDate getEventDate() {
        return eventDate;
    }

    public String getEventType() {
        return eventType;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public LocalDate getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(LocalDate orderDate) {
        this.orderDate = orderDate;
    }

    public String getFromDesignation() {
        return fromDesignation;
    }

    public void setFromDesignation(String fromDesignation) {
        this.fromDesignation = fromDesignation;
    }

    public String getToDesignation() {
        return toDesignation;
    }

    public void setToDesignation(String toDesignation) {
        this.toDesignation = toDesignation;
    }

    public String getFromDepartment() {
        return fromDepartment;
    }

    public void setFromDepartment(String fromDepartment) {
        this.fromDepartment = fromDepartment;
    }

    public String getToDepartment() {
        return toDepartment;
    }

    public void setToDepartment(String toDepartment) {
        this.toDepartment = toDepartment;
    }

    public String getFromRo() {
        return fromRo;
    }

    public void setFromRo(String fromRo) {
        this.fromRo = fromRo;
    }

    public String getToRo() {
        return toRo;
    }

    public void setToRo(String toRo) {
        this.toRo = toRo;
    }

    public BigDecimal getBasicPay() {
        return basicPay;
    }

    public void setBasicPay(BigDecimal basicPay) {
        this.basicPay = basicPay;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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
        return "StagingLegacyServiceBook";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("employeeCode", employeeCode);
        snapshot.put("eventDate", eventDate);
        snapshot.put("eventType", eventType);
        snapshot.put("orderNumber", orderNumber);
        snapshot.put("status", status);
        snapshot.put("rejectionReason", rejectionReason);
        return snapshot;
    }
}
