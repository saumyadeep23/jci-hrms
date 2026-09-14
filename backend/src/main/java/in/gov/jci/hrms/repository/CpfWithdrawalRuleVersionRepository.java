package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.CpfRuleStatus;
import in.gov.jci.hrms.entity.CpfWithdrawalRuleVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CpfWithdrawalRuleVersionRepository extends JpaRepository<CpfWithdrawalRuleVersion, UUID> {

    List<CpfWithdrawalRuleVersion> findByPurpose_CodeOrderByEffectiveFromDesc(String purposeCode);

    List<CpfWithdrawalRuleVersion> findAllByOrderByPurpose_CodeAscEffectiveFromDesc();

    List<CpfWithdrawalRuleVersion> findByStatusOrderByPurpose_CodeAsc(CpfRuleStatus status);

    /**
     * The version governing a purpose as of a given date - the most recent APPROVED version whose
     * [effectiveFrom, effectiveTo] window covers asOf. There can be at most one in practice (
     * {@code CpfWithdrawalRuleService.approve()} closes the prior version's effectiveTo before/when
     * approving a new one), but this is not a database-level guarantee on the live schema (see
     * CpfWithdrawalRuleVersion's own javadoc), so the query itself takes "most recent effectiveFrom" as
     * authoritative rather than assuming uniqueness.
     */
    List<CpfWithdrawalRuleVersion> findByPurpose_CodeAndStatusAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            String purposeCode, CpfRuleStatus status, LocalDate asOf);

    Optional<CpfWithdrawalRuleVersion> findFirstByPurpose_IdAndStatusOrderByEffectiveFromDesc(UUID purposeId, CpfRuleStatus status);
}
