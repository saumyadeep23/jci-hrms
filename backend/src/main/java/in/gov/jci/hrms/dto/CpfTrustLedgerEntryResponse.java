package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.CpfLedgerEntryType;
import in.gov.jci.hrms.entity.CpfTrustMemberLedgerEntry;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * One row of a member's CPF Trust passbook - GET /api/v1/payroll/trust/cpf/passbook/{employeeId}. Mirrors
 * v_member_cpf_passbook's own column set exactly (see V80): remarks is deliberately not exposed here (the
 * passbook display never shows it), and displayPeriod is the "Mon-YYYY" period label - derived from
 * sal_month/sal_year when populated (payroll-driven rows), falling back to valueDate otherwise
 * (LOAN_WITHDRAWAL/ANNUAL_INTEREST/etc.).
 */
public record CpfTrustLedgerEntryResponse(
        Long id,
        Long employeeId,
        String finYear,
        Integer salMonth,
        Integer salYear,
        LocalDate valueDate,
        String displayPeriod,
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
        BigDecimal sancCpfLoan,
        BigDecimal sancNrwEe,
        BigDecimal sancNrwEr,
        BigDecimal sancNrwVpf,
        BigDecimal loanRepayPrincipal,
        BigDecimal loanRepayInterest,
        BigDecimal runningEeBalance,
        BigDecimal runningErBalance,
        BigDecimal runningVpfBalance,
        BigDecimal runningTotalBalance,
        BigDecimal runningLoanCpfBalance,
        BigDecimal runningNrwEeBalance,
        BigDecimal runningNrwErBalance,
        BigDecimal runningNrwVpfBalance,
        Long payrollRunId,
        Long transferId,
        Long loanId,
        boolean isProvisionalRate,
        BigDecimal rateApplied,
        String rateSourceFinYear,
        String referenceDocNo,
        Instant createdAt
) {
    private static final DateTimeFormatter DISPLAY_PERIOD_FORMAT = DateTimeFormatter.ofPattern("MMM-yyyy", Locale.ENGLISH);

    public static CpfTrustLedgerEntryResponse from(CpfTrustMemberLedgerEntry e) {
        return new CpfTrustLedgerEntryResponse(
                e.getId(), e.getEmployee().getId(), e.getFinYear(), e.getSalMonth(), e.getSalYear(), e.getValueDate(),
                displayPeriodFor(e.getSalMonth(), e.getSalYear(), e.getValueDate()),
                e.getEntryType(), e.getEeShareCredit(), e.getEeShareDebit(), e.getErShareCredit(), e.getErShareDebit(),
                e.getVpfCredit(), e.getVpfDebit(), e.getInterestCredit(), e.getTotalCredit(), e.getTotalDebit(),
                e.getSancCpfLoan(), e.getSancNrwEe(), e.getSancNrwEr(), e.getSancNrwVpf(),
                e.getLoanRepayPrincipal(), e.getLoanRepayInterest(),
                e.getRunningEeBalance(), e.getRunningErBalance(), e.getRunningVpfBalance(), e.getRunningTotalBalance(),
                e.getRunningLoanCpfBalance(), e.getRunningNrwEeBalance(), e.getRunningNrwErBalance(), e.getRunningNrwVpfBalance(),
                e.getPayrollRun() != null ? e.getPayrollRun().getId() : null,
                e.getTransfer() != null ? e.getTransfer().getId() : null,
                e.getLoan() != null ? e.getLoan().getId() : null,
                e.isProvisionalRate(), e.getRateApplied(), e.getRateSourceFinYear(),
                e.getReferenceDocNo(), e.getCreatedAt());
    }

    /** Mirrors v_member_cpf_passbook's own display_period expression (V80) exactly. */
    private static String displayPeriodFor(Integer salMonth, Integer salYear, LocalDate valueDate) {
        LocalDate period = salMonth != null && salYear != null ? YearMonth.of(salYear, salMonth).atDay(1) : valueDate;
        return DISPLAY_PERIOD_FORMAT.format(period);
    }
}
