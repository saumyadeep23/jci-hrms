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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An append-only mobile clock-in/out log entry (no updated_at/deleted_at
 * column - unlike most other tables in this schema, punches are never
 * edited or soft-deleted once recorded). is_within_geofence/review_status
 * are computed at write time by GeofenceService/MobilePunchService, not
 * caller-supplied.
 */
@Entity
@Table(name = "mobile_punches")
@EntityListeners(AuditableEntityListener.class)
public class MobilePunch implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "punch_time", nullable = false)
    private Instant punchTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "punch_type", nullable = false, length = 3)
    private PunchType punchType;

    @Column(name = "latitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "accuracy_meters", precision = 6, scale = 2)
    private BigDecimal accuracyMeters;

    @Column(name = "is_within_geofence", nullable = false)
    private boolean withinGeofence;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 20)
    private ReviewStatus reviewStatus;

    @Column(name = "device_id", length = 100)
    private String deviceId;

    @Column(name = "photo_s3_key", length = 255)
    private String photoS3Key;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MobilePunch() {
    }

    public MobilePunch(Employee employee, Instant punchTime, PunchType punchType,
                        BigDecimal latitude, BigDecimal longitude, boolean withinGeofence, ReviewStatus reviewStatus) {
        this.employee = employee;
        this.punchTime = punchTime;
        this.punchType = punchType;
        this.latitude = latitude;
        this.longitude = longitude;
        this.withinGeofence = withinGeofence;
        this.reviewStatus = reviewStatus;
    }

    public Long getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public Instant getPunchTime() {
        return punchTime;
    }

    public PunchType getPunchType() {
        return punchType;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public BigDecimal getAccuracyMeters() {
        return accuracyMeters;
    }

    public void setAccuracyMeters(BigDecimal accuracyMeters) {
        this.accuracyMeters = accuracyMeters;
    }

    public boolean isWithinGeofence() {
        return withinGeofence;
    }

    public ReviewStatus getReviewStatus() {
        return reviewStatus;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getPhotoS3Key() {
        return photoS3Key;
    }

    public void setPhotoS3Key(String photoS3Key) {
        this.photoS3Key = photoS3Key;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public String auditEntityName() {
        return "MobilePunch";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("employeeId", employee != null ? employee.getId() : null);
        snapshot.put("punchTime", punchTime);
        snapshot.put("punchType", punchType);
        snapshot.put("latitude", latitude);
        snapshot.put("longitude", longitude);
        snapshot.put("isWithinGeofence", withinGeofence);
        snapshot.put("reviewStatus", reviewStatus);
        snapshot.put("deviceId", deviceId);
        return snapshot;
    }
}
