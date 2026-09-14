package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.JciEccsLoanRepayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JciEccsLoanRepaymentRepository extends JpaRepository<JciEccsLoanRepayment, Long> {

    List<JciEccsLoanRepayment> findByLoan_IdOrderByRepaymentDateAsc(Long loanId);

    /** Phase 1 reversal - the ledger row(s) a given recovery produced, so a reversal can link back to
     * (reversalOf) and restore against the exact original posting per loan. */
    List<JciEccsLoanRepayment> findByRecovery_Id(Long recoveryId);

    /** Phase 2 reconciliation - every ledger row (normal + reversal) ever posted against one
     * collection_detail line, independent of which recovery produced it, so "posted" can be verified
     * directly from the ledger rather than trusted from the allocation row. */
    List<JciEccsLoanRepayment> findByRecovery_CollectionDetail_Id(Long collectionDetailId);
}
