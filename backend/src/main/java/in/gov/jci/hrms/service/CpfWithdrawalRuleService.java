package in.gov.jci.hrms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.jci.hrms.dto.CpfWithdrawalRuleVersionRequest;
import in.gov.jci.hrms.dto.CpfWithdrawalRuleVersionResponse;
import in.gov.jci.hrms.entity.CpfHeadMaster;
import in.gov.jci.hrms.entity.CpfRuleAuditLog;
import in.gov.jci.hrms.entity.CpfRuleCeilingComponent;
import in.gov.jci.hrms.entity.CpfRuleDocument;
import in.gov.jci.hrms.entity.CpfRuleHeadEligibility;
import in.gov.jci.hrms.entity.CpfRuleStatus;
import in.gov.jci.hrms.entity.CpfWithdrawalPurposeMaster;
import in.gov.jci.hrms.entity.CpfWithdrawalRuleDetail;
import in.gov.jci.hrms.entity.CpfWithdrawalRuleVersion;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.repository.CpfHeadMasterRepository;
import in.gov.jci.hrms.repository.CpfRuleAuditLogRepository;
import in.gov.jci.hrms.repository.CpfRuleCeilingComponentRepository;
import in.gov.jci.hrms.repository.CpfRuleDocumentRepository;
import in.gov.jci.hrms.repository.CpfRuleHeadEligibilityRepository;
import in.gov.jci.hrms.repository.CpfWithdrawalPurposeMasterRepository;
import in.gov.jci.hrms.repository.CpfWithdrawalRuleDetailRepository;
import in.gov.jci.hrms.repository.CpfWithdrawalRuleVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The rule-change approval workflow (Parts 2/3 of the spec): DRAFT -&gt; PENDING_VERIFICATION -&gt;
 * PENDING_APPROVAL -&gt; APPROVED (-&gt; SUPERSEDED once a later version of the same purpose is itself
 * approved) or REJECTED - matching {@link CpfRuleStatus}'s own live Postgres enum exactly (already defined
 * on the shared dev database before this class existed - see {@link CpfHeadMaster}'s own javadoc). No
 * DRAFT/PENDING_* version is ever used by {@link CpfWithdrawalRuleEngine} or {@code CpfApplicationService} -
 * only {@link #resolveActiveVersion}'s APPROVED-and-currently-effective result is.
 */
@Service
@Transactional(readOnly = true)
public class CpfWithdrawalRuleService {

    private final CpfWithdrawalRuleVersionRepository versionRepository;
    private final CpfWithdrawalRuleDetailRepository detailRepository;
    private final CpfRuleHeadEligibilityRepository ruleHeadRepository;
    private final CpfRuleCeilingComponentRepository ruleCeilingRepository;
    private final CpfRuleDocumentRepository ruleDocumentRepository;
    private final CpfRuleAuditLogRepository auditLogRepository;
    private final CpfWithdrawalPurposeMasterRepository purposeRepository;
    private final CpfHeadMasterRepository headRepository;
    private final ObjectMapper objectMapper;

    public CpfWithdrawalRuleService(CpfWithdrawalRuleVersionRepository versionRepository, CpfWithdrawalRuleDetailRepository detailRepository,
                                     CpfRuleHeadEligibilityRepository ruleHeadRepository, CpfRuleCeilingComponentRepository ruleCeilingRepository,
                                     CpfRuleDocumentRepository ruleDocumentRepository, CpfRuleAuditLogRepository auditLogRepository,
                                     CpfWithdrawalPurposeMasterRepository purposeRepository, CpfHeadMasterRepository headRepository,
                                     ObjectMapper objectMapper) {
        this.versionRepository = versionRepository;
        this.detailRepository = detailRepository;
        this.ruleHeadRepository = ruleHeadRepository;
        this.ruleCeilingRepository = ruleCeilingRepository;
        this.ruleDocumentRepository = ruleDocumentRepository;
        this.auditLogRepository = auditLogRepository;
        this.purposeRepository = purposeRepository;
        this.headRepository = headRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CpfWithdrawalRuleVersionResponse createDraft(CpfWithdrawalRuleVersionRequest request, String createdBy) {
        CpfWithdrawalPurposeMaster purpose = purposeRepository.findByCode(request.purposeCode())
                .orElseThrow(() -> new BusinessRuleViolationException("Unknown purpose code " + request.purposeCode()));

        CpfWithdrawalRuleVersion version = new CpfWithdrawalRuleVersion(purpose, request.versionTag(), request.effectiveFrom(),
                request.changeReason(), createdBy);
        version = versionRepository.saveAndFlush(version);

        CpfWithdrawalRuleDetail detail = new CpfWithdrawalRuleDetail(version);
        detail.setMinServiceMonths(request.minServiceMonths());
        detail.setIncludePreviousService(request.includePreviousService());
        detail.setAllowBreakInService(request.allowBreakInService());
        detail.setFrequencyScope(request.frequencyScope());
        detail.setMaxOccurrences(request.maxOccurrences());
        detail.setMaxActiveConcurrency(request.maxActiveConcurrency());
        if (request.balanceRetentionPct() != null) {
            detail.setBalanceRetentionPct(request.balanceRetentionPct());
        }
        detail.setRepaymentCreditMethod(request.repaymentCreditMethod());
        detail.setMinTenureMonths(request.minTenureMonths());
        detail.setMaxTenureMonths(request.maxTenureMonths());
        detail.setDefaultTenureMonths(request.defaultTenureMonths());
        detail.setInterestRateAnnual(request.interestRateAnnual());
        detail.setInterestMethod(request.interestMethod());
        detail.setAllowPrepayment(request.allowPrepayment());
        detail.setAllowConversion(request.allowConversion());
        if (request.payrollCapType() != null) {
            detail.setPayrollCapType(request.payrollCapType());
        }
        detail.setTaxRuleReference(request.taxRuleReference());
        detail.setTaxServiceThresholdMonths(request.taxServiceThresholdMonths());
        if (request.workflowDefinitionCode() != null) {
            detail.setWorkflowDefinitionCode(request.workflowDefinitionCode());
        }
        detail = detailRepository.saveAndFlush(detail);

        if (request.heads() != null) {
            for (var headConfig : request.heads()) {
                CpfHeadMaster head = headRepository.findByCode(headConfig.headCode())
                        .orElseThrow(() -> new BusinessRuleViolationException("Unknown CPF head code " + headConfig.headCode()));
                ruleHeadRepository.save(new CpfRuleHeadEligibility(detail, head, headConfig.eligible(), headConfig.debitPriority(), headConfig.recreditPriority()));
            }
        }
        if (request.ceilings() != null) {
            for (var ceilingConfig : request.ceilings()) {
                ruleCeilingRepository.save(new CpfRuleCeilingComponent(detail, ceilingConfig.componentName(), ceilingConfig.sourceMetric(),
                        ceilingConfig.operator(), ceilingConfig.factorValue(), ceilingConfig.displayOrder()));
            }
        }
        if (request.documents() != null) {
            for (var documentConfig : request.documents()) {
                CpfRuleDocument document = new CpfRuleDocument(detail, documentConfig.documentName(), documentConfig.mandatory());
                if (documentConfig.allowedMimeTypes() != null) {
                    document.setAllowedMimeTypes(documentConfig.allowedMimeTypes());
                }
                if (documentConfig.maxSizeKb() != null) {
                    document.setMaxSizeKb(documentConfig.maxSizeKb());
                }
                ruleDocumentRepository.save(document);
            }
        }

        recordAudit(version, "CREATE", null, createdBy, "Draft created: " + request.changeReason());
        return CpfWithdrawalRuleVersionResponse.from(version, detail);
    }

    @Transactional
    public CpfWithdrawalRuleVersionResponse submitForVerification(UUID versionId, String submittedBy) {
        CpfWithdrawalRuleVersion version = findOrThrow(versionId);
        requireStatus(version, CpfRuleStatus.DRAFT, "submitted for verification");
        version.setStatus(CpfRuleStatus.PENDING_VERIFICATION);
        recordAudit(version, "SUBMIT_FOR_VERIFICATION", CpfRuleStatus.DRAFT.name(), submittedBy, "Submitted for Trust Secretariat verification");
        return CpfWithdrawalRuleVersionResponse.from(version, requireDetail(version));
    }

    @Transactional
    public CpfWithdrawalRuleVersionResponse verify(UUID versionId, String verifiedBy) {
        CpfWithdrawalRuleVersion version = findOrThrow(versionId);
        requireStatus(version, CpfRuleStatus.PENDING_VERIFICATION, "verified");
        version.setStatus(CpfRuleStatus.PENDING_APPROVAL);
        version.setVerifiedBy(verifiedBy);
        recordAudit(version, "VERIFY", CpfRuleStatus.PENDING_VERIFICATION.name(), verifiedBy, "Verified by Trust Secretariat");
        return CpfWithdrawalRuleVersionResponse.from(version, requireDetail(version));
    }

    /**
     * Approves a PENDING_APPROVAL version and, in the same transaction, closes out whatever version was
     * previously active for the same purpose (effectiveTo = this version's effectiveFrom minus one day) -
     * Part 2 of the spec's own worked example (a FY2027 rule taking over from an FY2026 one without ever
     * touching the FY2026 row an already-processed application still points to).
     */
    @Transactional
    public CpfWithdrawalRuleVersionResponse approve(UUID versionId, String approvedBy, String approvalReference, String reason) {
        CpfWithdrawalRuleVersion version = findOrThrow(versionId);
        requireStatus(version, CpfRuleStatus.PENDING_APPROVAL, "approved");

        resolveActiveVersion(version.getPurpose().getCode(), version.getEffectiveFrom()).ifPresent(current -> {
            if (!current.getId().equals(version.getId())) {
                current.setEffectiveTo(version.getEffectiveFrom().minusDays(1));
                current.setStatus(CpfRuleStatus.SUPERSEDED);
                recordAudit(current, "SUPERSEDE", CpfRuleStatus.APPROVED.name(), approvedBy,
                        "Superseded by version " + version.getVersionTag());
            }
        });

        version.setStatus(CpfRuleStatus.APPROVED);
        version.setApprovedBy(approvedBy);
        version.setApprovedAt(Instant.now());
        version.setApprovalReference(approvalReference);
        recordAudit(version, "APPROVE", CpfRuleStatus.PENDING_APPROVAL.name(), approvedBy, reason);
        return CpfWithdrawalRuleVersionResponse.from(version, requireDetail(version));
    }

    @Transactional
    public CpfWithdrawalRuleVersionResponse reject(UUID versionId, String rejectedBy, String reason) {
        CpfWithdrawalRuleVersion version = findOrThrow(versionId);
        if (version.getStatus() != CpfRuleStatus.PENDING_VERIFICATION && version.getStatus() != CpfRuleStatus.PENDING_APPROVAL) {
            throw new BusinessRuleViolationException("Version " + versionId + " is " + version.getStatus() + " - only a PENDING_VERIFICATION/PENDING_APPROVAL version can be rejected.");
        }
        String previousStatus = version.getStatus().name();
        version.setStatus(CpfRuleStatus.REJECTED);
        recordAudit(version, "REJECT", previousStatus, rejectedBy, reason);
        return CpfWithdrawalRuleVersionResponse.from(version, requireDetail(version));
    }

    public CpfWithdrawalRuleVersionResponse getVersion(UUID versionId) {
        CpfWithdrawalRuleVersion version = findOrThrow(versionId);
        return CpfWithdrawalRuleVersionResponse.from(version, requireDetail(version));
    }

    public List<CpfWithdrawalRuleVersionResponse> listVersions(String purposeCode) {
        return versionRepository.findByPurpose_CodeOrderByEffectiveFromDesc(purposeCode).stream()
                .map(v -> CpfWithdrawalRuleVersionResponse.from(v, requireDetail(v))).toList();
    }

    public List<CpfWithdrawalRuleVersionResponse> listAll() {
        return versionRepository.findAllByOrderByPurpose_CodeAscEffectiveFromDesc().stream()
                .map(v -> CpfWithdrawalRuleVersionResponse.from(v, requireDetail(v))).toList();
    }

    public List<CpfWithdrawalRuleVersionResponse> listPendingApproval() {
        return versionRepository.findByStatusOrderByPurpose_CodeAsc(CpfRuleStatus.PENDING_APPROVAL).stream()
                .map(v -> CpfWithdrawalRuleVersionResponse.from(v, requireDetail(v))).toList();
    }

    /** The most recent APPROVED version whose effective window covers asOf - CpfApplicationService's replacement for a hardcoded ceiling. Package-visible entity return since only same-package services call this. */
    Optional<CpfWithdrawalRuleVersion> resolveActiveVersion(String purposeCode, LocalDate asOf) {
        return versionRepository.findByPurpose_CodeAndStatusAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(purposeCode, CpfRuleStatus.APPROVED, asOf)
                .stream()
                .filter(v -> v.getEffectiveTo() == null || !v.getEffectiveTo().isBefore(asOf))
                .findFirst();
    }

    CpfWithdrawalRuleDetail requireDetail(CpfWithdrawalRuleVersion version) {
        return detailRepository.findByVersion_Id(version.getId())
                .orElseThrow(() -> new BusinessRuleViolationException("Rule version " + version.getId() + " has no detail row - data integrity issue"));
    }

    Optional<CpfWithdrawalRuleVersion> findEntity(UUID versionId) {
        return versionRepository.findById(versionId);
    }

    private void requireStatus(CpfWithdrawalRuleVersion version, CpfRuleStatus required, String action) {
        if (version.getStatus() != required) {
            throw new BusinessRuleViolationException("Version " + version.getId() + " is " + version.getStatus() + " - only a " + required + " version can be " + action + ".");
        }
    }

    private CpfWithdrawalRuleVersion findOrThrow(UUID versionId) {
        return versionRepository.findById(versionId).orElseThrow(() -> new BusinessRuleViolationException("Withdrawal rule version " + versionId + " not found"));
    }

    private void recordAudit(CpfWithdrawalRuleVersion version, String action, String previousStatus, String performedBy, String reason) {
        Map<String, Object> newState = new LinkedHashMap<>();
        newState.put("status", version.getStatus());
        newState.put("versionTag", version.getVersionTag());
        newState.put("effectiveFrom", version.getEffectiveFrom());
        newState.put("effectiveTo", version.getEffectiveTo());
        Map<String, Object> previousState = previousStatus == null ? null : Map.of("status", previousStatus);
        auditLogRepository.save(new CpfRuleAuditLog(version, action, toJson(previousState), toJson(newState), performedBy,
                reason != null ? reason : ""));
    }

    private String toJson(Map<String, Object> data) {
        if (data == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            return null;
        }
    }
}
