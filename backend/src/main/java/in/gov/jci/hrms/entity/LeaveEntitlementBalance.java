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

/**
 * One row per (employee, leave type, year) - richer than the pre-existing
 * leave_balances table (used by ordinary CL/RH/HPL application debits): this
 * table exists specifically for EL's encashable/enjoyable sub-ledger split
 * (baseline take-on, semi-annual accrual, Favorable Preservation consumption,
 * encashment). See V36's migration comment for why this is a new table
 * rather than an alteration of leave_balances.
 */
@Entity
@Table(name = "leave_entitlement_balance")
public class LeaveEntitlementBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leave_type_id", nullable = false)
    private LeaveType leaveType;

    @Column(name = "year", nullable = false)
    private Integer year;

    @Column(name = "opening_balance", nullable = false, precision = 5, scale = 2)
    private BigDecimal openingBalance = BigDecimal.ZERO;
    @Column(name = "credited_days", nullable = false, precision = 5, scale = 2)
    private BigDecimal creditedDays = BigDecimal.ZERO;
    @Column(name = "availed_days", nullable = false, precision = 5, scale = 2)
    private BigDecimal availedDays = BigDecimal.ZERO;
    @Column(name = "reserved_days", nullable = false, precision = 5, scale = 2)
    private BigDecimal reservedDays = BigDecimal.ZERO;
    @Column(name = "encashed_days", nullable = false, precision = 5, scale = 2)
    private BigDecimal encashedDays = BigDecimal.ZERO;
    @Column(name = "lapsed_days", nullable = false, precision = 5, scale = 2)
    private BigDecimal lapsedDays = BigDecimal.ZERO;
    @Column(name = "current_balance", nullable = false, precision = 5, scale = 2)
    private BigDecimal currentBalance = BigDecimal.ZERO;
    @Column(name = "available_balance", nullable = false, precision = 5, scale = 2)
    private BigDecimal availableBalance = BigDecimal.ZERO;

    @Column(name = "encashable_opening", nullable = false, precision = 5, scale = 2)
    private BigDecimal encashableOpening = BigDecimal.ZERO;
    @Column(name = "encashable_credited", nullable = false, precision = 5, scale = 2)
    private BigDecimal encashableCredited = BigDecimal.ZERO;
    @Column(name = "encashable_availed", nullable = false, precision = 5, scale = 2)
    private BigDecimal encashableAvailed = BigDecimal.ZERO;
    @Column(name = "encashable_reserved", nullable = false, precision = 5, scale = 2)
    private BigDecimal encashableReserved = BigDecimal.ZERO;
    @Column(name = "encashable_encashed", nullable = false, precision = 5, scale = 2)
    private BigDecimal encashableEncashed = BigDecimal.ZERO;
    @Column(name = "encashable_current", nullable = false, precision = 5, scale = 2)
    private BigDecimal encashableCurrent = BigDecimal.ZERO;
    @Column(name = "encashable_available", nullable = false, precision = 5, scale = 2)
    private BigDecimal encashableAvailable = BigDecimal.ZERO;

    @Column(name = "enjoyable_opening", nullable = false, precision = 5, scale = 2)
    private BigDecimal enjoyableOpening = BigDecimal.ZERO;
    @Column(name = "enjoyable_credited", nullable = false, precision = 5, scale = 2)
    private BigDecimal enjoyableCredited = BigDecimal.ZERO;
    @Column(name = "enjoyable_availed", nullable = false, precision = 5, scale = 2)
    private BigDecimal enjoyableAvailed = BigDecimal.ZERO;
    @Column(name = "enjoyable_reserved", nullable = false, precision = 5, scale = 2)
    private BigDecimal enjoyableReserved = BigDecimal.ZERO;
    @Column(name = "enjoyable_current", nullable = false, precision = 5, scale = 2)
    private BigDecimal enjoyableCurrent = BigDecimal.ZERO;
    @Column(name = "enjoyable_available", nullable = false, precision = 5, scale = 2)
    private BigDecimal enjoyableAvailable = BigDecimal.ZERO;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LeaveEntitlementBalance() {
    }

    public LeaveEntitlementBalance(Employee employee, LeaveType leaveType, Integer year) {
        this.employee = employee;
        this.leaveType = leaveType;
        this.year = year;
    }

    public Long getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public LeaveType getLeaveType() {
        return leaveType;
    }

    public Integer getYear() {
        return year;
    }

    public BigDecimal getOpeningBalance() {
        return openingBalance;
    }

    public void setOpeningBalance(BigDecimal openingBalance) {
        this.openingBalance = openingBalance;
    }

    public BigDecimal getCreditedDays() {
        return creditedDays;
    }

    public void setCreditedDays(BigDecimal creditedDays) {
        this.creditedDays = creditedDays;
    }

    public BigDecimal getAvailedDays() {
        return availedDays;
    }

    public void setAvailedDays(BigDecimal availedDays) {
        this.availedDays = availedDays;
    }

    public BigDecimal getReservedDays() {
        return reservedDays;
    }

    public void setReservedDays(BigDecimal reservedDays) {
        this.reservedDays = reservedDays;
    }

    public BigDecimal getEncashedDays() {
        return encashedDays;
    }

    public void setEncashedDays(BigDecimal encashedDays) {
        this.encashedDays = encashedDays;
    }

    public BigDecimal getLapsedDays() {
        return lapsedDays;
    }

    public void setLapsedDays(BigDecimal lapsedDays) {
        this.lapsedDays = lapsedDays;
    }

    public BigDecimal getCurrentBalance() {
        return currentBalance;
    }

    public void setCurrentBalance(BigDecimal currentBalance) {
        this.currentBalance = currentBalance;
    }

    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }

    public void setAvailableBalance(BigDecimal availableBalance) {
        this.availableBalance = availableBalance;
    }

    public BigDecimal getEncashableOpening() {
        return encashableOpening;
    }

    public void setEncashableOpening(BigDecimal encashableOpening) {
        this.encashableOpening = encashableOpening;
    }

    public BigDecimal getEncashableCredited() {
        return encashableCredited;
    }

    public void setEncashableCredited(BigDecimal encashableCredited) {
        this.encashableCredited = encashableCredited;
    }

    public BigDecimal getEncashableAvailed() {
        return encashableAvailed;
    }

    public void setEncashableAvailed(BigDecimal encashableAvailed) {
        this.encashableAvailed = encashableAvailed;
    }

    public BigDecimal getEncashableReserved() {
        return encashableReserved;
    }

    public void setEncashableReserved(BigDecimal encashableReserved) {
        this.encashableReserved = encashableReserved;
    }

    public BigDecimal getEncashableEncashed() {
        return encashableEncashed;
    }

    public void setEncashableEncashed(BigDecimal encashableEncashed) {
        this.encashableEncashed = encashableEncashed;
    }

    public BigDecimal getEncashableCurrent() {
        return encashableCurrent;
    }

    public void setEncashableCurrent(BigDecimal encashableCurrent) {
        this.encashableCurrent = encashableCurrent;
    }

    public BigDecimal getEncashableAvailable() {
        return encashableAvailable;
    }

    public void setEncashableAvailable(BigDecimal encashableAvailable) {
        this.encashableAvailable = encashableAvailable;
    }

    public BigDecimal getEnjoyableOpening() {
        return enjoyableOpening;
    }

    public void setEnjoyableOpening(BigDecimal enjoyableOpening) {
        this.enjoyableOpening = enjoyableOpening;
    }

    public BigDecimal getEnjoyableCredited() {
        return enjoyableCredited;
    }

    public void setEnjoyableCredited(BigDecimal enjoyableCredited) {
        this.enjoyableCredited = enjoyableCredited;
    }

    public BigDecimal getEnjoyableAvailed() {
        return enjoyableAvailed;
    }

    public void setEnjoyableAvailed(BigDecimal enjoyableAvailed) {
        this.enjoyableAvailed = enjoyableAvailed;
    }

    public BigDecimal getEnjoyableReserved() {
        return enjoyableReserved;
    }

    public void setEnjoyableReserved(BigDecimal enjoyableReserved) {
        this.enjoyableReserved = enjoyableReserved;
    }

    public BigDecimal getEnjoyableCurrent() {
        return enjoyableCurrent;
    }

    public void setEnjoyableCurrent(BigDecimal enjoyableCurrent) {
        this.enjoyableCurrent = enjoyableCurrent;
    }

    public BigDecimal getEnjoyableAvailable() {
        return enjoyableAvailable;
    }

    public void setEnjoyableAvailable(BigDecimal enjoyableAvailable) {
        this.enjoyableAvailable = enjoyableAvailable;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
