package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.JciEccsMigrationStatusResponse;
import in.gov.jci.hrms.dto.JciEccsStagingMemberRequest;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.JciEccsMember;
import in.gov.jci.hrms.entity.JciEccsStagingMember;
import in.gov.jci.hrms.entity.StagingRowStatus;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.JciEccsMemberRepository;
import in.gov.jci.hrms.repository.JciEccsStagingMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Historical Migration Service (spec section 4.6): migrates co-operative membership codes + audited FY
 * 2023-24 opening balances from a staging table into real {@link JciEccsMember} rows, mirroring
 * LegacyMigrationService's own stage -&gt; validateAndPromote -&gt; status workflow. Only ever selects
 * PENDING rows, so re-running {@link #validateAndPromote()} against an already-processed batch is safe
 * (already-PROMOTED/REJECTED rows are simply never re-selected).
 */
@Service
@Transactional(readOnly = true)
public class JciEccsHistoricalMigrationService {

    private final JciEccsStagingMemberRepository stagingRepository;
    private final JciEccsMemberRepository memberRepository;
    private final EmployeeRepository employeeRepository;

    public JciEccsHistoricalMigrationService(JciEccsStagingMemberRepository stagingRepository, JciEccsMemberRepository memberRepository,
                                              EmployeeRepository employeeRepository) {
        this.stagingRepository = stagingRepository;
        this.memberRepository = memberRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional
    public void stage(List<JciEccsStagingMemberRequest> requests) {
        for (JciEccsStagingMemberRequest r : requests) {
            stagingRepository.save(new JciEccsStagingMember(r.employeeCode(), r.membershipCode(), r.membershipDate(),
                    nullToZero(r.shareBalance()), nullToZero(r.fundBalance()), nullToZero(r.securityBalance()), nullToZero(r.thriftMonthlyAmount())));
        }
    }

    @Transactional
    public void validateAndPromote() {
        for (JciEccsStagingMember row : stagingRepository.findByStatus(StagingRowStatus.PENDING)) {
            Optional<Employee> employee = employeeRepository.findByEmployeeCode(row.getEmployeeCode());
            if (employee.isEmpty()) {
                reject(row, "No employee found with code " + row.getEmployeeCode());
                continue;
            }
            if (memberRepository.findByEmployeeId(employee.get().getId()).isPresent()) {
                reject(row, "Employee " + row.getEmployeeCode() + " already has a JCIECCS membership");
                continue;
            }
            if (memberRepository.findByMembershipCode(row.getMembershipCode()).isPresent()) {
                reject(row, "Membership code " + row.getMembershipCode() + " is already in use");
                continue;
            }

            JciEccsMember member = new JciEccsMember(employee.get().getId(), row.getMembershipCode(), row.getMembershipDate(),
                    row.getMembershipDate());
            member.setShareBalance(row.getShareBalance());
            member.setFundBalance(row.getFundBalance());
            member.setSecurityBalance(row.getSecurityBalance());
            member.setThriftMonthlyAmount(row.getThriftMonthlyAmount());
            memberRepository.save(member);

            row.setStatus(StagingRowStatus.PROMOTED);
            stagingRepository.save(row);
        }
    }

    public JciEccsMigrationStatusResponse getStatus() {
        long pending = stagingRepository.countByStatus(StagingRowStatus.PENDING);
        long promoted = stagingRepository.countByStatus(StagingRowStatus.PROMOTED);
        long rejected = stagingRepository.countByStatus(StagingRowStatus.REJECTED);
        List<JciEccsMigrationStatusResponse.RejectedRow> rejectedRows = stagingRepository.findByStatus(StagingRowStatus.REJECTED).stream()
                .map(r -> new JciEccsMigrationStatusResponse.RejectedRow(r.getId(), r.getEmployeeCode(), r.getMembershipCode(), r.getRejectionReason()))
                .toList();
        return new JciEccsMigrationStatusResponse(pending, promoted, rejected, rejectedRows);
    }

    private void reject(JciEccsStagingMember row, String reason) {
        row.setStatus(StagingRowStatus.REJECTED);
        row.setRejectionReason(reason);
        stagingRepository.save(row);
    }

    private static java.math.BigDecimal nullToZero(java.math.BigDecimal value) {
        return value == null ? java.math.BigDecimal.ZERO : value;
    }
}
