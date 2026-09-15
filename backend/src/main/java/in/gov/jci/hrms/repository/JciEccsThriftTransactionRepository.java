package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.JciEccsThriftTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JciEccsThriftTransactionRepository extends JpaRepository<JciEccsThriftTransaction, Long> {

    Optional<JciEccsThriftTransaction> findTopByMember_IdOrderByIdDesc(Long memberId);

    List<JciEccsThriftTransaction> findByMember_Id(Long memberId);

    /** Phase 2 reconciliation - every thrift ledger row (CONTRIBUTION + REFUND) ever posted against one
     * collection_detail line. */
    List<JciEccsThriftTransaction> findByCollectionDetail_Id(Long collectionDetailId);
}
