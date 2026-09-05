package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.PayScale;
import in.gov.jci.hrms.entity.ScaleType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PayScaleRepository extends JpaRepository<PayScale, Long> {

    Optional<PayScale> findByScaleTypeAndGrade(ScaleType scaleType, String grade);
}
