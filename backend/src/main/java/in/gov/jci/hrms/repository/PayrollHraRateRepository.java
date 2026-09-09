package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.PayrollHraRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface PayrollHraRateRepository extends JpaRepository<PayrollHraRate, Long> {

    List<PayrollHraRate> findAllByOrderByCityClassAscEffectiveFromDesc();

    @Query("SELECT r FROM PayrollHraRate r WHERE r.effectiveFrom <= :date AND (r.effectiveTo IS NULL OR r.effectiveTo >= :date) "
            + "ORDER BY r.cityClass ASC")
    List<PayrollHraRate> findActiveOn(@Param("date") LocalDate date);
}
