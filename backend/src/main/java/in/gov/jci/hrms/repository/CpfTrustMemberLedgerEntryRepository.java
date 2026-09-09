package in.gov.jci.hrms.repository;

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

    /** Every employee with at least one ledger row - CpfInterestComputationService.computeAnnualInterest()'s member population for a run. */
    @Query("SELECT DISTINCT e.employee.id FROM CpfTrustMemberLedgerEntry e")
    List<Long> findDistinctEmployeeIds();

    /** One loan's own transaction history (its LOAN_WITHDRAWAL plus every principal/interest recovery posted against it) - CpfLoanSettlementService's month-by-month outstanding-principal reconstruction for the early-foreclosure interest rebate. */
    List<CpfTrustMemberLedgerEntry> findByLoan_IdOrderByValueDateAscIdAsc(Long loanId);
}
