package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.BankAccountStatus;
import in.gov.jci.hrms.entity.BankAccountType;
import in.gov.jci.hrms.entity.EmployeeBankAccount;

import java.time.LocalDate;

public record EmployeeBankAccountResponse(
        Long id,
        Long employeeId,
        String bankName,
        String bankBranch,
        String bankAccountNumber,
        String bankIfsc,
        BankAccountType accountType,
        boolean primaryDisbursal,
        BankAccountStatus status,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
) {
    public static EmployeeBankAccountResponse from(EmployeeBankAccount account) {
        return new EmployeeBankAccountResponse(
                account.getId(),
                account.getEmployee().getId(),
                account.getBankName(),
                account.getBankBranch(),
                account.getBankAccountNumber(),
                account.getBankIfsc(),
                account.getAccountType(),
                account.isPrimaryDisbursal(),
                account.getStatus(),
                account.getEffectiveFrom(),
                account.getEffectiveTo()
        );
    }
}
