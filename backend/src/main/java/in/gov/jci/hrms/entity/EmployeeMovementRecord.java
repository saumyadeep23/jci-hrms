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
 * One employee's leg of a MovementOrder, from order through relieving through joining
 * verification/payroll sync. movementStatus tracks the physical lifecycle (ORDERED -&gt; RELIEVED
 * -&gt; JOINED, set as each step actually happens); joiningStatus tracks the joining report's own
 * approval workflow (NOT_SUBMITTED -&gt; PENDING_VERIFICATION -&gt; ACCEPTED/REJECTED) - the two are
 * deliberately separate, since a report can be PENDING_VERIFICATION while the employee has already
 * physically JOINED.
 */
@Entity
@Table(name = "employee_movement_records")
@EntityListeners(AuditableEntityListener.class)
public class EmployeeMovementRecord implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private MovementOrder order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "transfer_nature", nullable = false, length = 30)
    private TransferNature transferNature = TransferNature.ADMINISTRATIVE;

    @Column(name = "is_transfer_benefit_admissible", nullable = false)
    private boolean transferBenefitAdmissible = true;

    @Column(name = "request_application_ref", length = 100)
    private String requestApplicationRef;

    @Column(name = "request_reason", length = 255)
    private String requestReason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_office_id", nullable = false)
    private RegionalOffice fromOffice;

    /** V48 (additive, informational only) - set when fromOffice was resolved from a DPC's own parent RO; every office-dependent code path (payroll HRA, geofence, PDFs) reads fromOffice only and is unaffected by this. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_dpc_id")
    private DepartmentalPurchaseCentre fromDpc;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_department_id")
    private Department fromDepartment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_designation_id", nullable = false)
    private Designation fromDesignation;

    @Column(name = "from_pay_scale", length = 50)
    private String fromPayScale;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_office_id", nullable = false)
    private RegionalOffice toOffice;

    /** V48 (additive, informational only) - see fromDpc's javadoc. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_dpc_id")
    private DepartmentalPurchaseCentre toDpc;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_department_id")
    private Department toDepartment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_designation_id", nullable = false)
    private Designation toDesignation;

    @Column(name = "to_pay_scale", length = 50)
    private String toPayScale;

    @Column(name = "station_distance_km", nullable = false)
    private int stationDistanceKm;

    /** Concrete new basic pay for a PROMOTION/TRANSFER_CUM_PROMOTION order - see V46 migration comment. */
    @Column(name = "promotional_basic_pay", precision = 12, scale = 2)
    private BigDecimal promotionalBasicPay;

    @Column(name = "release_order_ref", length = 100)
    private String releaseOrderRef;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "release_session", length = 10)
    private SessionType releaseSession;

    @Column(name = "released_at_dbtimestamp")
    private Instant releasedAtDbTimestamp;

    @Column(name = "joining_report_no", length = 100)
    private String joiningReportNo;

    @Column(name = "joining_date")
    private LocalDate joiningDate;

    @Column(name = "joining_db_timestamp")
    private Instant joiningDbTimestamp;

    /** Never client-supplied - evaluated server-side from joiningDbTimestamp by JoiningReportService. */
    @Enumerated(EnumType.STRING)
    @Column(name = "joining_session", length = 10)
    private SessionType joiningSession;

    @Column(name = "joining_latitude", precision = 9, scale = 6)
    private BigDecimal joiningLatitude;

    @Column(name = "joining_longitude", precision = 9, scale = 6)
    private BigDecimal joiningLongitude;

    @Column(name = "joining_gps_accuracy", precision = 8, scale = 2)
    private BigDecimal joiningGpsAccuracy;

    @Column(name = "joining_distance_meters")
    private Double joiningDistanceMeters;

    @Column(name = "is_geo_verified", nullable = false)
    private boolean geoVerified = false;

    @Column(name = "submission_ip", length = 45)
    private String submissionIp;

    @Column(name = "joining_remarks")
    private String joiningRemarks;

    @Column(name = "admissible_jt_days", nullable = false)
    private int admissibleJtDays;

    @Column(name = "joining_time_availed_days", nullable = false)
    private int joiningTimeAvailedDays;

    @Column(name = "unavailed_jt_days", nullable = false)
    private int unavailedJtDays;

    @Column(name = "el_credited_days", nullable = false)
    private int elCreditedDays;

    @Column(name = "is_el_credited", nullable = false)
    private boolean elCredited = false;

    @Column(name = "leave_ledger_txn_id")
    private Long leaveLedgerTxnId;

    @Column(name = "probation_period_months", nullable = false)
    private int probationPeriodMonths;

    @Column(name = "probation_end_date")
    private LocalDate probationEndDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "payroll_sync_status", nullable = false, length = 30)
    private PayrollSyncStatus payrollSyncStatus = PayrollSyncStatus.PENDING;

    @Column(name = "lpc_number", length = 100)
    private String lpcNumber;

    @Column(name = "effective_pay_fixation_date")
    private LocalDate effectivePayFixationDate;

    @Column(name = "excess_transit_lwp_days", nullable = false)
    private int excessTransitLwpDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_status", nullable = false, length = 30)
    private MovementStatus movementStatus = MovementStatus.ORDERED;

    @Enumerated(EnumType.STRING)
    @Column(name = "joining_status", nullable = false, length = 30)
    private JoiningStatus joiningStatus = JoiningStatus.NOT_SUBMITTED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_officer_id")
    private Employee approvedByOfficer;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "clarification_remarks")
    private String clarificationRemarks;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clarification_requested_by")
    private Employee clarificationRequestedBy;

    @Column(name = "clarification_requested_at")
    private Instant clarificationRequestedAt;

    @Column(name = "resubmission_count", nullable = false)
    private int resubmissionCount;

    @Column(name = "resubmitted_at")
    private Instant resubmittedAt;

    @Column(name = "lpc_issue_date")
    private LocalDate lpcIssueDate;

    @Column(name = "lpc_signatory_name", length = 100)
    private String lpcSignatoryName;

    @Column(name = "lpc_signatory_designation", length = 100)
    private String lpcSignatoryDesignation;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EmployeeMovementRecord() {
    }

    public EmployeeMovementRecord(MovementOrder order, Employee employee, RegionalOffice fromOffice, Designation fromDesignation,
                                   RegionalOffice toOffice, Designation toDesignation) {
        this.order = order;
        this.employee = employee;
        this.fromOffice = fromOffice;
        this.fromDesignation = fromDesignation;
        this.toOffice = toOffice;
        this.toDesignation = toDesignation;
    }

    public Long getId() {
        return id;
    }

    public MovementOrder getOrder() {
        return order;
    }

    public Employee getEmployee() {
        return employee;
    }

    public TransferNature getTransferNature() {
        return transferNature;
    }

    public void setTransferNature(TransferNature transferNature) {
        this.transferNature = transferNature;
    }

    public boolean isTransferBenefitAdmissible() {
        return transferBenefitAdmissible;
    }

    public void setTransferBenefitAdmissible(boolean transferBenefitAdmissible) {
        this.transferBenefitAdmissible = transferBenefitAdmissible;
    }

    public String getRequestApplicationRef() {
        return requestApplicationRef;
    }

    public void setRequestApplicationRef(String requestApplicationRef) {
        this.requestApplicationRef = requestApplicationRef;
    }

    public String getRequestReason() {
        return requestReason;
    }

    public void setRequestReason(String requestReason) {
        this.requestReason = requestReason;
    }

    public RegionalOffice getFromOffice() {
        return fromOffice;
    }

    public DepartmentalPurchaseCentre getFromDpc() {
        return fromDpc;
    }

    public void setFromDpc(DepartmentalPurchaseCentre fromDpc) {
        this.fromDpc = fromDpc;
    }

    public Department getFromDepartment() {
        return fromDepartment;
    }

    public void setFromDepartment(Department fromDepartment) {
        this.fromDepartment = fromDepartment;
    }

    public Designation getFromDesignation() {
        return fromDesignation;
    }

    public String getFromPayScale() {
        return fromPayScale;
    }

    public void setFromPayScale(String fromPayScale) {
        this.fromPayScale = fromPayScale;
    }

    public RegionalOffice getToOffice() {
        return toOffice;
    }

    public DepartmentalPurchaseCentre getToDpc() {
        return toDpc;
    }

    public void setToDpc(DepartmentalPurchaseCentre toDpc) {
        this.toDpc = toDpc;
    }

    public Department getToDepartment() {
        return toDepartment;
    }

    public void setToDepartment(Department toDepartment) {
        this.toDepartment = toDepartment;
    }

    public Designation getToDesignation() {
        return toDesignation;
    }

    public String getToPayScale() {
        return toPayScale;
    }

    public void setToPayScale(String toPayScale) {
        this.toPayScale = toPayScale;
    }

    public int getStationDistanceKm() {
        return stationDistanceKm;
    }

    public void setStationDistanceKm(int stationDistanceKm) {
        this.stationDistanceKm = stationDistanceKm;
    }

    public BigDecimal getPromotionalBasicPay() {
        return promotionalBasicPay;
    }

    public void setPromotionalBasicPay(BigDecimal promotionalBasicPay) {
        this.promotionalBasicPay = promotionalBasicPay;
    }

    public String getReleaseOrderRef() {
        return releaseOrderRef;
    }

    public void setReleaseOrderRef(String releaseOrderRef) {
        this.releaseOrderRef = releaseOrderRef;
    }

    public LocalDate getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(LocalDate releaseDate) {
        this.releaseDate = releaseDate;
    }

    public SessionType getReleaseSession() {
        return releaseSession;
    }

    public void setReleaseSession(SessionType releaseSession) {
        this.releaseSession = releaseSession;
    }

    public Instant getReleasedAtDbTimestamp() {
        return releasedAtDbTimestamp;
    }

    public void setReleasedAtDbTimestamp(Instant releasedAtDbTimestamp) {
        this.releasedAtDbTimestamp = releasedAtDbTimestamp;
    }

    public String getJoiningReportNo() {
        return joiningReportNo;
    }

    public void setJoiningReportNo(String joiningReportNo) {
        this.joiningReportNo = joiningReportNo;
    }

    public LocalDate getJoiningDate() {
        return joiningDate;
    }

    public void setJoiningDate(LocalDate joiningDate) {
        this.joiningDate = joiningDate;
    }

    public Instant getJoiningDbTimestamp() {
        return joiningDbTimestamp;
    }

    public void setJoiningDbTimestamp(Instant joiningDbTimestamp) {
        this.joiningDbTimestamp = joiningDbTimestamp;
    }

    public SessionType getJoiningSession() {
        return joiningSession;
    }

    public void setJoiningSession(SessionType joiningSession) {
        this.joiningSession = joiningSession;
    }

    public BigDecimal getJoiningLatitude() {
        return joiningLatitude;
    }

    public void setJoiningLatitude(BigDecimal joiningLatitude) {
        this.joiningLatitude = joiningLatitude;
    }

    public BigDecimal getJoiningLongitude() {
        return joiningLongitude;
    }

    public void setJoiningLongitude(BigDecimal joiningLongitude) {
        this.joiningLongitude = joiningLongitude;
    }

    public BigDecimal getJoiningGpsAccuracy() {
        return joiningGpsAccuracy;
    }

    public void setJoiningGpsAccuracy(BigDecimal joiningGpsAccuracy) {
        this.joiningGpsAccuracy = joiningGpsAccuracy;
    }

    public Double getJoiningDistanceMeters() {
        return joiningDistanceMeters;
    }

    public void setJoiningDistanceMeters(Double joiningDistanceMeters) {
        this.joiningDistanceMeters = joiningDistanceMeters;
    }

    public boolean isGeoVerified() {
        return geoVerified;
    }

    public void setGeoVerified(boolean geoVerified) {
        this.geoVerified = geoVerified;
    }

    public String getSubmissionIp() {
        return submissionIp;
    }

    public void setSubmissionIp(String submissionIp) {
        this.submissionIp = submissionIp;
    }

    public String getJoiningRemarks() {
        return joiningRemarks;
    }

    public void setJoiningRemarks(String joiningRemarks) {
        this.joiningRemarks = joiningRemarks;
    }

    public int getAdmissibleJtDays() {
        return admissibleJtDays;
    }

    public void setAdmissibleJtDays(int admissibleJtDays) {
        this.admissibleJtDays = admissibleJtDays;
    }

    public int getJoiningTimeAvailedDays() {
        return joiningTimeAvailedDays;
    }

    public void setJoiningTimeAvailedDays(int joiningTimeAvailedDays) {
        this.joiningTimeAvailedDays = joiningTimeAvailedDays;
    }

    public int getUnavailedJtDays() {
        return unavailedJtDays;
    }

    public void setUnavailedJtDays(int unavailedJtDays) {
        this.unavailedJtDays = unavailedJtDays;
    }

    public int getElCreditedDays() {
        return elCreditedDays;
    }

    public void setElCreditedDays(int elCreditedDays) {
        this.elCreditedDays = elCreditedDays;
    }

    public boolean isElCredited() {
        return elCredited;
    }

    public void setElCredited(boolean elCredited) {
        this.elCredited = elCredited;
    }

    public Long getLeaveLedgerTxnId() {
        return leaveLedgerTxnId;
    }

    public void setLeaveLedgerTxnId(Long leaveLedgerTxnId) {
        this.leaveLedgerTxnId = leaveLedgerTxnId;
    }

    public int getProbationPeriodMonths() {
        return probationPeriodMonths;
    }

    public void setProbationPeriodMonths(int probationPeriodMonths) {
        this.probationPeriodMonths = probationPeriodMonths;
    }

    public LocalDate getProbationEndDate() {
        return probationEndDate;
    }

    public void setProbationEndDate(LocalDate probationEndDate) {
        this.probationEndDate = probationEndDate;
    }

    public PayrollSyncStatus getPayrollSyncStatus() {
        return payrollSyncStatus;
    }

    public void setPayrollSyncStatus(PayrollSyncStatus payrollSyncStatus) {
        this.payrollSyncStatus = payrollSyncStatus;
    }

    public String getLpcNumber() {
        return lpcNumber;
    }

    public void setLpcNumber(String lpcNumber) {
        this.lpcNumber = lpcNumber;
    }

    public LocalDate getEffectivePayFixationDate() {
        return effectivePayFixationDate;
    }

    public void setEffectivePayFixationDate(LocalDate effectivePayFixationDate) {
        this.effectivePayFixationDate = effectivePayFixationDate;
    }

    public int getExcessTransitLwpDays() {
        return excessTransitLwpDays;
    }

    public void setExcessTransitLwpDays(int excessTransitLwpDays) {
        this.excessTransitLwpDays = excessTransitLwpDays;
    }

    public MovementStatus getMovementStatus() {
        return movementStatus;
    }

    public void setMovementStatus(MovementStatus movementStatus) {
        this.movementStatus = movementStatus;
    }

    public JoiningStatus getJoiningStatus() {
        return joiningStatus;
    }

    public void setJoiningStatus(JoiningStatus joiningStatus) {
        this.joiningStatus = joiningStatus;
    }

    public Employee getApprovedByOfficer() {
        return approvedByOfficer;
    }

    public void setApprovedByOfficer(Employee approvedByOfficer) {
        this.approvedByOfficer = approvedByOfficer;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }

    public String getClarificationRemarks() {
        return clarificationRemarks;
    }

    public void setClarificationRemarks(String clarificationRemarks) {
        this.clarificationRemarks = clarificationRemarks;
    }

    public Employee getClarificationRequestedBy() {
        return clarificationRequestedBy;
    }

    public void setClarificationRequestedBy(Employee clarificationRequestedBy) {
        this.clarificationRequestedBy = clarificationRequestedBy;
    }

    public Instant getClarificationRequestedAt() {
        return clarificationRequestedAt;
    }

    public void setClarificationRequestedAt(Instant clarificationRequestedAt) {
        this.clarificationRequestedAt = clarificationRequestedAt;
    }

    public int getResubmissionCount() {
        return resubmissionCount;
    }

    public void setResubmissionCount(int resubmissionCount) {
        this.resubmissionCount = resubmissionCount;
    }

    public Instant getResubmittedAt() {
        return resubmittedAt;
    }

    public void setResubmittedAt(Instant resubmittedAt) {
        this.resubmittedAt = resubmittedAt;
    }

    public LocalDate getLpcIssueDate() {
        return lpcIssueDate;
    }

    public void setLpcIssueDate(LocalDate lpcIssueDate) {
        this.lpcIssueDate = lpcIssueDate;
    }

    public String getLpcSignatoryName() {
        return lpcSignatoryName;
    }

    public void setLpcSignatoryName(String lpcSignatoryName) {
        this.lpcSignatoryName = lpcSignatoryName;
    }

    public String getLpcSignatoryDesignation() {
        return lpcSignatoryDesignation;
    }

    public void setLpcSignatoryDesignation(String lpcSignatoryDesignation) {
        this.lpcSignatoryDesignation = lpcSignatoryDesignation;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String auditEntityName() {
        return "EmployeeMovementRecord";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("employeeId", employee != null ? employee.getId() : null);
        snapshot.put("orderId", order != null ? order.getId() : null);
        snapshot.put("movementStatus", movementStatus);
        snapshot.put("joiningStatus", joiningStatus);
        snapshot.put("payrollSyncStatus", payrollSyncStatus);
        snapshot.put("isElCredited", elCredited);
        return snapshot;
    }
}
