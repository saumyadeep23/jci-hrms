package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.SalaryHead;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SalaryHeadRepository extends JpaRepository<SalaryHead, Integer> {

    List<SalaryHead> findAllByOrderByHeadCountAsc();
}
