package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.CpfHeadMasterResponse;
import in.gov.jci.hrms.dto.CpfWithdrawalPurposeResponse;
import in.gov.jci.hrms.dto.CpfWithdrawalTypeResponse;
import in.gov.jci.hrms.repository.CpfHeadMasterRepository;
import in.gov.jci.hrms.repository.CpfWithdrawalPurposeMasterRepository;
import in.gov.jci.hrms.repository.CpfWithdrawalTypeMasterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read access to the Withdrawal Type/Purpose/Head masters (Parts 5-7) - already comprehensively seeded live
 * (see {@link in.gov.jci.hrms.entity.CpfHeadMaster}'s own javadoc: 3 heads, 3 types, 8 purposes). Create/
 * deactivate endpoints for these simple lookup lists are a smaller, lower-priority extension left for a
 * follow-up - the heavier, spec-emphasized DRAFT/PENDING_APPROVAL/APPROVED workflow in
 * {@link CpfWithdrawalRuleService} governs the actual eligibility/ceiling/frequency BUSINESS RULES, which
 * is where this delivery's effort went.
 */
@Service
@Transactional(readOnly = true)
public class CpfWithdrawalMasterService {

    private final CpfWithdrawalTypeMasterRepository typeRepository;
    private final CpfWithdrawalPurposeMasterRepository purposeRepository;
    private final CpfHeadMasterRepository headRepository;

    public CpfWithdrawalMasterService(CpfWithdrawalTypeMasterRepository typeRepository, CpfWithdrawalPurposeMasterRepository purposeRepository,
                                       CpfHeadMasterRepository headRepository) {
        this.typeRepository = typeRepository;
        this.purposeRepository = purposeRepository;
        this.headRepository = headRepository;
    }

    public List<CpfWithdrawalTypeResponse> listTypes() {
        return typeRepository.findAllByOrderByDisplayOrderAsc().stream().map(CpfWithdrawalTypeResponse::from).toList();
    }

    public List<CpfWithdrawalPurposeResponse> listPurposes() {
        return purposeRepository.findAllByOrderByCodeAsc().stream().map(CpfWithdrawalPurposeResponse::from).toList();
    }

    public List<CpfHeadMasterResponse> listHeads() {
        return headRepository.findAllByOrderByCodeAsc().stream().map(CpfHeadMasterResponse::from).toList();
    }
}
