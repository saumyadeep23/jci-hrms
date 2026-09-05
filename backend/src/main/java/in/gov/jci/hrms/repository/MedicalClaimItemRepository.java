package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.MedicalClaimItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MedicalClaimItemRepository extends JpaRepository<MedicalClaimItem, Long> {

    List<MedicalClaimItem> findByMedicalClaimId(Long medicalClaimId);
}
