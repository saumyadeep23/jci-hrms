package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.DaRateHistory;
import in.gov.jci.hrms.entity.ScaleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DaRateHistoryRepository extends JpaRepository<DaRateHistory, Long> {

    Optional<DaRateHistory> findTopByScaleTypeAndActiveTrueAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            ScaleType scaleType,
            LocalDate effectiveDate
    );

    boolean existsByScaleTypeAndEffectiveFrom(ScaleType scaleType, LocalDate effectiveFrom);

    /** The row whose range the new effectiveFrom would land inside - its effectiveTo gets closed out. */
    Optional<DaRateHistory> findTopByScaleTypeAndEffectiveFromLessThanOrderByEffectiveFromDesc(
            ScaleType scaleType,
            LocalDate effectiveFrom
    );

    /** The next-later row, if any - bounds the new row's own effectiveTo. */
    Optional<DaRateHistory> findTopByScaleTypeAndEffectiveFromGreaterThanOrderByEffectiveFromAsc(
            ScaleType scaleType,
            LocalDate effectiveFrom
    );

    List<DaRateHistory> findAllByOrderByScaleTypeAscEffectiveFromDesc();
}