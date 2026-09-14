package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.JciEccsRecoveryComponent;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static in.gov.jci.hrms.entity.JciEccsRecoveryComponent.EMERGENCY_INTEREST;
import static in.gov.jci.hrms.entity.JciEccsRecoveryComponent.EMERGENCY_PRINCIPAL;
import static in.gov.jci.hrms.entity.JciEccsRecoveryComponent.TERM_INTEREST;
import static in.gov.jci.hrms.entity.JciEccsRecoveryComponent.TERM_PRINCIPAL;
import static in.gov.jci.hrms.entity.JciEccsRecoveryComponent.THRIFT;

/**
 * JCIECCS Lifecycle Engine Phase 1 - pure allocation calculator, extracted (not reinvented) from
 * {@code JciEccsDebitConfirmationService}'s original {@code allocate()} cascade: an actually-received
 * amount is distributed across components in the module's own fixed priority order - Thrift, Term
 * Interest, Emergency Interest, Term Principal, Emergency Principal - never SQL/map iteration order.
 * Reused by both the payroll debit-confirmation path (up to all five components) and the cash-repayment
 * path (typically just one loan's interest+principal). Never persists anything - the caller
 * (JciEccsRecoveryService) is responsible for turning the result into {@code JciEccsRecoveryAllocation}
 * rows.
 */
@Service
public class JciEccsRecoveryAllocationService {

    private static final List<JciEccsRecoveryComponent> PRIORITY_ORDER =
            List.of(THRIFT, TERM_INTEREST, EMERGENCY_INTEREST, TERM_PRINCIPAL, EMERGENCY_PRINCIPAL);

    public record ComponentAllocation(JciEccsRecoveryComponent component, BigDecimal expectedAmount, BigDecimal allocatedAmount,
                                       int allocationSequence) {
    }

    /** {@code excessUnallocated} is the over-debit guard (Task spec section 14): if actualAmount exceeds
     * the sum of every component's expectedAmount, the leftover is reported here rather than silently
     * distributed - the caller must flag RECONCILIATION_REQUIRED, never apply it. */
    public record AllocationResult(List<ComponentAllocation> allocations, BigDecimal totalAllocated, BigDecimal excessUnallocated) {
    }

    /** Components with an expectedAmount of zero (or absent from the map) are omitted from the result
     * entirely - they carry no information (no due, no recovery) and match the existing code's own
     * "only post if this component/loan received something" gating. */
    public AllocationResult allocate(Map<JciEccsRecoveryComponent, BigDecimal> expectedByComponent, BigDecimal actualAmount) {
        BigDecimal remaining = actualAmount;
        List<ComponentAllocation> results = new ArrayList<>();
        int sequence = 1;
        for (JciEccsRecoveryComponent component : PRIORITY_ORDER) {
            BigDecimal expected = expectedByComponent.getOrDefault(component, BigDecimal.ZERO);
            if (expected.signum() <= 0) {
                continue;
            }
            BigDecimal allocated = remaining.min(expected);
            remaining = remaining.subtract(allocated);
            results.add(new ComponentAllocation(component, expected, allocated, sequence++));
        }
        BigDecimal totalAllocated = actualAmount.subtract(remaining);
        return new AllocationResult(results, totalAllocated, remaining);
    }
}
