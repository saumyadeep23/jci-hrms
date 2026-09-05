package in.gov.jci.hrms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.util.UUID;

/**
 * One-time, HR-verified opening balance as of go-live/an employee's joining
 * date, taken from the physical service book before the digital
 * leave_entitlement_balance ledger takes over. is_locked defaults true -
 * see V36's migration comment. No soft-delete: a verified baseline is a
 * permanent audit record, not something to hide/undo by flipping a flag.
 */
@Entity
@Table(name = "leave_baseline_initialization")
public class LeaveBaselineInitialization {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "baseline_id")
    private UUID baselineId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leave_type_id", nullable = false)
    private LeaveType leaveType;

    @Column(name = "as_on_date", nullable = false)
    private LocalDate asOnDate;

    @Column(name = "opening_balance", nullable = false, precision = 5, scale = 2)
    private BigDecimal openingBalance;

    @Column(name = "opening_encashable_el", nullable = false, precision = 5, scale = 2)
    private BigDecimal openingEncashableEl = BigDecimal.ZERO;

    @Column(name = "opening_enjoyable_el", nullable = false, precision = 5, scale = 2)
    private BigDecimal openingEnjoyableEl = BigDecimal.ZERO;

    @Column(name = "physical_service_book_folio", length = 50)
    private String physicalServiceBookFolio;

    @Column(name = "verification_order_ref", length = 100)
    private String verificationOrderRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by")
    private Employee verifiedBy;

    @Column(name = "is_locked", nullable = false)
    private boolean locked = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LeaveBaselineInitialization() {
    }

    public LeaveBaselineInitialization(Employee employee, LeaveType leaveType, LocalDate asOnDate, BigDecimal openingBalance,
                                        BigDecimal openingEncashableEl, BigDecimal openingEnjoyableEl) {
        this.employee = employee;
        this.leaveType = leaveType;
        this.asOnDate = asOnDate;
        this.openingBalance = openingBalance;
        this.openingEncashableEl = openingEncashableEl != null ? openingEncashableEl : BigDecimal.ZERO;
        this.openingEnjoyableEl = openingEnjoyableEl != null ? openingEnjoyableEl : BigDecimal.ZERO;
    }

    public UUID getBaselineId() {
        return baselineId;
    }

    public Employee getEmployee() {
        return employee;
    }

    public LeaveType getLeaveType() {
        return leaveType;
    }

    public LocalDate getAsOnDate() {
        return asOnDate;
    }

    public BigDecimal getOpeningBalance() {
        return openingBalance;
    }

    public BigDecimal getOpeningEncashableEl() {
        return openingEncashableEl;
    }

    public BigDecimal getOpeningEnjoyableEl() {
        return openingEnjoyableEl;
    }

    public String getPhysicalServiceBookFolio() {
        return physicalServiceBookFolio;
    }

    public void setPhysicalServiceBookFolio(String physicalServiceBookFolio) {
        this.physicalServiceBookFolio = physicalServiceBookFolio;
    }

    public String getVerificationOrderRef() {
        return verificationOrderRef;
    }

    public void setVerificationOrderRef(String verificationOrderRef) {
        this.verificationOrderRef = verificationOrderRef;
    }

    public Employee getVerifiedBy() {
        return verifiedBy;
    }

    public void setVerifiedBy(Employee verifiedBy) {
        this.verifiedBy = verifiedBy;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
