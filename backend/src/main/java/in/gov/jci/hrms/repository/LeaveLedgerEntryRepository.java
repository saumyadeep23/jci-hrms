package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.LeaveLedgerEntry;
import in.gov.jci.hrms.entity.LeaveLedgerSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LeaveLedgerEntryRepository extends JpaRepository<LeaveLedgerEntry, Long> {

    List<LeaveLedgerEntry> findByEmployeeId(Long employeeId);

    /** Idempotency guard - re-evaluating the same DailyAttendance row (e.g. a re-run after a punch correction) must never debit twice for it. */
    boolean existsByRelatedDailyAttendanceId(Long dailyAttendanceId);

    /** Used by AttendanceRegularizationService to find the original AUTO_LATE_DEDUCTION debit to reverse on an approved regularization. */
    Optional<LeaveLedgerEntry> findByRelatedDailyAttendanceId(Long dailyAttendanceId);

    /** Used by ElAccrualService's EOL-proxy deduction (LWP debits are written with source = AUTO_LATE_DEDUCTION). */
    List<LeaveLedgerEntry> findByEmployeeIdAndSourceAndEntryDateBetween(
            Long employeeId, LeaveLedgerSource source, LocalDate entryDateFrom, LocalDate entryDateTo);
}
