package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.JciEccsStagingMember;
import in.gov.jci.hrms.entity.StagingRowStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JciEccsStagingMemberRepository extends JpaRepository<JciEccsStagingMember, Long> {

    List<JciEccsStagingMember> findByStatus(StagingRowStatus status);

    long countByStatus(StagingRowStatus status);
}
