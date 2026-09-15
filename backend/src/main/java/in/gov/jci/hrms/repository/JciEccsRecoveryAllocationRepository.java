package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.JciEccsRecoveryAllocation;
import in.gov.jci.hrms.entity.JciEccsRecoveryComponent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface JciEccsRecoveryAllocationRepository extends JpaRepository<JciEccsRecoveryAllocation, Long> {

    List<JciEccsRecoveryAllocation> findByRecovery_IdOrderByAllocationSequenceAsc(Long recoveryId);

    /** Phase 5 hardening - batch-fetches allocations for a whole page of recoveries in one query
     * (JciEccsRecoveryController#list groups the result by recovery id itself) instead of the previous
     * one-query-per-recovery N+1 on the audit trail page (up to 100 extra queries per page load). */
    @Query("SELECT a FROM JciEccsRecoveryAllocation a WHERE a.recovery.id IN :recoveryIds ORDER BY a.recovery.id ASC, a.allocationSequence ASC")
    List<JciEccsRecoveryAllocation> findByRecovery_IdIn(@Param("recoveryIds") Collection<Long> recoveryIds);

    /** Phase 2 reconciliation - every allocation (across every recovery, including reversals) ever made
     * against one collection_detail line for one component, so actual/posted can be netted independently
     * of any single recovery row. */
    List<JciEccsRecoveryAllocation> findByCollectionDetail_IdAndComponent(Long collectionDetailId, JciEccsRecoveryComponent component);

    List<JciEccsRecoveryAllocation> findByCollectionDetail_Id(Long collectionDetailId);
}
