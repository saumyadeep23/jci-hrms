package in.gov.jci.hrms.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record CpfRuleSimulatorResponse(
        String ruleVersionId,
        String ruleVersionTag,
        String ruleStatus,
        boolean serviceEligible,
        String serviceEligibilityReason,
        boolean frequencyEligible,
        String frequencyReason,
        BigDecimal totalEligibleBalance,
        BigDecimal finalEligibleAmount,
        Map<String, BigDecimal> debitAllocationByHead,
        BigDecimal projectedEmi,
        boolean taxLikely,
        List<String> calculationTrace
) {
}
