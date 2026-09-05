package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.DiversionStatus;
import in.gov.jci.hrms.entity.DiversionType;
import in.gov.jci.hrms.entity.EmployeeLoan;
import in.gov.jci.hrms.entity.PfDiversion;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record PfDiversionResponse(
        Long id,
        Long employeeId,
        String employeeCode,
        DiversionType diversionType,
        BigDecimal totalAmount,
        BigDecimal empBucketAmount,
        BigDecimal erBucketAmount,
        BigDecimal vpfBucketAmount,
        Long linkedLoanId,
        LocalDate sanctionDate,
        DiversionStatus status,
        Instant createdAt
) {
    public static PfDiversionResponse from(PfDiversion diversion) {
        EmployeeLoan linkedLoan = diversion.getLinkedLoan();
        return new PfDiversionResponse(
                diversion.getId(),
                diversion.getEmployee().getId(),
                diversion.getEmployee().getEmployeeCode(),
                diversion.getDiversionType(),
                diversion.getTotalAmount(),
                diversion.getEmpBucketAmount(),
                diversion.getErBucketAmount(),
                diversion.getVpfBucketAmount(),
                linkedLoan != null ? linkedLoan.getId() : null,
                diversion.getSanctionDate(),
                diversion.getStatus(),
                diversion.getCreatedAt()
        );
    }
}
