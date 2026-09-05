package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.LoanRepayment;
import in.gov.jci.hrms.entity.RepaymentSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record LoanRepaymentResponse(
        Long id,
        Long loanId,
        RepaymentSource repaymentSource,
        BigDecimal amount,
        BigDecimal principalComponent,
        BigDecimal interestComponent,
        LocalDate paymentDate,
        Long payrollRunId,
        String transactionReference,
        Instant createdAt
) {
    public static LoanRepaymentResponse from(LoanRepayment repayment) {
        return new LoanRepaymentResponse(
                repayment.getId(),
                repayment.getLoan().getId(),
                repayment.getRepaymentSource(),
                repayment.getAmount(),
                repayment.getPrincipalComponent(),
                repayment.getInterestComponent(),
                repayment.getPaymentDate(),
                repayment.getPayrollRun() != null ? repayment.getPayrollRun().getId() : null,
                repayment.getTransactionReference(),
                repayment.getCreatedAt()
        );
    }
}
