package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.JciEccsReconciliation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JciEccsReconciliationRepository extends JpaRepository<JciEccsReconciliation, Long> {

    /** The upsert key (V91 partial unique index) - reconcilePayrollRun() refreshes this row instead of
     * inserting a duplicate on a repeat run. */
    Optional<JciEccsReconciliation> findByCollectionDetail_IdAndComponent(Long collectionDetailId,
                                                                           in.gov.jci.hrms.entity.JciEccsRecoveryComponent component);

    List<JciEccsReconciliation> findByCollectionBatch_IdOrderByMember_MembershipCodeAsc(Long collectionBatchId);

    List<JciEccsReconciliation> findByMember_IdOrderByDetectedAtDesc(Long memberId);

    List<JciEccsReconciliation> findByStatusNotAndResolvedFalseOrderByDetectedAtDesc(
            in.gov.jci.hrms.entity.JciEccsReconciliationStatus matchedStatus);
}
