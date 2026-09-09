package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.TransportAllowanceRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransportAllowanceRateRepository extends JpaRepository<TransportAllowanceRate, Long> {

    /** No effective_to on this table (unlike PayrollHraRate) - the most recent effective_from for the pair is the active one. */
    List<TransportAllowanceRate> findByGradeScale_IdAndCityClassOrderByEffectiveFromDesc(Long gradeScaleId, String cityClass);
}
