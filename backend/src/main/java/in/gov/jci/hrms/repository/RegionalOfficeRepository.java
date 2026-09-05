package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.RegionalOffice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RegionalOfficeRepository extends JpaRepository<RegionalOffice, Long> {

    Optional<RegionalOffice> findByCode(String code);

    Optional<RegionalOffice> findByName(String name);
}
