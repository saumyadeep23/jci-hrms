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
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * request_number uniqueness is enforced by a partial unique index
 * (WHERE deleted_at IS NULL) in the Flyway migration. direct_* costs are
 * expenses the organization paid/booked directly (e.g. a company-booked
 * flight) - distinct from a TadaClaim's out-of-pocket amount, which is
 * what the employee is separately reimbursed for.
 */
@Entity
@Table(name = "tour_requests")
@SQLRestriction("deleted_at IS NULL")
@EntityListeners(AuditableEntityListener.class)
public class TourRequest implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_number", nullable = false, length = 50)
    private String requestNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "purpose", nullable = false)
    private String purpose;

    @Column(name = "origin", nullable = false, length = 150)
    private String origin;

    @Column(name = "destination", nullable = false, length = 150)
    private String destination;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "is_post_facto", nullable = false)
    private boolean postFacto = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TourRequestStatus status = TourRequestStatus.DRAFT;

    @Column(name = "direct_flight_cost", precision = 12, scale = 2)
    private BigDecimal directFlightCost;

    @Column(name = "direct_hotel_cost", precision = 12, scale = 2)
    private BigDecimal directHotelCost;

    @Column(name = "direct_vehicle_cost", precision = 12, scale = 2)
    private BigDecimal directVehicleCost;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected TourRequest() {
    }

    public TourRequest(String requestNumber, Employee employee, String purpose, String origin, String destination,
                        LocalDate startDate, LocalDate endDate, boolean postFacto) {
        this.requestNumber = requestNumber;
        this.employee = employee;
        this.purpose = purpose;
        this.origin = origin;
        this.destination = destination;
        this.startDate = startDate;
        this.endDate = endDate;
        this.postFacto = postFacto;
    }

    public Long getId() {
        return id;
    }

    public String getRequestNumber() {
        return requestNumber;
    }

    public Employee getEmployee() {
        return employee;
    }

    public String getPurpose() {
        return purpose;
    }

    public String getOrigin() {
        return origin;
    }

    public String getDestination() {
        return destination;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public boolean isPostFacto() {
        return postFacto;
    }

    public TourRequestStatus getStatus() {
        return status;
    }

    public void setStatus(TourRequestStatus status) {
        this.status = status;
    }

    public BigDecimal getDirectFlightCost() {
        return directFlightCost;
    }

    public void setDirectFlightCost(BigDecimal directFlightCost) {
        this.directFlightCost = directFlightCost;
    }

    public BigDecimal getDirectHotelCost() {
        return directHotelCost;
    }

    public void setDirectHotelCost(BigDecimal directHotelCost) {
        this.directHotelCost = directHotelCost;
    }

    public BigDecimal getDirectVehicleCost() {
        return directVehicleCost;
    }

    public void setDirectVehicleCost(BigDecimal directVehicleCost) {
        this.directVehicleCost = directVehicleCost;
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
        return "TourRequest";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("requestNumber", requestNumber);
        snapshot.put("employeeId", employee != null ? employee.getId() : null);
        snapshot.put("origin", origin);
        snapshot.put("destination", destination);
        snapshot.put("startDate", startDate);
        snapshot.put("endDate", endDate);
        snapshot.put("isPostFacto", postFacto);
        snapshot.put("status", status);
        snapshot.put("deletedAt", deletedAt);
        return snapshot;
    }
}
