package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.JciEccsLoan;
import in.gov.jci.hrms.entity.JciEccsLoanProductCode;
import in.gov.jci.hrms.entity.JciEccsLoanStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JciEccsLoanRepository extends JpaRepository<JciEccsLoan, Long> {

    Optional<JciEccsLoan> findByLoanIssueId(String loanIssueId);

    List<JciEccsLoan> findByMember_IdOrderByCreatedAtDesc(Long memberId);

    /** Phase 4 - the "Active Loans" listing/dashboard KPI source (previously no list-all endpoint
     * existed at all; ActiveLoansPage.tsx documented this exact gap). JOIN FETCH loanProduct/
     * disbursementCycle (Phase 5 hardening): JciEccsLoanResponse.from reads both associations' own
     * non-id fields (productCode, cycleCode) for every row, which would otherwise be a per-row lazy
     * initialization - a real N+1 on this exact list/dashboard path, not a speculative one. */
    @Query("SELECT l FROM JciEccsLoan l JOIN FETCH l.loanProduct JOIN FETCH l.disbursementCycle WHERE l.status = :status ORDER BY l.createdAt DESC")
    List<JciEccsLoan> findByStatusOrderByCreatedAtDesc(@Param("status") JciEccsLoanStatus status);

    @Query("SELECT l FROM JciEccsLoan l JOIN FETCH l.loanProduct JOIN FETCH l.disbursementCycle ORDER BY l.createdAt DESC")
    List<JciEccsLoan> findAllByOrderByCreatedAtDesc();

    /** One active Term/Emergency loan per member at a time is the practical norm this module enforces
     * in the service layer (not a DB constraint) - used to find "the" current loan of a given product
     * for collection-snapshot resolution. */
    Optional<JciEccsLoan> findFirstByMember_IdAndLoanProduct_ProductCodeAndStatusOrderByCreatedAtDesc(
            Long memberId, JciEccsLoanProductCode productCode, JciEccsLoanStatus status);

    /** Pessimistic row lock ("SELECT ... FOR UPDATE") taken before debit-confirmation/restructure/top-up
     * reads-and-mutates a loan's outstanding balance, mirroring CpfLoanApplicationRepository.findByIdForUpdate. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM JciEccsLoan l WHERE l.id = :id")
    Optional<JciEccsLoan> findByIdForUpdate(@Param("id") Long id);

    /** Backs the TE-/EM- prefixed loan_issue_id - real Postgres sequences via nextval(), never MAX()+1,
     * same pattern as onboarding_draft_seq + EmployeeOnboardingDraftRepository.nextDraftCodeSequence(). */
    @Query(value = "SELECT nextval('jcieccs_te_loan_seq')", nativeQuery = true)
    long nextTermLoanSequence();

    @Query(value = "SELECT nextval('jcieccs_em_loan_seq')", nativeQuery = true)
    long nextEmergencyLoanSequence();
}
