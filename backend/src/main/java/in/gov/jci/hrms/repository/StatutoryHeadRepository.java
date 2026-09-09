package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.StatutoryHead;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StatutoryHeadRepository extends JpaRepository<StatutoryHead, Integer> {

    List<StatutoryHead> findAllByOrderByStatHeadCountAsc();
}
