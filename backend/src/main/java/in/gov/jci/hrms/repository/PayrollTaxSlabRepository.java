package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.PayrollTaxSlab;
import in.gov.jci.hrms.entity.TaxRegimeType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PayrollTaxSlabRepository extends JpaRepository<PayrollTaxSlab, Long> {

    List<PayrollTaxSlab> findByFinancialYearAndRegimeOrderBySlabMinAsc(String financialYear, TaxRegimeType regime);
}
