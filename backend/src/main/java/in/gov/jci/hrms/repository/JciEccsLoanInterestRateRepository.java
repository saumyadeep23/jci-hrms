package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.JciEccsLoanInterestRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JciEccsLoanInterestRateRepository extends JpaRepository<JciEccsLoanInterestRate, Long> {

    Optional<JciEccsLoanInterestRate> findByLoanProduct_IdAndEffectiveToIsNull(Long loanProductId);
}
