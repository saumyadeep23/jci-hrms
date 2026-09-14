package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.CpfRuleDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CpfRuleDocumentRepository extends JpaRepository<CpfRuleDocument, UUID> {

    List<CpfRuleDocument> findByDetail_Id(UUID detailId);
}
