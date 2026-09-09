package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.CpfStatutoryInterestRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CpfStatutoryInterestRateRepository extends JpaRepository<CpfStatutoryInterestRate, Long> {

    /** The currently-notified rate for a FY, if one has been notified - CpfRateResolutionService's lookup for both the requested FY and, on Para 60(2) fallback, the preceding FY. */
    Optional<CpfStatutoryInterestRate> findByFinYearAndActiveTrue(String finYear);

    /** Notification history/log for the CPF Rate of Interest Entry GUI - newest FY first. */
    List<CpfStatutoryInterestRate> findAllByOrderByFinYearDesc();

    /** cpf_statutory_interest_rates.fin_year is UNIQUE (see V77's own header comment) - pre-check before insert so CpfStatutoryInterestRateService can surface a clean 409 instead of a raw constraint-violation 500. */
    boolean existsByFinYear(String finYear);
}
