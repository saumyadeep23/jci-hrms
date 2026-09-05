package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.EmployeeMovementRecordResponse;
import in.gov.jci.hrms.dto.JoiningDecisionRequest;
import in.gov.jci.hrms.dto.JoiningReportRequest;
import in.gov.jci.hrms.dto.MovementReleaseRequest;
import in.gov.jci.hrms.dto.ServiceBookEventRequest;
import in.gov.jci.hrms.entity.CareerEventType;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeMovementRecord;
import in.gov.jci.hrms.entity.JoiningStatus;
import in.gov.jci.hrms.entity.LeaveEntitlementBalance;
import in.gov.jci.hrms.entity.LeaveLedgerEntry;
import in.gov.jci.hrms.entity.LeaveLedgerSource;
import in.gov.jci.hrms.entity.LeaveType;
import in.gov.jci.hrms.entity.MovementOrderType;
import in.gov.jci.hrms.entity.MovementStatus;
import in.gov.jci.hrms.entity.PayrollSyncStatus;
import in.gov.jci.hrms.entity.RegionalOffice;
import in.gov.jci.hrms.entity.SessionType;
import in.gov.jci.hrms.entity.TransferNature;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.EmployeeMovementRecordRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.LeaveEntitlementBalanceRepository;
import in.gov.jci.hrms.repository.LeaveLedgerEntryRepository;
import in.gov.jci.hrms.repository.LeaveTypeRepository;
import in.gov.jci.hrms.service.pdf.ReleaseOrderPdfGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Owns the movement lifecycle from relieving through joining-report verification: Database-Clock
 * FN/AN session evaluation (never client-supplied), Soft GPS verification against the destination
 * office's geofence, the unavailed-Joining-Time -&gt; EL-credit conversion (300-day ceiling), and
 * orchestrating the e-Service Book (EmployeeServiceBookService) and payroll (
 * PayrollMovementIntegrationService) side effects that fire on approval.
 */
@Service
@Transactional(readOnly = true)
public class JoiningReportService {

    /** All employees are physically in India - the DB-clock session cutoff is evaluated in IST regardless of the submitting device/app-server's own timezone. */
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalTime SESSION_CUTOFF = LocalTime.of(13, 0);
    private static final String EL_CODE = "EL";
    private static final BigDecimal EL_CEILING = BigDecimal.valueOf(300);

    private final EmployeeMovementRecordRepository movementRecordRepository;
    private final EmployeeRepository employeeRepository;
    private final DbClockService dbClockService;
    private final JoiningTimeCalculatorService joiningTimeCalculatorService;
    private final GeofenceService geofenceService;
    private final EmployeeServiceBookService employeeServiceBookService;
    private final PayrollMovementIntegrationService payrollMovementIntegrationService;
    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveEntitlementBalanceRepository entitlementBalanceRepository;
    private final LeaveLedgerEntryRepository leaveLedgerEntryRepository;
    private final ReleaseOrderPdfGenerator releaseOrderPdfGenerator;

    public JoiningReportService(EmployeeMovementRecordRepository movementRecordRepository, EmployeeRepository employeeRepository,
                                 DbClockService dbClockService, JoiningTimeCalculatorService joiningTimeCalculatorService,
                                 GeofenceService geofenceService, EmployeeServiceBookService employeeServiceBookService,
                                 PayrollMovementIntegrationService payrollMovementIntegrationService,
                                 LeaveTypeRepository leaveTypeRepository, LeaveEntitlementBalanceRepository entitlementBalanceRepository,
                                 LeaveLedgerEntryRepository leaveLedgerEntryRepository, ReleaseOrderPdfGenerator releaseOrderPdfGenerator) {
        this.movementRecordRepository = movementRecordRepository;
        this.employeeRepository = employeeRepository;
        this.dbClockService = dbClockService;
        this.joiningTimeCalculatorService = joiningTimeCalculatorService;
        this.geofenceService = geofenceService;
        this.employeeServiceBookService = employeeServiceBookService;
        this.releaseOrderPdfGenerator = releaseOrderPdfGenerator;
        this.payrollMovementIntegrationService = payrollMovementIntegrationService;
        this.leaveTypeRepository = leaveTypeRepository;
        this.entitlementBalanceRepository = entitlementBalanceRepository;
        this.leaveLedgerEntryRepository = leaveLedgerEntryRepository;
    }

    public List<EmployeeMovementRecordResponse> pendingReleases() {
        return movementRecordRepository.findByMovementStatusOrderByCreatedAtAsc(MovementStatus.ORDERED).stream()
                .map(EmployeeMovementRecordResponse::from).toList();
    }

    public List<EmployeeMovementRecordResponse> pendingJoiningVerifications() {
        return movementRecordRepository.findByJoiningStatusOrderByJoiningDbTimestampAsc(JoiningStatus.PENDING_VERIFICATION).stream()
                .map(EmployeeMovementRecordResponse::from).toList();
    }

    public List<EmployeeMovementRecordResponse> mine(Long employeeId) {
        return movementRecordRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId).stream()
                .map(EmployeeMovementRecordResponse::from).toList();
    }

    @Transactional
    public EmployeeMovementRecordResponse release(Long movementId, MovementReleaseRequest request) {
        EmployeeMovementRecord record = findOrThrow(movementId);
        if (record.getMovementStatus() != MovementStatus.ORDERED) {
            throw new BusinessRuleViolationException(
                    "Movement " + movementId + " is not pending release (status: " + record.getMovementStatus() + ")");
        }

        record.setReleaseOrderRef(request.releaseOrderRef());
        record.setReleaseDate(request.releaseDate());
        record.setReleaseSession(request.releaseSession());
        record.setReleasedAtDbTimestamp(dbClockService.now());
        record.setMovementStatus(MovementStatus.RELIEVED);

        employeeServiceBookService.recordEvent(record.getEmployee().getId(), releaseServiceBookEvent(record));
        return EmployeeMovementRecordResponse.from(record);
    }

    /**
     * ESS self-service submission. joiningDate/session/dbTimestamp are captured from the database
     * clock here - request never carries them, so a client can't backdate a submission or force a
     * particular FN/AN outcome.
     */
    @Transactional
    public EmployeeMovementRecordResponse submitJoiningReport(Long movementId, JoiningReportRequest request,
                                                                Long callerEmployeeId, String submissionIp) {
        EmployeeMovementRecord record = findOrThrow(movementId);
        if (callerEmployeeId != null && !callerEmployeeId.equals(record.getEmployee().getId())) {
            throw new BusinessRuleViolationException("You may only submit a joining report for your own movement record");
        }
        if (record.getMovementStatus() != MovementStatus.RELIEVED) {
            throw new BusinessRuleViolationException("Movement " + movementId + " has not been relieved yet");
        }
        if (record.getJoiningStatus() != JoiningStatus.NOT_SUBMITTED) {
            throw new BusinessRuleViolationException("A joining report has already been submitted for movement " + movementId);
        }
        if (movementRecordRepository.existsByJoiningReportNo(request.joiningReportNo())) {
            throw new MasterDataConflictException("A joining report with number " + request.joiningReportNo() + " already exists.");
        }

        applyJoiningSubmissionFields(record, request, submissionIp);
        record.setMovementStatus(MovementStatus.JOINED);
        record.setJoiningStatus(JoiningStatus.PENDING_VERIFICATION);

        return EmployeeMovementRecordResponse.from(record);
    }

    /**
     * Officer sends a PENDING_VERIFICATION report back to the employee for amendment - e.g. GPS
     * coordinates didn't corroborate the claimed destination office, or the remarks need more
     * detail. Never mutates the joining data itself; that only happens on resubmitJoiningReport().
     */
    @Transactional
    public EmployeeMovementRecordResponse requestClarification(Long movementId, String remarks, Long supervisorEmployeeId) {
        EmployeeMovementRecord record = findOrThrow(movementId);
        if (record.getJoiningStatus() != JoiningStatus.PENDING_VERIFICATION) {
            throw new BusinessRuleViolationException(
                    "Movement " + movementId + " has no pending joining report to seek clarification on (status: " + record.getJoiningStatus() + ")");
        }
        if (remarks == null || remarks.isBlank()) {
            throw new BusinessRuleViolationException("Clarification remarks are required");
        }

        record.setJoiningStatus(JoiningStatus.CLARIFICATION_REQUESTED);
        record.setClarificationRemarks(remarks);
        record.setClarificationRequestedBy(
                supervisorEmployeeId != null ? employeeRepository.findById(supervisorEmployeeId).orElse(null) : null);
        record.setClarificationRequestedAt(Instant.now());

        return EmployeeMovementRecordResponse.from(record);
    }

    /**
     * ESS self-service amendment of a CLARIFICATION_REQUESTED report - re-captures the database
     * clock timestamp/session and soft-GPS exactly like the original submission (an amended report
     * gets a fresh, independently-evaluated session/timestamp, not the original one carried over),
     * and returns the record to PENDING_VERIFICATION for another review pass.
     */
    @Transactional
    public EmployeeMovementRecordResponse resubmitJoiningReport(Long movementId, JoiningReportRequest request,
                                                                  Long callerEmployeeId, String submissionIp) {
        EmployeeMovementRecord record = findOrThrow(movementId);
        if (callerEmployeeId != null && !callerEmployeeId.equals(record.getEmployee().getId())) {
            throw new BusinessRuleViolationException("You may only resubmit a joining report for your own movement record");
        }
        if (record.getJoiningStatus() != JoiningStatus.CLARIFICATION_REQUESTED) {
            throw new BusinessRuleViolationException(
                    "Movement " + movementId + " is not awaiting a clarification resubmission (status: " + record.getJoiningStatus() + ")");
        }
        if (!request.joiningReportNo().equals(record.getJoiningReportNo())
                && movementRecordRepository.existsByJoiningReportNo(request.joiningReportNo())) {
            throw new MasterDataConflictException("A joining report with number " + request.joiningReportNo() + " already exists.");
        }

        applyJoiningSubmissionFields(record, request, submissionIp);
        record.setResubmissionCount(record.getResubmissionCount() + 1);
        record.setResubmittedAt(Instant.now());
        record.setJoiningStatus(JoiningStatus.PENDING_VERIFICATION);

        return EmployeeMovementRecordResponse.from(record);
    }

    /** Shared by submitJoiningReport() and resubmitJoiningReport(): DB-clock session evaluation, soft-GPS, and JT day math. */
    private void applyJoiningSubmissionFields(EmployeeMovementRecord record, JoiningReportRequest request, String submissionIp) {
        Instant dbNow = dbClockService.now();
        LocalTime istTime = dbNow.atZone(IST).toLocalTime();
        SessionType session = istTime.isBefore(SESSION_CUTOFF) ? SessionType.FORENOON : SessionType.AFTERNOON;
        LocalDate joiningDate = dbNow.atZone(IST).toLocalDate();

        record.setJoiningReportNo(request.joiningReportNo());
        record.setJoiningDate(joiningDate);
        record.setJoiningDbTimestamp(dbNow);
        record.setJoiningSession(session);
        record.setJoiningLatitude(request.latitude());
        record.setJoiningLongitude(request.longitude());
        record.setJoiningGpsAccuracy(request.accuracyMeters());
        record.setSubmissionIp(submissionIp);
        record.setJoiningRemarks(request.remarks());
        applySoftGeoVerification(record, request);

        int availedDays = record.getReleaseDate() != null
                ? (int) Math.max(0, ChronoUnit.DAYS.between(record.getReleaseDate(), joiningDate))
                : 0;
        record.setJoiningTimeAvailedDays(availedDays);
        record.setUnavailedJtDays(joiningTimeCalculatorService.computeUnavailedJtDays(record.getAdmissibleJtDays(), availedDays));
        record.setExcessTransitLwpDays(joiningTimeCalculatorService.computeExcessTransitLwpDays(record.getAdmissibleJtDays(), availedDays));
    }

    /**
     * Soft: distance/verified are informational only, never a hard gate on submission (a desktop
     * ESS session with no GPS chip, or one where the browser's permission prompt was denied, must
     * still be able to submit - is_geo_verified just tells the reviewing officer whether the
     * coordinates corroborate the claimed destination office).
     */
    private void applySoftGeoVerification(EmployeeMovementRecord record, JoiningReportRequest request) {
        RegionalOffice toOffice = record.getToOffice();
        if (request.latitude() == null || request.longitude() == null
                || toOffice.getLatitude() == null || toOffice.getLongitude() == null) {
            record.setGeoVerified(false);
            return;
        }
        double distanceMeters = geofenceService.distanceMeters(toOffice.getLatitude(), toOffice.getLongitude(),
                request.latitude(), request.longitude());
        record.setJoiningDistanceMeters(distanceMeters);
        record.setGeoVerified(distanceMeters <= toOffice.getGeofenceRadiusMeters().doubleValue());
    }

    @Transactional
    public EmployeeMovementRecordResponse decide(Long movementId, JoiningDecisionRequest decision, Long approverEmployeeId) {
        EmployeeMovementRecord record = findOrThrow(movementId);
        if (record.getJoiningStatus() != JoiningStatus.PENDING_VERIFICATION) {
            throw new BusinessRuleViolationException("Movement " + movementId + " has no pending joining report to decide");
        }

        Employee approver = approverEmployeeId != null ? employeeRepository.findById(approverEmployeeId).orElse(null) : null;
        record.setApprovedByOfficer(approver);
        record.setApprovedAt(Instant.now());
        if (decision.remarks() != null) {
            record.setJoiningRemarks(decision.remarks());
        }

        if (Boolean.TRUE.equals(decision.approve())) {
            record.setJoiningStatus(JoiningStatus.ACCEPTED);
            approveJoiningReport(record);
        } else {
            record.setJoiningStatus(JoiningStatus.REJECTED);
        }
        return EmployeeMovementRecordResponse.from(record);
    }

    @Transactional
    public EmployeeMovementRecordResponse acceptLpc(Long movementId) {
        EmployeeMovementRecord record = findOrThrow(movementId);
        if (record.getPayrollSyncStatus() != PayrollSyncStatus.LPC_ISSUED) {
            throw new BusinessRuleViolationException(
                    "Movement " + movementId + " has no issued LPC pending Finance acceptance (status: " + record.getPayrollSyncStatus() + ")");
        }
        record.setPayrollSyncStatus(PayrollSyncStatus.LPC_ACCEPTED);
        return EmployeeMovementRecordResponse.from(record);
    }

    private void approveJoiningReport(EmployeeMovementRecord record) {
        recordJoiningServiceBookEvent(record);
        if (record.getOrder().getOrderType() != MovementOrderType.TRANSFER) {
            applyPromotion(record);
        }
        creditUnavailedJoiningTime(record);

        payrollMovementIntegrationService.generateInputs(record);
        record.setLpcNumber("LPC/" + record.getId() + "/" + record.getJoiningDate().getYear());
        record.setPayrollSyncStatus(PayrollSyncStatus.LPC_ISSUED);
        record.setEffectivePayFixationDate(record.getJoiningDate());
    }

    private void recordJoiningServiceBookEvent(EmployeeMovementRecord record) {
        String remarks = "Joined at " + record.getToOffice().getName() + " on " + record.getJoiningDate()
                + " (" + record.getJoiningSession() + "); nature " + record.getTransferNature()
                + (record.isTransferBenefitAdmissible() ? " with transfer benefit" : " without transfer benefit");
        ServiceBookEventRequest joining = new ServiceBookEventRequest(record.getJoiningDate(), CareerEventType.TRANSFER_JOINING,
                record.getJoiningReportNo(), record.getOrder().getOrderDate(),
                record.getToDepartment() != null ? record.getToDepartment().getId() : null,
                record.getToDesignation().getId(), record.getToOffice().getId(), null, null, remarks);
        employeeServiceBookService.recordEvent(record.getEmployee().getId(), joining);
    }

    private void applyPromotion(EmployeeMovementRecord record) {
        record.setProbationEndDate(record.getJoiningDate().plusMonths(Math.max(record.getProbationPeriodMonths(), 0)));
        String remarks = "Promoted to " + record.getToDesignation().getTitle()
                + (record.getToPayScale() != null ? ", revised pay scale " + record.getToPayScale() : "")
                + "; probation " + record.getProbationPeriodMonths() + " months ending " + record.getProbationEndDate();
        ServiceBookEventRequest promotion = new ServiceBookEventRequest(record.getJoiningDate(), CareerEventType.PROMOTION,
                record.getOrder().getOrderRefNo(), record.getOrder().getOrderDate(),
                record.getToDepartment() != null ? record.getToDepartment().getId() : null,
                record.getToDesignation().getId(), record.getToOffice().getId(), null, record.getPromotionalBasicPay(), remarks);
        employeeServiceBookService.recordEvent(record.getEmployee().getId(), promotion);

        if (record.getPromotionalBasicPay() != null) {
            String fixationRemarks = "Pay fixation on promotion effective " + record.getJoiningDate()
                    + " (" + record.getJoiningSession() + ") for payroll verification";
            ServiceBookEventRequest fixation = new ServiceBookEventRequest(record.getJoiningDate(), CareerEventType.PAY_FIXATION,
                    record.getOrder().getOrderRefNo(), record.getOrder().getOrderDate(), null, null, null, null,
                    record.getPromotionalBasicPay(), fixationRemarks);
            employeeServiceBookService.recordEvent(record.getEmployee().getId(), fixation);
        }
    }

    /**
     * unavailedDays = max(0, admissible - availed); creditAllowed = min(unavailedDays,
     * max(0, 300 - currentBalance)) - the 300-day statutory EL ceiling. Only administrative
     * transfers with the benefit flag admissible ever credit (OWN_REQUEST/MUTUAL, or an
     * administrative transfer explicitly marked benefit-inadmissible, never do).
     */
    private void creditUnavailedJoiningTime(EmployeeMovementRecord record) {
        if (record.getTransferNature() != TransferNature.ADMINISTRATIVE
                || !record.isTransferBenefitAdmissible()
                || record.getUnavailedJtDays() <= 0) {
            return;
        }

        LeaveType el = leaveTypeRepository.findByCode(EL_CODE)
                .orElseThrow(() -> new BusinessRuleViolationException("EL leave type is not configured - cannot credit unavailed joining time"));
        int year = record.getJoiningDate().getYear();
        LeaveEntitlementBalance balance = entitlementBalanceRepository
                .findByEmployeeIdAndLeaveTypeIdAndYear(record.getEmployee().getId(), el.getId(), year)
                .orElseGet(() -> new LeaveEntitlementBalance(record.getEmployee(), el, year));

        BigDecimal room = EL_CEILING.subtract(balance.getCurrentBalance()).max(BigDecimal.ZERO);
        BigDecimal creditAllowed = BigDecimal.valueOf(record.getUnavailedJtDays()).min(room);
        if (creditAllowed.signum() <= 0) {
            return;
        }

        balance.setCreditedDays(balance.getCreditedDays().add(creditAllowed));
        balance.setCurrentBalance(balance.getCurrentBalance().add(creditAllowed));
        balance.setAvailableBalance(balance.getAvailableBalance().add(creditAllowed));
        entitlementBalanceRepository.saveAndFlush(balance);

        LeaveLedgerEntry entry = new LeaveLedgerEntry(record.getEmployee(), el, record.getJoiningDate(), creditAllowed,
                "Unavailed Joining Time on transfer as per Order " + record.getOrder().getOrderRefNo(),
                LeaveLedgerSource.TRANSFER_JT_CONVERSION);
        leaveLedgerEntryRepository.saveAndFlush(entry);

        record.setElCreditedDays(creditAllowed.intValue());
        record.setElCredited(true);
        record.setLeaveLedgerTxnId(entry.getId());

        String remarks = "Admissible JT " + record.getAdmissibleJtDays() + " days, availed " + record.getJoiningTimeAvailedDays()
                + " days, " + creditAllowed + " days credited to EL (300-day ceiling applied)";
        ServiceBookEventRequest elCredit = new ServiceBookEventRequest(record.getJoiningDate(), CareerEventType.TRANSFER_BENEFIT_EL_CREDIT,
                record.getOrder().getOrderRefNo(), record.getOrder().getOrderDate(), null, null, null, null, null, remarks);
        employeeServiceBookService.recordEvent(record.getEmployee().getId(), elCredit);
    }

    private ServiceBookEventRequest releaseServiceBookEvent(EmployeeMovementRecord record) {
        String remarks = "Relieved from " + record.getFromOffice().getName() + " on " + record.getReleaseDate()
                + " (" + record.getReleaseSession() + "); Order Ref " + record.getOrder().getOrderRefNo();
        return new ServiceBookEventRequest(record.getReleaseDate(), CareerEventType.TRANSFER_RELEASE,
                record.getReleaseOrderRef(), record.getOrder().getOrderDate(),
                record.getFromDepartment() != null ? record.getFromDepartment().getId() : null,
                record.getFromDesignation().getId(), record.getFromOffice().getId(), null, null, remarks);
    }

    /** Requires the movement to have been released already - releaseOrderRef/Date/Session must be set. */
    public byte[] generateReleasePdf(Long movementId) {
        EmployeeMovementRecord record = findOrThrow(movementId);
        if (record.getReleaseDate() == null) {
            throw new BusinessRuleViolationException("Movement " + movementId + " has not been released yet - no Release Order to generate");
        }
        return releaseOrderPdfGenerator.generate(record);
    }

    /** ESS: the same Release Order PDF, scoped to the caller's own record. */
    public byte[] generateReleasePdfForEmployee(Long movementId, Long callerEmployeeId) {
        EmployeeMovementRecord record = findOrThrow(movementId);
        if (callerEmployeeId != null && !callerEmployeeId.equals(record.getEmployee().getId())) {
            throw new BusinessRuleViolationException("You may only download your own release order");
        }
        return generateReleasePdf(movementId);
    }

    private EmployeeMovementRecord findOrThrow(Long id) {
        return movementRecordRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException("Movement Record", id));
    }
}
