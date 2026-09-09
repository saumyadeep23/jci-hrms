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
 * Vehicle Allotment Transaction Management (Employment tab) - one row per vehicle allotment (current
 * or historical) for an employee. An active (ACTIVE-status) allotment suppresses Transport Allowance
 * (Head 10) to zero and keeps employee.has_office_car true - EmployeeVehicleAllotmentService is the
 * only writer of has_office_car; see its javadoc for why that sync happens there rather than via a DB
 * trigger.
 */
@Entity
@Table(name = "employee_vehicle_allotments")
@EntityListeners(AuditableEntityListener.class)
public class EmployeeVehicleAllotment implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "allotment_order_no", length = 100)
    private String allotmentOrderNo;

    @Column(name = "vehicle_reg_no", nullable = false, length = 50)
    private String vehicleRegNo;

    @Column(name = "vehicle_make_model", length = 100)
    private String vehicleMakeModel;

    @Column(name = "driver_provided", nullable = false)
    private boolean driverProvided = true;

    @Column(name = "personal_use_allowed", nullable = false)
    private boolean personalUseAllowed = true;

    @Column(name = "deduction_applicable", nullable = false)
    private boolean deductionApplicable = true;

    @Column(name = "monthly_deduction_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal monthlyDeductionAmount = new BigDecimal("2000.00");

    @Column(name = "allotted_from", nullable = false)
    private LocalDate allottedFrom;

    @Column(name = "surrendered_on")
    private LocalDate surrenderedOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private VehicleAllotmentStatus status = VehicleAllotmentStatus.ACTIVE;

    @Column(name = "remarks", length = 255)
    private String remarks;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected EmployeeVehicleAllotment() {
    }

    public EmployeeVehicleAllotment(Employee employee, String allotmentOrderNo, String vehicleRegNo, String vehicleMakeModel,
                                     boolean driverProvided, boolean personalUseAllowed, boolean deductionApplicable,
                                     BigDecimal monthlyDeductionAmount, LocalDate allottedFrom, String remarks) {
        this.employee = employee;
        this.allotmentOrderNo = allotmentOrderNo;
        this.vehicleRegNo = vehicleRegNo;
        this.vehicleMakeModel = vehicleMakeModel;
        this.driverProvided = driverProvided;
        this.personalUseAllowed = personalUseAllowed;
        this.deductionApplicable = deductionApplicable;
        if (monthlyDeductionAmount != null) {
            this.monthlyDeductionAmount = monthlyDeductionAmount;
        }
        this.allottedFrom = allottedFrom;
        this.remarks = remarks;
    }

    public Long getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public String getAllotmentOrderNo() {
        return allotmentOrderNo;
    }

    public String getVehicleRegNo() {
        return vehicleRegNo;
    }

    public String getVehicleMakeModel() {
        return vehicleMakeModel;
    }

    public boolean isDriverProvided() {
        return driverProvided;
    }

    public boolean isPersonalUseAllowed() {
        return personalUseAllowed;
    }

    public boolean isDeductionApplicable() {
        return deductionApplicable;
    }

    public BigDecimal getMonthlyDeductionAmount() {
        return monthlyDeductionAmount;
    }

    public LocalDate getAllottedFrom() {
        return allottedFrom;
    }

    public LocalDate getSurrenderedOn() {
        return surrenderedOn;
    }

    public void setSurrenderedOn(LocalDate surrenderedOn) {
        this.surrenderedOn = surrenderedOn;
    }

    public VehicleAllotmentStatus getStatus() {
        return status;
    }

    public void setStatus(VehicleAllotmentStatus status) {
        this.status = status;
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
        return "EmployeeVehicleAllotment";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("employeeId", employee != null ? employee.getId() : null);
        snapshot.put("vehicleRegNo", vehicleRegNo);
        snapshot.put("status", status);
        snapshot.put("allottedFrom", allottedFrom);
        snapshot.put("surrenderedOn", surrenderedOn);
        return snapshot;
    }
}
