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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A device (phone or biometric terminal) an employee has registered for
 * attendance punching, subject to admin approval before it's meant to be
 * trusted. Nothing in MobilePunchService currently cross-checks a punch's
 * free-text device_id against an APPROVED row here (see
 * RegisteredDeviceService's class javadoc) - this is the registry/approval
 * workflow itself, not yet an enforcement gate.
 */
@Entity
@Table(name = "registered_devices")
@EntityListeners(AuditableEntityListener.class)
public class RegisteredDevice implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "device_name", nullable = false, length = 150)
    private String deviceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", nullable = false, length = 30)
    private DeviceType deviceType;

    @Column(name = "device_identifier", nullable = false, length = 150)
    private String deviceIdentifier;

    @Column(name = "platform", length = 100)
    private String platform;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DeviceApprovalStatus status = DeviceApprovalStatus.PENDING_APPROVAL;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private Employee approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RegisteredDevice() {
    }

    public RegisteredDevice(Employee employee, String deviceName, DeviceType deviceType, String deviceIdentifier, String platform) {
        this.employee = employee;
        this.deviceName = deviceName;
        this.deviceType = deviceType;
        this.deviceIdentifier = deviceIdentifier;
        this.platform = platform;
    }

    public Long getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public DeviceType getDeviceType() {
        return deviceType;
    }

    public String getDeviceIdentifier() {
        return deviceIdentifier;
    }

    public String getPlatform() {
        return platform;
    }

    public DeviceApprovalStatus getStatus() {
        return status;
    }

    public Employee getApprovedBy() {
        return approvedBy;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void approve(Employee approver, Instant at) {
        this.status = DeviceApprovalStatus.APPROVED;
        this.approvedBy = approver;
        this.approvedAt = at;
    }

    public void revoke() {
        this.status = DeviceApprovalStatus.REVOKED;
    }

    /** Re-submitting the same (employee, deviceIdentifier) pair - resets the approval lifecycle back to PENDING_APPROVAL rather than leaving a stale APPROVED/REVOKED status on refreshed device details. */
    public void reRegister(String deviceName, DeviceType deviceType, String platform) {
        this.deviceName = deviceName;
        this.deviceType = deviceType;
        this.platform = platform;
        this.status = DeviceApprovalStatus.PENDING_APPROVAL;
        this.approvedBy = null;
        this.approvedAt = null;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String auditEntityName() {
        return "RegisteredDevice";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("employeeId", employee != null ? employee.getId() : null);
        snapshot.put("deviceName", deviceName);
        snapshot.put("deviceType", deviceType);
        snapshot.put("deviceIdentifier", deviceIdentifier);
        snapshot.put("platform", platform);
        snapshot.put("status", status);
        snapshot.put("approvedById", approvedBy != null ? approvedBy.getId() : null);
        snapshot.put("approvedAt", approvedAt);
        return snapshot;
    }
}
