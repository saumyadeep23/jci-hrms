package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.TadaClaim;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TadaClaimRepository extends JpaRepository<TadaClaim, Long> {

    Optional<TadaClaim> findByClaimNumber(String claimNumber);
}
