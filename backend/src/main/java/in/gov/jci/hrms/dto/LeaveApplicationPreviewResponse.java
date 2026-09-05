package in.gov.jci.hrms.dto;

import java.math.BigDecimal;

/**
 * calendarDays is the raw span (endDate - startDate + 1) regardless of leave
 * type; debitableDays is what LeaveValidationService would actually debit
 * (null when valid is false, since no meaningful figure exists). message is
 * the CCS-rule violation text when valid is false, null otherwise.
 */
public record LeaveApplicationPreviewResponse(
        BigDecimal calendarDays,
        BigDecimal debitableDays,
        boolean valid,
        String message
) {
}
