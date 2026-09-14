package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.CpfDisputeAdminResponse;
import in.gov.jci.hrms.dto.CpfDisputeCreateRequest;
import in.gov.jci.hrms.dto.CpfDisputeHistoryEntryResponse;
import in.gov.jci.hrms.dto.CpfDisputeResponse;
import in.gov.jci.hrms.dto.CpfDisputeSummaryResponse;
import in.gov.jci.hrms.entity.CpfDisputeCategory;
import in.gov.jci.hrms.entity.CpfDisputeStatus;
import in.gov.jci.hrms.entity.CpfTransactionDispute;
import in.gov.jci.hrms.entity.CpfTrustMemberLedgerEntry;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.ConcurrencyConflictException;
import in.gov.jci.hrms.exception.DuplicateDisputeException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.AuditLogRepository;
import in.gov.jci.hrms.repository.CpfTransactionDisputeRepository;
import in.gov.jci.hrms.repository.CpfTrustMemberLedgerEntryRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Year;
import java.util.List;

/**
 * The CPF Transaction Dispute workflow (Parts 7-13/19/29-30 of the Passbook V2 module spec) - an external
 * process that only ever REFERENCES an immutable {@link CpfTrustMemberLedgerEntry} by id; no method here
 * ever calls a setter on that entity or on any of its running-balance/contribution fields (the
 * non-negotiable accounting-safety rule the whole module is built around).
 *
 * <h2>State machine (Part 30)</h2>
 * <pre>
 *   OPEN -> UNDER_REVIEW -> RESOLVED
 *   OPEN -> UNDER_REVIEW -> REJECTED
 *   OPEN -> UNDER_REVIEW -> CLARIFICATION_REQUIRED -> UNDER_REVIEW -> (RESOLVED|REJECTED)
 *   OPEN -> WITHDRAWN
 * </pre>
 * Every transition method below hard-checks the dispute's CURRENT status before mutating it - there is no
 * generic "setStatus" path exposed to a controller.
 *
 * <h2>Audit (Part 19)</h2>
 * {@link CpfTransactionDispute} implements the existing generic {@code Auditable} interface (the same
 * mechanism {@code CpfAnnualInterestRun}/{@code CpfWithdrawalRule} already use elsewhere in this codebase) -
 * every create/status-change is captured automatically with a before/after snapshot, actor, and timestamp,
 * so this service never needs its own bespoke audit-event list.
 */
@Service
@Transactional(readOnly = true)
public class CpfTransactionDisputeService {

    private static final List<CpfDisputeStatus> INACTIVE_STATUSES =
            List.of(CpfDisputeStatus.RESOLVED, CpfDisputeStatus.REJECTED, CpfDisputeStatus.WITHDRAWN);

    private final CpfTransactionDisputeRepository disputeRepository;
    private final CpfTrustMemberLedgerEntryRepository ledgerRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditLogRepository auditLogRepository;

    public CpfTransactionDisputeService(CpfTransactionDisputeRepository disputeRepository, CpfTrustMemberLedgerEntryRepository ledgerRepository,
                                         EmployeeRepository employeeRepository, AuditLogRepository auditLogRepository) {
        this.disputeRepository = disputeRepository;
        this.ledgerRepository = ledgerRepository;
        this.employeeRepository = employeeRepository;
        this.auditLogRepository = auditLogRepository;
    }

    // ---------------------------------------------------------------------------------------------- Employee self-service

    /**
     * Validates (Part 9): the transaction exists, belongs to this employee (IDOR guard - Part 21, mirrors
     * EssPayrollService.getDetail()'s own established "belongs to this employee" check), no active
     * duplicate exists for the same (transaction, category) pair (Part 10 - the same rule V83's own partial
     * unique index enforces at the database level, checked here first so a violation surfaces as a clean
     * 409 rather than a raw constraint-violation 500), then creates the dispute. Atomic (Part 28): a single
     * @Transactional method, so a failure after the duplicate check (e.g. the attachment reference is
     * malformed) never leaves a half-created row - the audit CREATE event fires only once this method's
     * transaction actually commits the insert, via the same Auditable mechanism as every other entity in
     * this codebase.
     */
    @Transactional
    public CpfDisputeResponse raiseDispute(Long employeeId, CpfDisputeCreateRequest request) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new BusinessRuleViolationException("Employee " + employeeId + " not found"));
        CpfTrustMemberLedgerEntry transaction = ledgerRepository.findById(request.cpfLedgerTransactionId())
                .orElseThrow(() -> new MasterDataNotFoundException("CPF Transaction", request.cpfLedgerTransactionId()));
        if (!transaction.getEmployee().getId().equals(employeeId)) {
            throw new BusinessRuleViolationException("CPF transaction " + request.cpfLedgerTransactionId() + " does not belong to this employee");
        }
        if (disputeRepository.existsByCpfLedgerTransaction_IdAndDisputeCategoryAndStatusNotIn(
                transaction.getId(), request.disputeCategory(), INACTIVE_STATUSES)) {
            throw new DuplicateDisputeException("An active dispute already exists for this transaction under category "
                    + request.disputeCategory() + " - withdraw or resolve it before raising another for the same issue.");
        }

        CpfTransactionDispute dispute = new CpfTransactionDispute(generateDisputeNumber(), employee, transaction,
                request.disputeCategory(), request.employeeRemarks(), employee);
        dispute.setAttachmentS3Key(request.attachmentS3Key());
        dispute.setAttachmentOriginalFilename(request.attachmentOriginalFilename());
        return CpfDisputeResponse.from(disputeRepository.save(dispute));
    }

    public List<CpfDisputeResponse> myDisputes(Long employeeId) {
        return disputeRepository.findByEmployee_IdOrderByRaisedAtDesc(employeeId).stream().map(CpfDisputeResponse::from).toList();
    }

    public CpfDisputeResponse getMyDispute(Long employeeId, Long disputeId) {
        return CpfDisputeResponse.from(ownedByOrThrow(disputeId, employeeId));
    }

    /** OPEN -> WITHDRAWN only (Part 11: "Employee may withdraw only when allowed by business rules"). */
    @Transactional
    public CpfDisputeResponse withdraw(Long employeeId, Long disputeId) {
        CpfTransactionDispute dispute = ownedByOrThrow(disputeId, employeeId);
        requireStatus(dispute, CpfDisputeStatus.OPEN, "withdrawn");
        dispute.setStatus(CpfDisputeStatus.WITHDRAWN);
        dispute.setWithdrawnAt(Instant.now());
        return CpfDisputeResponse.from(dispute);
    }

    /** CLARIFICATION_REQUIRED -> UNDER_REVIEW, recording the employee's reply (Part 11). */
    @Transactional
    public CpfDisputeResponse respondToClarification(Long employeeId, Long disputeId, String response) {
        CpfTransactionDispute dispute = ownedByOrThrow(disputeId, employeeId);
        requireStatus(dispute, CpfDisputeStatus.CLARIFICATION_REQUIRED, "responded to");
        dispute.setEmployeeResponse(response);
        dispute.setStatus(CpfDisputeStatus.UNDER_REVIEW);
        return CpfDisputeResponse.from(dispute);
    }

    // ---------------------------------------------------------------------------------------------- CPF/HR reviewer & admin

    public Page<CpfDisputeSummaryResponse> search(String disputeNumber, Long employeeId, CpfDisputeCategory category,
                                                   CpfDisputeStatus status, Pageable pageable) {
        return disputeRepository.search(disputeNumber, employeeId, category, status, pageable).map(CpfDisputeSummaryResponse::from);
    }

    public CpfDisputeAdminResponse getForAdmin(Long disputeId) {
        return CpfDisputeAdminResponse.from(findOrThrow(disputeId));
    }

    public java.util.Map<CpfDisputeStatus, Long> dashboardCounts() {
        java.util.Map<CpfDisputeStatus, Long> counts = new java.util.EnumMap<>(CpfDisputeStatus.class);
        for (CpfDisputeStatus status : CpfDisputeStatus.values()) {
            counts.put(status, disputeRepository.countByStatus(status));
        }
        return counts;
    }

    public List<CpfDisputeHistoryEntryResponse> history(Long disputeId) {
        findOrThrow(disputeId); // 404 if the dispute itself doesn't exist
        return auditLogRepository.findByEntityNameAndEntityIdOrderByCreatedAtAsc("CpfTransactionDispute", disputeId).stream()
                .map(CpfDisputeHistoryEntryResponse::from).toList();
    }

    @Transactional
    public CpfDisputeAdminResponse assign(Long disputeId, Long assigneeEmployeeId, Long expectedVersion, Long actorId) {
        CpfTransactionDispute dispute = findOrThrowWithVersionCheck(disputeId, expectedVersion);
        Employee assignee = employeeRepository.findById(assigneeEmployeeId)
                .orElseThrow(() -> new BusinessRuleViolationException("Employee " + assigneeEmployeeId + " not found"));
        dispute.setAssignedTo(assignee);
        dispute.setAssignedAt(Instant.now());
        return CpfDisputeAdminResponse.from(dispute);
    }

    /** OPEN -> UNDER_REVIEW. */
    @Transactional
    public CpfDisputeAdminResponse startReview(Long disputeId, Long expectedVersion, Long actorId) {
        CpfTransactionDispute dispute = findOrThrowWithVersionCheck(disputeId, expectedVersion);
        requireStatus(dispute, CpfDisputeStatus.OPEN, "moved to review");
        dispute.setStatus(CpfDisputeStatus.UNDER_REVIEW);
        return CpfDisputeAdminResponse.from(dispute);
    }

    /** UNDER_REVIEW -> CLARIFICATION_REQUIRED. */
    @Transactional
    public CpfDisputeAdminResponse requestClarification(Long disputeId, String message, Long expectedVersion, Long actorId) {
        CpfTransactionDispute dispute = findOrThrowWithVersionCheck(disputeId, expectedVersion);
        requireStatus(dispute, CpfDisputeStatus.UNDER_REVIEW, "sent for clarification");
        dispute.setClarificationRequest(message);
        dispute.setStatus(CpfDisputeStatus.CLARIFICATION_REQUIRED);
        return CpfDisputeAdminResponse.from(dispute);
    }

    /**
     * UNDER_REVIEW -> RESOLVED (Part 13: this NEVER touches the underlying CPF ledger - resolving a
     * dispute records a decision/remark only; an actual accounting correction, if warranted, must go
     * through the existing authorized CPF correction/reversal mechanism separately, not through this
     * method). Part 29 optimistic-locking: expectedVersion must match the dispute's current version or
     * this throws ConcurrencyConflictException before any field is touched.
     */
    @Transactional
    public CpfDisputeAdminResponse resolve(Long disputeId, String remarks, Long expectedVersion, Long actorId) {
        CpfTransactionDispute dispute = findOrThrowWithVersionCheck(disputeId, expectedVersion);
        requireStatus(dispute, CpfDisputeStatus.UNDER_REVIEW, "resolved");
        dispute.setResolutionRemarks(remarks);
        dispute.setStatus(CpfDisputeStatus.RESOLVED);
        dispute.setResolvedAt(Instant.now());
        resolveActingEmployee(actorId).ifPresent(dispute::setResolvedBy);
        return CpfDisputeAdminResponse.from(dispute);
    }

    /** UNDER_REVIEW -> REJECTED - same ledger-immutability and optimistic-locking guarantees as resolve(). */
    @Transactional
    public CpfDisputeAdminResponse reject(Long disputeId, String remarks, Long expectedVersion, Long actorId) {
        CpfTransactionDispute dispute = findOrThrowWithVersionCheck(disputeId, expectedVersion);
        requireStatus(dispute, CpfDisputeStatus.UNDER_REVIEW, "rejected");
        dispute.setResolutionRemarks(remarks);
        dispute.setStatus(CpfDisputeStatus.REJECTED);
        dispute.setRejectedAt(Instant.now());
        resolveActingEmployee(actorId).ifPresent(dispute::setRejectedBy);
        return CpfDisputeAdminResponse.from(dispute);
    }

    // ---------------------------------------------------------------------------------------------- Internals

    private CpfTransactionDispute ownedByOrThrow(Long disputeId, Long employeeId) {
        return disputeRepository.findByIdAndEmployee_Id(disputeId, employeeId)
                .orElseThrow(() -> new MasterDataNotFoundException("CPF Dispute", disputeId));
    }

    private CpfTransactionDispute findOrThrow(Long disputeId) {
        return disputeRepository.findById(disputeId).orElseThrow(() -> new MasterDataNotFoundException("CPF Dispute", disputeId));
    }

    /** Part 29: an explicit compare-and-check before any mutation, since each REST call is its own fresh transaction/persistence-context - Hibernate's own @Version dirty-check alone would not catch a cross-request race the way this explicit check does. */
    private CpfTransactionDispute findOrThrowWithVersionCheck(Long disputeId, Long expectedVersion) {
        CpfTransactionDispute dispute = findOrThrow(disputeId);
        if (expectedVersion != null && !expectedVersion.equals(dispute.getVersion())) {
            throw new ConcurrencyConflictException("This dispute has been updated by another user. Please refresh.");
        }
        return dispute;
    }

    private void requireStatus(CpfTransactionDispute dispute, CpfDisputeStatus required, String action) {
        if (dispute.getStatus() != required) {
            throw new BusinessRuleViolationException(
                    "Dispute " + dispute.getDisputeNumber() + " is " + dispute.getStatus() + " - only a " + required + " dispute can be " + action + ".");
        }
    }

    private java.util.Optional<Employee> resolveActingEmployee(Long employeeId) {
        return employeeId == null ? java.util.Optional.empty() : employeeRepository.findById(employeeId);
    }

    /** "CPF-DSP-{calendar year}-{seq, 6 digits}" per the module spec's own example format - same count-based-prefix generator pattern as CpfLoanApplicationService.generateLoanApplicationNo(). */
    private String generateDisputeNumber() {
        String year = String.valueOf(Year.now().getValue());
        String prefix = "CPF-DSP-" + year + "-";
        long seq = disputeRepository.countByDisputeNumberStartingWith(prefix) + 1;
        return prefix + String.format("%06d", seq);
    }
}
