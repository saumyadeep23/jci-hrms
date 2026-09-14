package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.CpfApplication;
import in.gov.jci.hrms.entity.CpfApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CpfApplicationRepository extends JpaRepository<CpfApplication, UUID> {

    List<CpfApplication> findByEmployeeCodeOrderByCreatedAtDesc(String employeeCode);

    List<CpfApplication> findByEmployeeCodeAndPurpose_IdAndStatusIn(String employeeCode, UUID purposeId, List<CpfApplicationStatus> statuses);

    long countByApplicationNumberStartingWith(String prefix);
}
