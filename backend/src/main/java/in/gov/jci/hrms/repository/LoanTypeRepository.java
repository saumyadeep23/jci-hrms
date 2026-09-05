package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.LoanType;
import in.gov.jci.hrms.entity.LoanTypeCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LoanTypeRepository extends JpaRepository<LoanType, Long> {

    Optional<LoanType> findByCode(LoanTypeCode code);
}
