package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.EmployeeOnboardingDraft;
import in.gov.jci.hrms.entity.OnboardingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EmployeeOnboardingDraftRepository extends JpaRepository<EmployeeOnboardingDraft, Long> {

    Page<EmployeeOnboardingDraft> findByStatus(OnboardingStatus status, Pageable pageable);

    /** See V25 migration for onboarding_draft_seq. */
    @Query(value = "SELECT nextval('onboarding_draft_seq')", nativeQuery = true)
    long nextDraftCodeSequence();
}
