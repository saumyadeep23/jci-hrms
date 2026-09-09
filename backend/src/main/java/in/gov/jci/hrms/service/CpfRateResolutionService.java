package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.CpfResolvedRateDto;
import in.gov.jci.hrms.entity.CpfStatutoryInterestRate;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.repository.CpfStatutoryInterestRateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves which notified CPF interest rate applies to a financial year, enforcing Paragraph 60(2) of the
 * EPF Scheme: if the current FY's rate hasn't been notified yet, the immediately preceding FY's notified
 * rate is applied provisionally rather than blocking every passbook read and exit settlement until the
 * government/CBT notification catches up.
 */
@Service
@Transactional(readOnly = true)
public class CpfRateResolutionService {

    private final CpfStatutoryInterestRateRepository rateRepository;

    public CpfRateResolutionService(CpfStatutoryInterestRateRepository rateRepository) {
        this.rateRepository = rateRepository;
    }

    public CpfResolvedRateDto resolveStatutoryRate(String finYear) {
        var notified = rateRepository.findByFinYearAndActiveTrue(finYear);
        if (notified.isPresent()) {
            CpfStatutoryInterestRate rate = notified.get();
            return new CpfResolvedRateDto(rate.getBaseCpfRate(), rate.getEffectiveLoanRate(), finYear, finYear, false);
        }

        String precedingFinYear = precedingFinYear(finYear);
        var fallback = rateRepository.findByFinYearAndActiveTrue(precedingFinYear);
        if (fallback.isPresent()) {
            CpfStatutoryInterestRate rate = fallback.get();
            return new CpfResolvedRateDto(rate.getBaseCpfRate(), rate.getEffectiveLoanRate(), finYear, precedingFinYear, true);
        }

        throw new BusinessRuleViolationException(
                "No notified CPF interest rate found for FY " + finYear + " or, under Para 60(2) fallback, the preceding FY "
                        + precedingFinYear + " - seed cpf_statutory_interest_rates for at least one of them before requesting this passbook or settlement.");
    }

    /** "2026-2027" -> "2025-2026" - the FY immediately before the given one. */
    static String precedingFinYear(String finYear) {
        String[] parts = finYear.split("-");
        if (parts.length != 2) {
            throw new BusinessRuleViolationException("finYear must be in \"YYYY-YYYY\" form, got: " + finYear);
        }
        int startYear = Integer.parseInt(parts[0]) - 1;
        return startYear + "-" + (startYear + 1);
    }
}
