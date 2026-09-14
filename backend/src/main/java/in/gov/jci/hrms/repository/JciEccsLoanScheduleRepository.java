package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.JciEccsLoanSchedule;
import in.gov.jci.hrms.entity.JciEccsScheduleStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JciEccsLoanScheduleRepository extends JpaRepository<JciEccsLoanSchedule, Long> {

    List<JciEccsLoanSchedule> findByLoan_IdOrderByInstallmentNoAsc(Long loanId);

    Optional<JciEccsLoanSchedule> findByLoan_IdAndInstallmentNo(Long loanId, int installmentNo);

    /** Used by the collection-snapshot service to find "this loan's due installment for this cycle" -
     * loan_id+cycle_id is not the DB's own unique key (loan_id+installment_no is), but is unique in
     * practice since a schedule never has two installments in the same cycle. */
    Optional<JciEccsLoanSchedule> findByLoan_IdAndCycle_Id(Long loanId, Long cycleId);

    /** The earliest not-yet-fully-paid installment - what a payroll/cash recovery applies against next. */
    List<JciEccsLoanSchedule> findByLoan_IdAndStatusNotOrderByInstallmentNoAsc(Long loanId, JciEccsScheduleStatus paidStatus);

    /** Used to recalculate the remaining schedule after a cash prepayment - every not-yet-fully-paid row
     * is discarded and replaced, PAID rows are real history and are never touched. */
    void deleteByLoan_IdAndStatusNot(Long loanId, JciEccsScheduleStatus paidStatus);
}
