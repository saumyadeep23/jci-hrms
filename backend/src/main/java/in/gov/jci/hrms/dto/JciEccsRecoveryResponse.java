package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.JciEccsRecovery;
import in.gov.jci.hrms.entity.JciEccsRecoveryAllocation;
import in.gov.jci.hrms.entity.JciEccsRecoveryComponent;
import in.gov.jci.hrms.entity.JciEccsRecoverySource;
import in.gov.jci.hrms.entity.JciEccsRecoveryStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** The priority-cascade audit trail (spec section 66): one recovery event with its component-level
 * allocation breakdown, in the exact sequence the allocation cascade applied it. */
public record JciEccsRecoveryResponse(
        Long id,
        JciEccsRecoverySource source,
        JciEccsRecoveryStatus status,
        String membershipCode,
        Long employeeId,
        Long loanId,
        String loanIssueId,
        BigDecimal grossAmount,
        Instant createdAt,
        Instant postedAt,
        Long reversalOfRecoveryId,
        List<AllocationLine> allocations
) {
    public record AllocationLine(int sequence, JciEccsRecoveryComponent component, BigDecimal expectedAmount, BigDecimal allocatedAmount,
                                  Long loanId, Long loanScheduleId) {
        public static AllocationLine from(JciEccsRecoveryAllocation a) {
            return new AllocationLine(a.getAllocationSequence(), a.getComponent(), a.getExpectedAmount(), a.getAllocatedAmount(),
                    a.getLoan() != null ? a.getLoan().getId() : null, a.getLoanSchedule() != null ? a.getLoanSchedule().getId() : null);
        }
    }

    public static JciEccsRecoveryResponse from(JciEccsRecovery r, List<JciEccsRecoveryAllocation> allocations) {
        return new JciEccsRecoveryResponse(r.getId(), r.getSource(), r.getStatus(), r.getMember().getMembershipCode(), r.getMember().getEmployeeId(),
                r.getLoan() != null ? r.getLoan().getId() : null, r.getLoan() != null ? r.getLoan().getLoanIssueId() : null,
                r.getGrossAmount(), r.getCreatedAt(), r.getPostedAt(), r.getReversalOfRecovery() != null ? r.getReversalOfRecovery().getId() : null,
                allocations.stream().map(AllocationLine::from).toList());
    }
}
