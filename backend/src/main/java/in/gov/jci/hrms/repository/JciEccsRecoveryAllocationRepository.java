package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.JciEccsRecoveryAllocation;
import in.gov.jci.hrms.entity.JciEccsRecoveryComponent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JciEccsRecoveryAllocationRepository extends JpaRepository<JciEccsRecoveryAllocation, Long> {

    List<JciEccsRecoveryAllocation> findByRecovery_IdOrderByAllocationSequenceAsc(Long recoveryId);

    /** Phase 2 reconciliation - every allocation (across every recovery, including reversals) ever made
     * against one collection_detail line for one component, so actual/posted can be netted independently
     * of any single recovery row. */
    List<JciEccsRecoveryAllocation> findByCollectionDetail_IdAndComponent(Long collectionDetailId, JciEccsRecoveryComponent component);

    List<JciEccsRecoveryAllocation> findByCollectionDetail_Id(Long collectionDetailId);
}
