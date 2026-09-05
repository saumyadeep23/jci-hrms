package in.gov.jci.hrms.entity;

import in.gov.jci.hrms.audit.Auditable;
import in.gov.jci.hrms.audit.AuditableEntityListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * department_id/designation_id/regional_office_id/pay_scale_id are nullable -
 * legacy service-book backfill resolves from/to labels against master data on
 * a best-effort basis and leaves the FK null when unresolvable (see
 * LegacyMigrationService). event_type is a plain VARCHAR, not an enum, since
 * legacy career-event labels are free-form (JOINING, PROMOTION, TRANSFER,
 * INCREMENT, etc. - not an exhaustive closed set).
 */
@Entity
@Table(name = "employee_service_book")
@SQLRestriction("deleted_at IS NULL")
@EntityListeners(AuditableEntityListener.class)
public class EmployeeServiceBook implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "order_number", length = 100)
    private String orderNumber;

    @Column(name = "order_date")
    private LocalDate orderDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "designation_id")
    private Designation designation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "regional_office_id")
    private RegionalOffice regionalOffice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pay_scale_id")
    private PayScale payScale;

    @Column(name = "basic_pay", precision = 12, scale = 2)
    private BigDecimal basicPay;

    @Column(name = "event_description")
    private String eventDescription;

    @Column(name = "remarks")
    private String remarks;

    @Column(name = "is_migrated", nullable = false)
    private boolean migrated = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected EmployeeServiceBook() {
    }

    public EmployeeServiceBook(Employee employee, LocalDate eventDate, String eventType) {
        this.employee = employee;
        this.eventDate = eventDate;
        this.eventType = eventType;
    }

    public Long getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
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

    public Department getDepartment() {
        return department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }

    public Designation getDesignation() {
        return designation;
    }

    public void setDesignation(Designation designation) {
        this.designation = designation;
    }

    public RegionalOffice getRegionalOffice() {
        return regionalOffice;
    }

    public void setRegionalOffice(RegionalOffice regionalOffice) {
        this.regionalOffice = regionalOffice;
    }

    public PayScale getPayScale() {
        return payScale;
    }

    public void setPayScale(PayScale payScale) {
        this.payScale = payScale;
    }

    public BigDecimal getBasicPay() {
        return basicPay;
    }

    public void setBasicPay(BigDecimal basicPay) {
        this.basicPay = basicPay;
    }

    public String getEventDescription() {
        return eventDescription;
    }

    public void setEventDescription(String eventDescription) {
        this.eventDescription = eventDescription;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public boolean isMigrated() {
        return migrated;
    }

    public void setMigrated(boolean migrated) {
        this.migrated = migrated;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    @Override
    public String auditEntityName() {
        return "EmployeeServiceBook";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("employeeId", employee != null ? employee.getId() : null);
        snapshot.put("eventDate", eventDate);
        snapshot.put("eventType", eventType);
        snapshot.put("orderNumber", orderNumber);
        snapshot.put("departmentId", department != null ? department.getId() : null);
        snapshot.put("designationId", designation != null ? designation.getId() : null);
        snapshot.put("regionalOfficeId", regionalOffice != null ? regionalOffice.getId() : null);
        snapshot.put("basicPay", basicPay);
        snapshot.put("isMigrated", migrated);
        snapshot.put("deletedAt", deletedAt);
        return snapshot;
    }
}
