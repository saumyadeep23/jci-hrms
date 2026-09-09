package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.PayrollStatutoryParameter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PayrollStatutoryParameterRepository extends JpaRepository<PayrollStatutoryParameter, Long> {

    List<PayrollStatutoryParameter> findAllByOrderByParamKeyAscEffectiveFromDesc();

    List<PayrollStatutoryParameter> findByEffectiveToIsNullOrderByParamKeyAsc();

    /** The one open-ended ("current") row for a key - effective_to IS NULL. Empty if the key doesn't exist or (transiently, mid-revision) has none. */
    Optional<PayrollStatutoryParameter> findByParamKeyAndEffectiveToIsNull(String paramKey);

    List<PayrollStatutoryParameter> findByParamKeyOrderByEffectiveFromDesc(String paramKey);

    /** Point-in-time resolution for IdaProjectionEngineService/IdaArrearComputationService - the rate actually in force on a given retro month, rather than always the current open-ended row (in practice these coincide today since every seeded rate has effective_to = NULL, but a mid-window rate revision would make them differ). */
    @Query("SELECT p FROM PayrollStatutoryParameter p WHERE p.paramKey = :paramKey AND p.effectiveFrom <= :targetDate "
            + "AND (p.effectiveTo IS NULL OR p.effectiveTo >= :targetDate) ORDER BY p.effectiveFrom DESC LIMIT 1")
    Optional<PayrollStatutoryParameter> findActiveParamOnDate(@Param("paramKey") String paramKey, @Param("targetDate") LocalDate targetDate);
}
