package in.gov.jci.hrms.dto;

import java.math.BigDecimal;

/**
 * One employment-category row of the 4-Tier Manpower report - PIMS_SPEC.md
 * dashboard card 4. totalCost/averageCost are each normalized to a monthly
 * basis (REGULAR: current basic pay from regular_pay_fixations, CASUAL:
 * daily wage rate x 26 working days, CONTRACTUAL: monthly lump sum,
 * OUTSOURCED: monthly CTC) - see costBasis for the label shown per tier.
 */
public record ManpowerTierRow(
        String employmentCategory,
        String costBasis,
        long headcount,
        BigDecimal totalCost,
        BigDecimal averageCost
) {
}
