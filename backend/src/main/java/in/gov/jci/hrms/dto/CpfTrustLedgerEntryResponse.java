package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.CpfLedgerEntryType;
import in.gov.jci.hrms.entity.CpfTrustMemberLedgerEntry;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** One row of a member's CPF Trust passbook - GET /api/v1/payroll/trust/cpf/passbook/{employeeId}. */
public record CpfTrustLedgerEntryResponse(
        Long id,
        Long employeeId,
        String finYear,
        Integer salMonth,
        Integer salYear,
        LocalDate valueDate,
        CpfLedgerEntryType entryType,
        BigDecimal eeShareCredit,
        BigDecimal eeShareDebit,
        BigDecimal erShareCredit,
        BigDecimal erShareDebit,
        BigDecimal vpfCredit,
        BigDecimal vpfDebit,
        BigDecimal interestCredit,
        BigDecimal totalCredit,
        BigDecimal totalDebit,
        BigDecimal runningEeBalance,
        BigDecimal runningErBalance,
        BigDecimal runningVpfBalance,
        BigDecimal runningTotalBalance,
        Long payrollRunId,
        Long transferId,
        Long loanId,
        boolean isProvisionalRate,
        BigDecimal rateApplied,
        String rateSourceFinYear,
        String referenceDocNo,
        String remarks,
        Instant createdAt
) {
    public static CpfTrustLedgerEntryResponse from(CpfTrustMemberLedgerEntry e) {
        return new CpfTrustLedgerEntryResponse(
                e.getId(), e.getEmployee().getId(), e.getFinYear(), e.getSalMonth(), e.getSalYear(), e.getValueDate(),
                e.getEntryType(), e.getEeShareCredit(), e.getEeShareDebit(), e.getErShareCredit(), e.getErShareDebit(),
                e.getVpfCredit(), e.getVpfDebit(), e.getInterestCredit(), e.getTotalCredit(), e.getTotalDebit(),
                e.getRunningEeBalance(), e.getRunningErBalance(), e.getRunningVpfBalance(), e.getRunningTotalBalance(),
                e.getPayrollRun() != null ? e.getPayrollRun().getId() : null,
                e.getTransfer() != null ? e.getTransfer().getId() : null,
                e.getLoan() != null ? e.getLoan().getId() : null,
                e.isProvisionalRate(), e.getRateApplied(), e.getRateSourceFinYear(),
                e.getReferenceDocNo(), e.getRemarks(), e.getCreatedAt());
    }
}
