package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.CpfPassbookResponseDto;
import in.gov.jci.hrms.dto.CpfResolvedRateDto;
import in.gov.jci.hrms.dto.CpfTrustLedgerEntryResponse;
import in.gov.jci.hrms.entity.CpfLedgerEntryType;
import in.gov.jci.hrms.entity.CpfTrustMemberLedgerEntry;
import in.gov.jci.hrms.repository.CpfTrustMemberLedgerEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Backs GET /api/v1/payroll/trust/cpf/passbook/{employeeId} - the member's posted ledger plus, when this
 * FY's interest hasn't been credited yet (no ANNUAL_INTEREST/INTERIM_SETTLEMENT_INTEREST row posted),
 * an in-memory-only "shadow accrual" projection so the passbook shows an up-to-date corpus estimate
 * without writing anything to the ledger. Nothing here ever persists - the real credit only happens
 * through CpfInterestComputationService's annual run or interim-settlement crystallization.
 */
@Service
@Transactional(readOnly = true)
public class CpfTrustPassbookService {

    private final CpfTrustMemberLedgerEntryRepository ledgerRepository;
    private final CpfRateResolutionService rateResolutionService;

    public CpfTrustPassbookService(CpfTrustMemberLedgerEntryRepository ledgerRepository, CpfRateResolutionService rateResolutionService) {
        this.ledgerRepository = ledgerRepository;
        this.rateResolutionService = rateResolutionService;
    }

    public CpfPassbookResponseDto getPassbook(Long employeeId, String finYear) {
        CpfResolvedRateDto resolvedRate = rateResolutionService.resolveStatutoryRate(finYear);
        List<CpfTrustMemberLedgerEntry> entries = ledgerRepository.findByEmployee_IdAndFinYearOrderByValueDateAscIdAsc(employeeId, finYear);

        boolean interestAlreadyPosted = entries.stream().anyMatch(e ->
                e.getEntryType() == CpfLedgerEntryType.ANNUAL_INTEREST || e.getEntryType() == CpfLedgerEntryType.INTERIM_SETTLEMENT_INTEREST);

        BigDecimal accruedInterestFytd = interestAlreadyPosted
                ? BigDecimal.ZERO
                : shadowAccrual(employeeId, finYear, resolvedRate.baseRate());

        BigDecimal ledgerBalance = entries.isEmpty() ? BigDecimal.ZERO : entries.get(entries.size() - 1).getRunningTotalBalance();
        BigDecimal effectiveTotalCorpus = ledgerBalance.add(accruedInterestFytd);

        String provisionalNotice = resolvedRate.isProvisional()
                ? "Provisional rate applied as per Para 60(2) of EPF Scheme based on FY " + resolvedRate.effectiveFinYear()
                : null;

        return new CpfPassbookResponseDto(employeeId, finYear, entries.stream().map(CpfTrustLedgerEntryResponse::from).toList(),
                ledgerBalance, accruedInterestFytd, effectiveTotalCorpus, resolvedRate.baseRate(), resolvedRate.effectiveFinYear(),
                resolvedRate.isProvisional(), provisionalNotice);
    }

    /** Monthly-product interest over the elapsed April-to-today window of finYear (all 12 months if finYear has already closed) - same formula as CpfInterestComputationService, purely in-memory. */
    private BigDecimal shadowAccrual(Long employeeId, String finYear, BigDecimal baseRate) {
        LocalDate today = LocalDate.now();
        BigDecimal monthlyProductEe = BigDecimal.ZERO;
        BigDecimal monthlyProductEr = BigDecimal.ZERO;
        BigDecimal monthlyProductVpf = BigDecimal.ZERO;
        for (LocalDate monthEnd : CpfInterestComputationService.monthEndsFor(finYear)) {
            if (monthEnd.isAfter(today)) {
                break;
            }
            var closing = ledgerRepository.findFirstByEmployee_IdAndValueDateLessThanEqualOrderByValueDateDescIdDesc(employeeId, monthEnd);
            if (closing.isPresent()) {
                monthlyProductEe = monthlyProductEe.add(closing.get().getRunningEeBalance());
                monthlyProductEr = monthlyProductEr.add(closing.get().getRunningErBalance());
                monthlyProductVpf = monthlyProductVpf.add(closing.get().getRunningVpfBalance());
            }
        }

        BigDecimal virtualEe = CpfInterestComputationService.interestFor(monthlyProductEe, baseRate);
        BigDecimal virtualEr = CpfInterestComputationService.interestFor(monthlyProductEr, baseRate);
        BigDecimal virtualVpf = CpfInterestComputationService.interestFor(monthlyProductVpf, baseRate);
        return virtualEe.add(virtualEr).add(virtualVpf);
    }
}
