package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.CpfLoanRecoveryPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CpfLoanRecoveryPolicyRepository extends JpaRepository<CpfLoanRecoveryPolicy, Long> {

    /** Same "current row has effectiveTo == null" convention as CpfPayrollDeductionCapRepository. */
    Optional<CpfLoanRecoveryPolicy> findByEffectiveToIsNull();

    List<CpfLoanRecoveryPolicy> findAllByOrderByEffectiveFromDesc();
}
