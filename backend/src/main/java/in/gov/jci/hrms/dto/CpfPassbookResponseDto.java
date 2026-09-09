package in.gov.jci.hrms.dto;

import java.math.BigDecimal;
import java.util.List;

/** GET /api/v1/payroll/trust/cpf/passbook/{employeeId}?finYear= - CpfTrustPassbookService.getPassbook(). */
public record CpfPassbookResponseDto(
        Long employeeId,
        String finYear,
        List<CpfTrustLedgerEntryResponse> entries,
        BigDecimal ledgerBalance,
        BigDecimal accruedInterestFytd,
        BigDecimal effectiveTotalCorpus,
        BigDecimal rateApplied,
        String rateSourceFinYear,
        boolean isProvisionalRate,
        String provisionalNotice
) {
}
