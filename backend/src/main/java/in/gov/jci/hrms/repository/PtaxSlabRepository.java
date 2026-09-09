package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.PtaxSlab;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PtaxSlabRepository extends JpaRepository<PtaxSlab, Long> {

    List<PtaxSlab> findByStateCodeOrderBySlabMinAsc(String stateCode);
}
