package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.TransferNature;
import org.springframework.stereotype.Service;

/**
 * Admissible Joining Time (JT) day slabs and the unavailed/excess-transit math that flows from
 * them. Pure/stateless - no repository or clock dependency, so JoiningReportService owns when
 * these are computed (admissible days at order-creation time, availed/unavailed/excess once the
 * joining report itself is submitted).
 */
@Service
public class JoiningTimeCalculatorService {

    private static final int SAME_STATION_THRESHOLD_KM = 20;
    private static final int MID_RANGE_THRESHOLD_KM = 1000;
    private static final int LONG_RANGE_THRESHOLD_KM = 2000;

    /**
     * OWN_REQUEST transfers never carry JT. A same-station move (&lt;20km) is 1 day, unless there
     * is no relocation at all (0km - e.g. a promotion with no change of station), which needs none.
     * Everything else is banded by distance: &le;1000km -&gt; 10 days, &le;2000km -&gt; 12 days,
     * beyond that -&gt; 15 days.
     */
    public int computeAdmissibleJtDays(TransferNature transferNature, int stationDistanceKm) {
        if (transferNature == TransferNature.OWN_REQUEST) {
            return 0;
        }
        if (stationDistanceKm <= 0) {
            return 0;
        }
        if (stationDistanceKm < SAME_STATION_THRESHOLD_KM) {
            return 1;
        }
        if (stationDistanceKm <= MID_RANGE_THRESHOLD_KM) {
            return 10;
        }
        if (stationDistanceKm <= LONG_RANGE_THRESHOLD_KM) {
            return 12;
        }
        return 15;
    }

    public int computeUnavailedJtDays(int admissibleJtDays, int availedDays) {
        return Math.max(0, admissibleJtDays - availedDays);
    }

    public int computeExcessTransitLwpDays(int admissibleJtDays, int availedDays) {
        return Math.max(0, availedDays - admissibleJtDays);
    }
}
