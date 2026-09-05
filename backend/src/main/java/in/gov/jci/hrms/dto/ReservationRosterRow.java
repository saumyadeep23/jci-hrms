package in.gov.jci.hrms.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * One social-category x recruitment-stream cell of the reservation
 * register. statutoryTargetPercentage is the current DoPT/DPE model-roster
 * target for unreserved vacancies (SC 15, ST 7.5, OBC-NCL 27, EWS 10) -
 * PwBD (4%, horizontal across all categories) is reported separately via
 * pwbdCount/pwbdPercentage rather than as its own row. This is a
 * representation-percentage summary, not a vacancy-by-vacancy 100-point
 * roster register (which needs a dedicated point-tracking table this
 * schema doesn't yet have) - see ReservationRosterReportService's class
 * comment.
 */
public record ReservationRosterRow(
        String socialCategory,
        String recruitmentStream,
        long count,
        BigDecimal representationPercentage,
        BigDecimal statutoryTargetPercentage
) {
    public static ReservationRosterRow of(String socialCategory, String recruitmentStream, long count, long streamTotal, BigDecimal target) {
        BigDecimal pct = streamTotal == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(count).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(streamTotal), 2, RoundingMode.HALF_UP);
        return new ReservationRosterRow(socialCategory, recruitmentStream, count, pct, target);
    }
}
