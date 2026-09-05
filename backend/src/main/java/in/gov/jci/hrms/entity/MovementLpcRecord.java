package in.gov.jci.hrms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.time.LocalDate;

/**
 * The immutable line-item breakdown behind one movement's Last Pay Certificate - generated once
 * (LastPayCertificateService.getOrCreate(), enforced by the uq_movement_lpc_records_movement_id
 * unique constraint) and re-printable exactly as originally certified from then on, regardless of
 * how the employee's live payroll figures change afterward.
 */
@Entity
@Table(name = "movement_lpc_records")
public class MovementLpcRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movement_id", nullable = false)
    private EmployeeMovementRecord movement;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "lpc_number", nullable = false, length = 100)
    private String lpcNumber;

    @Column(name = "rate_of_pay_basic", nullable = false, precision = 12, scale = 2)
    private BigDecimal rateOfPayBasic;

    @Column(name = "rate_of_da", nullable = false, precision = 12, scale = 2)
    private BigDecimal rateOfDa;

    @Column(name = "rate_of_hra", nullable = false, precision = 12, scale = 2)
    private BigDecimal rateOfHra;

    @Column(name = "rate_of_special_allowance", nullable = false, precision = 12, scale = 2)
    private BigDecimal rateOfSpecialAllowance = BigDecimal.ZERO;

    @Column(name = "cpf_subscription", nullable = false, precision = 12, scale = 2)
    private BigDecimal cpfSubscription;

    @Column(name = "cpf_advance_balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal cpfAdvanceBalance = BigDecimal.ZERO;

    @Column(name = "festival_advance_balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal festivalAdvanceBalance = BigDecimal.ZERO;

    @Column(name = "el_balance_days", nullable = false)
    private int elBalanceDays;

    @Column(name = "hpl_balance_days", nullable = false)
    private int hplBalanceDays;

    @Column(name = "pay_drawn_upto_date", nullable = false)
    private LocalDate payDrawnUptoDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "pay_drawn_session", nullable = false, length = 10)
    private SessionType payDrawnSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generated_by")
    private Employee generatedBy;

    @CreationTimestamp
    @Column(name = "generated_at", nullable = false, updatable = false)
    private Instant generatedAt;

    protected MovementLpcRecord() {
    }

    public MovementLpcRecord(EmployeeMovementRecord movement, Employee employee, String lpcNumber,
                              BigDecimal rateOfPayBasic, BigDecimal rateOfDa, BigDecimal rateOfHra, BigDecimal cpfSubscription,
                              int elBalanceDays, int hplBalanceDays, LocalDate payDrawnUptoDate, SessionType payDrawnSession) {
        this.movement = movement;
        this.employee = employee;
        this.lpcNumber = lpcNumber;
        this.rateOfPayBasic = rateOfPayBasic;
        this.rateOfDa = rateOfDa;
        this.rateOfHra = rateOfHra;
        this.cpfSubscription = cpfSubscription;
        this.elBalanceDays = elBalanceDays;
        this.hplBalanceDays = hplBalanceDays;
        this.payDrawnUptoDate = payDrawnUptoDate;
        this.payDrawnSession = payDrawnSession;
    }

    public Long getId() {
        return id;
    }

    public EmployeeMovementRecord getMovement() {
        return movement;
    }

    public Employee getEmployee() {
        return employee;
    }

    public String getLpcNumber() {
        return lpcNumber;
    }

    public BigDecimal getRateOfPayBasic() {
        return rateOfPayBasic;
    }

    public BigDecimal getRateOfDa() {
        return rateOfDa;
    }

    public BigDecimal getRateOfHra() {
        return rateOfHra;
    }

    public BigDecimal getRateOfSpecialAllowance() {
        return rateOfSpecialAllowance;
    }

    public void setRateOfSpecialAllowance(BigDecimal rateOfSpecialAllowance) {
        if (rateOfSpecialAllowance != null) {
            this.rateOfSpecialAllowance = rateOfSpecialAllowance;
        }
    }

    public BigDecimal getCpfSubscription() {
        return cpfSubscription;
    }

    public BigDecimal getCpfAdvanceBalance() {
        return cpfAdvanceBalance;
    }

    public void setCpfAdvanceBalance(BigDecimal cpfAdvanceBalance) {
        if (cpfAdvanceBalance != null) {
            this.cpfAdvanceBalance = cpfAdvanceBalance;
        }
    }

    public BigDecimal getFestivalAdvanceBalance() {
        return festivalAdvanceBalance;
    }

    public void setFestivalAdvanceBalance(BigDecimal festivalAdvanceBalance) {
        if (festivalAdvanceBalance != null) {
            this.festivalAdvanceBalance = festivalAdvanceBalance;
        }
    }

    public int getElBalanceDays() {
        return elBalanceDays;
    }

    public int getHplBalanceDays() {
        return hplBalanceDays;
    }

    public LocalDate getPayDrawnUptoDate() {
        return payDrawnUptoDate;
    }

    public SessionType getPayDrawnSession() {
        return payDrawnSession;
    }

    public Employee getGeneratedBy() {
        return generatedBy;
    }

    public void setGeneratedBy(Employee generatedBy) {
        this.generatedBy = generatedBy;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }
}
