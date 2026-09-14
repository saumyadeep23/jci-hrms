package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.CpfRuleCeilingComponent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CpfRuleCeilingComponentRepository extends JpaRepository<CpfRuleCeilingComponent, UUID> {

    List<CpfRuleCeilingComponent> findByDetail_IdOrderByDisplayOrderAsc(UUID detailId);
}
