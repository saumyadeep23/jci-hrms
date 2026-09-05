package in.gov.jci.hrms.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** One row of the Cadre Strength Statement - PIMS_SPEC.md dashboard card 2's report. */
public record CadreStrengthRow(
        String locationType,
        long sanctioned,
        long occupied,
        long vacant,
        long frozen,
        BigDecimal occupancyPercentage
) {
    public static CadreStrengthRow of(String locationType, long sanctioned, long occupied, long vacant, long frozen) {
        BigDecimal occupancy = sanctioned == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(occupied).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(sanctioned), 2, RoundingMode.HALF_UP);
        return new CadreStrengthRow(locationType, sanctioned, occupied, vacant, frozen, occupancy);
    }
}
