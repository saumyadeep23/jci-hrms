package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.CityClass;
import in.gov.jci.hrms.entity.TadaRateMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TadaRateMasterRepository extends JpaRepository<TadaRateMaster, Long> {

    Optional<TadaRateMaster> findByDesignationIdAndCityClassAndActiveTrue(Long designationId, CityClass cityClass);
}
