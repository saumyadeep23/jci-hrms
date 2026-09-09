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
 * Employee Company Accommodation (Onboarding/Edit Tab 9) - one row per accommodation allotment
 * (current or historical) for an employee. JCI does not own residential quarters; accommodation is
 * leased or company-provided at an address, not a quarter number in an estate - see V68's own comment
 * for why this replaced the earlier quarter/estate model (V67). An OCCUPIED allotment suppresses HRA
 * (Head 9) to zero and schedules the monthly license fee/water/electric recoveries in payroll (future
 * payroll-run integration, not done by this entity itself - see
 * EmployeeQuarterAllotmentService.isHraSuppressed()). When syncCurrentAddress is true,
 * EmployeeQuarterAllotmentService also propagates this address to the employee's PRESENT
 * employee_addresses row - see its javadoc.
 */
@Entity
@Table(name = "employee_quarter_allotments")
@EntityListeners(AuditableEntityListener.class)
public class EmployeeQuarterAllotment implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "allotment_order_no", length = 100)
    private String allotmentOrderNo;

    @Column(name = "address_line1", nullable = false, length = 255)
    private String addressLine1;

    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state_code", length = 20)
    private String stateCode;

    @Column(name = "pincode", length = 10)
    private String pincode;

    @Column(name = "sync_current_address", nullable = false)
    private boolean syncCurrentAddress = true;

    @Column(name = "license_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal licenseFee = BigDecimal.ZERO;

    @Column(name = "water_charges", nullable = false, precision = 10, scale = 2)
    private BigDecimal waterCharges = BigDecimal.ZERO;

    @Column(name = "electric_charges", nullable = false, precision = 10, scale = 2)
    private BigDecimal electricCharges = BigDecimal.ZERO;

    @Column(name = "allotted_from", nullable = false)
    private LocalDate allottedFrom;

    @Column(name = "vacated_on")
    private LocalDate vacatedOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private QuarterAllotmentStatus status = QuarterAllotmentStatus.OCCUPIED;

    @Column(name = "remarks", length = 255)
    private String remarks;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected EmployeeQuarterAllotment() {
    }

    public EmployeeQuarterAllotment(Employee employee, String allotmentOrderNo, String addressLine1, String addressLine2,
                                     String city, String stateCode, String pincode, boolean syncCurrentAddress,
                                     BigDecimal licenseFee, BigDecimal waterCharges, BigDecimal electricCharges,
                                     LocalDate allottedFrom, String remarks) {
        this.employee = employee;
        this.allotmentOrderNo = allotmentOrderNo;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.city = city;
        this.stateCode = stateCode;
        this.pincode = pincode;
        this.syncCurrentAddress = syncCurrentAddress;
        if (licenseFee != null) {
            this.licenseFee = licenseFee;
        }
        if (waterCharges != null) {
            this.waterCharges = waterCharges;
        }
        if (electricCharges != null) {
            this.electricCharges = electricCharges;
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

    public void setAllotmentOrderNo(String allotmentOrderNo) {
        this.allotmentOrderNo = allotmentOrderNo;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public void setAddressLine1(String addressLine1) {
        this.addressLine1 = addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public void setAddressLine2(String addressLine2) {
        this.addressLine2 = addressLine2;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getStateCode() {
        return stateCode;
    }

    public void setStateCode(String stateCode) {
        this.stateCode = stateCode;
    }

    public String getPincode() {
        return pincode;
    }

    public void setPincode(String pincode) {
        this.pincode = pincode;
    }

    public boolean isSyncCurrentAddress() {
        return syncCurrentAddress;
    }

    public void setSyncCurrentAddress(boolean syncCurrentAddress) {
        this.syncCurrentAddress = syncCurrentAddress;
    }

    public BigDecimal getLicenseFee() {
        return licenseFee;
    }

    public void setLicenseFee(BigDecimal licenseFee) {
        this.licenseFee = licenseFee;
    }

    public BigDecimal getWaterCharges() {
        return waterCharges;
    }

    public void setWaterCharges(BigDecimal waterCharges) {
        this.waterCharges = waterCharges;
    }

    public BigDecimal getElectricCharges() {
        return electricCharges;
    }

    public void setElectricCharges(BigDecimal electricCharges) {
        this.electricCharges = electricCharges;
    }

    public LocalDate getAllottedFrom() {
        return allottedFrom;
    }

    public void setAllottedFrom(LocalDate allottedFrom) {
        this.allottedFrom = allottedFrom;
    }

    public LocalDate getVacatedOn() {
        return vacatedOn;
    }

    public void setVacatedOn(LocalDate vacatedOn) {
        this.vacatedOn = vacatedOn;
    }

    public QuarterAllotmentStatus getStatus() {
        return status;
    }

    public void setStatus(QuarterAllotmentStatus status) {
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
        return "EmployeeQuarterAllotment";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("employeeId", employee != null ? employee.getId() : null);
        snapshot.put("city", city);
        snapshot.put("status", status);
        snapshot.put("allottedFrom", allottedFrom);
        snapshot.put("vacatedOn", vacatedOn);
        return snapshot;
    }
}
