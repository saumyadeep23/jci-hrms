package in.gov.jci.hrms.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** GOI CCS Form 1 bifurcated (Enjoyable/Encashable) EL leave register for one employee/calendar year - ALMS Reporting Workbench. */
public record CcsForm1Response(
        Long employeeId,
        String employeeCode,
        String employeeName,
        String designation,
        int calendarYear,
        BigDecimal openingEnjoyable,
        BigDecimal openingEncashable,
        BigDecimal advanceCreditEnjoyable,
        BigDecimal advanceCreditEncashable,
        BigDecimal eolDeduction,
        List<CcsForm1Transaction> availedTransactions,
        BigDecimal closingEnjoyable,
        BigDecimal closingEncashable,
        BigDecimal totalBalance,
        /** Encashable balance in excess of what a 300-day-cap career limit could ever pay out at once - a soft "review" signal, not a hard rule enforced anywhere else. */
        BigDecimal surplusBuffer
) {
    public record CcsForm1Transaction(
            LocalDate fromDate,
            LocalDate toDate,
            String type,
            BigDecimal enjoyableDebited,
            BigDecimal encashableDebited,
            String orderRef
    ) {
    }
}
