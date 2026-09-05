package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.MedicalClaim;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MedicalClaimRepository extends JpaRepository<MedicalClaim, Long> {

    Optional<MedicalClaim> findByClaimNumber(String claimNumber);
}
