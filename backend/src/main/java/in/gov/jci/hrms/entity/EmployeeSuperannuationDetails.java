package in.gov.jci.hrms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One row per employee, maintained by the DB (fn_calculate_jci_
 * superannuation_date trigger on employees.date_of_birth, and
 * sp_apply_director_ministry_extension) - see V31 migration. Read-only from
 * the application's point of view except for the Director-specific flags
 * (isBoardDirector/directorAppointmentDate), which HR sets explicitly and
 * the trigger then reads back on its next run.
 */
@Entity
@Table(name = "employee_superannuation_details")
public class EmployeeSuperannuationDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, unique = true)
    private Employee employee;

    @Column(name = "superannuation_date", nullable = false)
    private LocalDate superannuationDate;

    @Column(name = "retirement_type", nullable = false, length = 50)
    private String retirementType = "SUPERANNUATION";

    @Column(name = "is_board_director", nullable = false)
    private boolean boardDirector = false;

    @Column(name = "director_appointment_date")
    private LocalDate directorAppointmentDate;

    @Column(name = "initial_term_expiry_date")
    private LocalDate initialTermExpiryDate;

    @Column(name = "is_ministry_extended", nullable = false)
    private boolean ministryExtended = false;

    @Column(name = "ministry_extension_order_no", length = 100)
    private String ministryExtensionOrderNo;

    @Column(name = "ministry_extension_order_date")
    private LocalDate ministryExtensionOrderDate;

    @Column(name = "ministry_extended_upto")
    private LocalDate ministryExtendedUpto;

    @Column(name = "calculation_basis", length = 100)
    private String calculationBasis;

    @Column(name = "retirement_order_no", length = 100)
    private String retirementOrderNo;

    @Column(name = "actual_retirement_date")
    private LocalDate actualRetirementDate;

    @Column(name = "pension_settlement_status", nullable = false, length = 50)
    private String pensionSettlementStatus = "NOT_DUE";

    @Column(name = "gratuity_settlement_status", nullable = false, length = 50)
    private String gratuitySettlementStatus = "NOT_DUE";

    @Column(name = "leave_encashment_days")
    private Integer leaveEncashmentDays = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EmployeeSuperannuationDetails() {
    }

    public Long getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public LocalDate getSuperannuationDate() {
        return superannuationDate;
    }

    public String getRetirementType() {
        return retirementType;
    }

    public boolean isBoardDirector() {
        return boardDirector;
    }

    public void setBoardDirector(boolean boardDirector) {
        this.boardDirector = boardDirector;
    }

    public LocalDate getDirectorAppointmentDate() {
        return directorAppointmentDate;
    }

    public void setDirectorAppointmentDate(LocalDate directorAppointmentDate) {
        this.directorAppointmentDate = directorAppointmentDate;
    }

    public LocalDate getInitialTermExpiryDate() {
        return initialTermExpiryDate;
    }

    public boolean isMinistryExtended() {
        return ministryExtended;
    }

    public String getMinistryExtensionOrderNo() {
        return ministryExtensionOrderNo;
    }

    public LocalDate getMinistryExtensionOrderDate() {
        return ministryExtensionOrderDate;
    }

    public LocalDate getMinistryExtendedUpto() {
        return ministryExtendedUpto;
    }

    public String getCalculationBasis() {
        return calculationBasis;
    }

    public String getRetirementOrderNo() {
        return retirementOrderNo;
    }

    public LocalDate getActualRetirementDate() {
        return actualRetirementDate;
    }

    public String getPensionSettlementStatus() {
        return pensionSettlementStatus;
    }

    public String getGratuitySettlementStatus() {
        return gratuitySettlementStatus;
    }

    public Integer getLeaveEncashmentDays() {
        return leaveEncashmentDays;
    }
}
