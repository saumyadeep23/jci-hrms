package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.CpfBalanceLedger;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record CpfBalanceLedgerResponse(
        Long id,
        Long employeeId,
        String employeeCode,
        BigDecimal employeeFundBalance,
        BigDecimal employerFundBalance,
        BigDecimal vpfBalance,
        LocalDate lastInterestCreditedDate,
        Instant updatedAt
) {
    public static CpfBalanceLedgerResponse from(CpfBalanceLedger ledger) {
        return new CpfBalanceLedgerResponse(
                ledger.getId(),
                ledger.getEmployee().getId(),
                ledger.getEmployee().getEmployeeCode(),
                ledger.getEmployeeFundBalance(),
                ledger.getEmployerFundBalance(),
                ledger.getVpfBalance(),
                ledger.getLastInterestCreditedDate(),
                ledger.getUpdatedAt()
        );
    }
}
