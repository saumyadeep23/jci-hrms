package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.CpfLoanSettlementTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CpfLoanSettlementRepository extends JpaRepository<CpfLoanSettlementTransaction, Long> {

    List<CpfLoanSettlementTransaction> findByLoanIdOrderByCreatedAtDesc(Long loanId);

    /**
     * Part 23 idempotency check - a given instrument/challan number can settle a given loan at most once
     * (uq_cpf_loan_settlement_loan_instrument, V84). A retry with the same key returns this existing row
     * instead of creating another settlement. An explicit @Query, not a derived findBy..., since Spring
     * Data's method-name parser misreads the "Or" inside the property name instrumentOrChallanNo as the
     * keyword OR ("No property 'instrument' found").
     */
    @Query("SELECT t FROM CpfLoanSettlementTransaction t WHERE t.loan.id = :loanId AND t.instrumentOrChallanNo = :instrumentOrChallanNo")
    Optional<CpfLoanSettlementTransaction> findByLoanIdAndInstrumentOrChallanNo(@Param("loanId") Long loanId, @Param("instrumentOrChallanNo") String instrumentOrChallanNo);

    /** Sequence source for CpfLoanSettlementService's "CPFL-RCPT/{finYear}/{seq}" voucher numbering. */
    long countByReceiptVoucherNoStartingWith(String prefix);
}
