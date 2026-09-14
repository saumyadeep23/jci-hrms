package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.JciEccsFinancialPositionResponse;
import in.gov.jci.hrms.dto.JciEccsLoanResponse;
import in.gov.jci.hrms.dto.JciEccsMemberResponse;
import in.gov.jci.hrms.dto.JciEccsMemberStatusChangeRequest;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.JciEccsLoanProductCode;
import in.gov.jci.hrms.entity.JciEccsLoanStatus;
import in.gov.jci.hrms.entity.JciEccsMember;
import in.gov.jci.hrms.entity.JciEccsMembershipStatus;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.JciEccsLoanRepository;
import in.gov.jci.hrms.repository.JciEccsMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class JciEccsMemberService {

    private final JciEccsMemberRepository memberRepository;
    private final JciEccsLoanRepository loanRepository;
    private final EmployeeRepository employeeRepository;
    private final JciEccsLifecycleEventService lifecycleEventService;

    public JciEccsMemberService(JciEccsMemberRepository memberRepository, JciEccsLoanRepository loanRepository,
                                 EmployeeRepository employeeRepository, JciEccsLifecycleEventService lifecycleEventService) {
        this.memberRepository = memberRepository;
        this.loanRepository = loanRepository;
        this.employeeRepository = employeeRepository;
        this.lifecycleEventService = lifecycleEventService;
    }

    /** Batch-fetches every referenced Employee (EmployeeRepository.findByIdIn, RO/DPC eager) rather than
     * one lookup per member, so the directory listing stays a single extra query regardless of size. */
    public List<JciEccsMemberResponse> listMembers() {
        List<JciEccsMember> members = memberRepository.findAll();
        Map<Long, Employee> employeesById = employeeRepository
                .findByIdIn(members.stream().map(JciEccsMember::getEmployeeId).distinct().toList())
                .stream().collect(Collectors.toMap(Employee::getId, Function.identity()));
        return members.stream().map(m -> JciEccsMemberResponse.from(m, employeesById.get(m.getEmployeeId()))).toList();
    }

    public JciEccsFinancialPositionResponse financialPosition(Long employeeId) {
        JciEccsMember member = memberRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new BusinessRuleViolationException("No JCIECCS membership found for employee id " + employeeId));
        Employee employee = employeeRepository.findById(employeeId).orElse(null);

        JciEccsLoanResponse activeTermLoan = loanRepository
                .findFirstByMember_IdAndLoanProduct_ProductCodeAndStatusOrderByCreatedAtDesc(member.getId(), JciEccsLoanProductCode.TERM,
                        JciEccsLoanStatus.ACTIVE)
                .map(JciEccsLoanResponse::from).orElse(null);
        JciEccsLoanResponse activeEmergencyLoan = loanRepository
                .findFirstByMember_IdAndLoanProduct_ProductCodeAndStatusOrderByCreatedAtDesc(member.getId(), JciEccsLoanProductCode.EMERGENCY,
                        JciEccsLoanStatus.ACTIVE)
                .map(JciEccsLoanResponse::from).orElse(null);

        return new JciEccsFinancialPositionResponse(JciEccsMemberResponse.from(member, employee), activeTermLoan, activeEmergencyLoan);
    }

    /**
     * Changes a member's status (ACTIVE/SUSPENDED/CLOSED - CLOSED covers resignation/cessation/
     * superannuation collectively, there is no separate DB value for each). Remarks are required for a
     * transition into SUSPENDED or CLOSED (Bye-laws 15/16); effectiveDate is recorded on effective_to
     * for a non-ACTIVE status (cleared back to null on reactivation) and updated_at is refreshed
     * automatically (@UpdateTimestamp). No separate "lock future snapshots" step is needed: JciEccsCollectionSnapshotService.generateSnapshot()
     * already only iterates ACTIVE members (findByMembershipStatus(ACTIVE)) when building a NEW
     * snapshot, so a member moved off ACTIVE here is automatically excluded from every snapshot
     * generated from this point on - already-locked past snapshots are immutable by design and are
     * never retroactively edited.
     */
    @Transactional
    public JciEccsMemberResponse changeStatus(Long memberId, JciEccsMemberStatusChangeRequest request, Long performedByEmployeeId) {
        JciEccsMember member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MasterDataNotFoundException("JCIECCS Member", memberId));

        boolean requiresRemarks = request.status() == JciEccsMembershipStatus.SUSPENDED || request.status() == JciEccsMembershipStatus.CLOSED;
        if (requiresRemarks && (request.remarks() == null || request.remarks().isBlank())) {
            throw new BusinessRuleViolationException(
                    "Remarks are required when suspending or closing a membership (Bye-laws 15/16)");
        }

        JciEccsMembershipStatus oldStatus = member.getMembershipStatus();
        member.setMembershipStatus(request.status());
        member.setEffectiveTo(request.status() == JciEccsMembershipStatus.ACTIVE ? null : request.effectiveDate());
        memberRepository.save(member);

        Employee employee = employeeRepository.findById(member.getEmployeeId()).orElse(null);
        JciEccsMemberResponse response = JciEccsMemberResponse.from(member, employee);

        lifecycleEventService.record("JCIECCS_MEMBER", member.getId(), "MEMBERSHIP_STATUS_CHANGED", oldStatus, request.status(),
                member.getMembershipCode(), performedByEmployeeId, request.remarks());

        return response;
    }
}
