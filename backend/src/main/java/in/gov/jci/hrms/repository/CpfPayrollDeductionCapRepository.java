package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.CpfPayrollDeductionCap;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CpfPayrollDeductionCapRepository extends JpaRepository<CpfPayrollDeductionCap, Long> {

    Optional<CpfPayrollDeductionCap> findByEffectiveToIsNull();

    List<CpfPayrollDeductionCap> findAllByOrderByEffectiveFromDesc();
}
