package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.DepartmentalPurchaseCentre;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DepartmentalPurchaseCentreRepository extends JpaRepository<DepartmentalPurchaseCentre, Long> {

    Optional<DepartmentalPurchaseCentre> findByCode(String code);
}
