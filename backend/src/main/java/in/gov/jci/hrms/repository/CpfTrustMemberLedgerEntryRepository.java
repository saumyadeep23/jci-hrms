package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.CpfLedgerEntryType;
import in.gov.jci.hrms.entity.CpfTrustMemberLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CpfTrustMemberLedgerEntryRepository extends JpaRepository<CpfTrustMemberLedgerEntry, Long> {

    /** Full member passbook, oldest first. */
    List<CpfTrustMemberLedgerEntry> findByEmployee_IdOrderByValueDateAscIdAsc(Long employeeId);

    /** One financial year's slice of a member's passbook. */
    List<CpfTrustMemberLedgerEntry> findByEmployee_IdAndFinYearOrderByValueDateAscIdAsc(Long employeeId, String finYear);

    /** The member's most recent entry (any fin year) - source of the running balances a new entry builds on. */
    Optional<CpfTrustMemberLedgerEntry> findFirstByEmployee_IdOrderByValueDateDescIdDesc(Long employeeId);

    /** The member's balance as of (on or before) a given date - CpfInterestComputationService's month-end snapshot lookup. */
    Optional<CpfTrustMemberLedgerEntry> findFirstByEmployee_IdAndValueDateLessThanEqualOrderByValueDateDescIdDesc(Long employeeId, LocalDate onOrBefore);

    /** Every employee with at least one ledger row - CpfInterestRunService's ALL_MEMBERS-scope member population for a run. */
    @Query("SELECT DISTINCT e.employee.id FROM CpfTrustMemberLedgerEntry e")
    List<Long> findDistinctEmployeeIds();

    /** One loan's own transaction history (its LOAN_WITHDRAWAL plus every principal/interest recovery posted against it) - CpfLoanSettlementService's month-by-month outstanding-principal reconstruction for the early-foreclosure interest rebate. */
    List<CpfTrustMemberLedgerEntry> findByLoan_IdOrderByValueDateAscIdAsc(Long loanId);

    /** Every row for this member strictly after a given date, oldest first - CpfInterestRunService's running-balance cascade offsets exactly these rows when a historical interest entry is inserted mid-stream. */
    List<CpfTrustMemberLedgerEntry> findByEmployee_IdAndValueDateGreaterThanOrderByValueDateAscIdAsc(Long employeeId, LocalDate after);

    /** The rows a given interest run posted (ANNUAL_INTEREST) or reversed (ANNUAL_INTEREST_REVERSAL) - CpfInterestRunService.reverseRun()'s precise, unambiguous target set. */
    List<CpfTrustMemberLedgerEntry> findByInterestRun_IdAndEntryType(Long interestRunId, CpfLedgerEntryType entryType);

    /** The earliest value_date across the whole ledger - CpfInterestRunService's basis for computing the first financial year with any calculable opening-balance basis at all (see its own javadoc; never hardcoded). */
    @Query("SELECT MIN(e.valueDate) FROM CpfTrustMemberLedgerEntry e")
    Optional<LocalDate> findEarliestValueDate();

    /** The latest value_date across the whole ledger - CpfInterestRunService's basis for excluding a financial year whose full April-March window isn't yet migrated (Part 19 of the module spec: FY2026-27 is partial as of Aug-2026). */
    @Query("SELECT MAX(e.valueDate) FROM CpfTrustMemberLedgerEntry e")
    Optional<LocalDate> findLatestValueDate();
}
