package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.JciEccsLoanProduct;
import in.gov.jci.hrms.entity.JciEccsLoanProductCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JciEccsLoanProductRepository extends JpaRepository<JciEccsLoanProduct, Long> {

    Optional<JciEccsLoanProduct> findByProductCode(JciEccsLoanProductCode productCode);
}
