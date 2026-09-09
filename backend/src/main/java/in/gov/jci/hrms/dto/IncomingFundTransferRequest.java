package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.IncomingTransferPaymentMode;
import in.gov.jci.hrms.entity.IncomingTransferType;
import in.gov.jci.hrms.entity.PastServiceOrganizationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** POST /api/v1/payroll/trust/incoming-transfers - IncomingFundTransferService.recordIncomingTransfer(). */
public record IncomingFundTransferRequest(
        @NotNull Long employeeId,
        /** Optional link to the employee's own declared EmployeePastServiceRecord for this same past employer. */
        Long pastServiceRecordId,
        @NotBlank String sourceOrganizationName,
        @NotNull PastServiceOrganizationType sourceOrganizationType,
        @NotNull IncomingTransferType transferType,
        @NotNull LocalDate relievingDate,
        @NotNull LocalDate jciJoiningDate,
        @NotNull IncomingTransferPaymentMode paymentMode,
        @NotBlank String instrumentOrUtrNo,
        @NotNull LocalDate instrumentDate,
        @NotNull LocalDate bankRealizationDate,
        @NotBlank String bankAccountCode,
        @NotNull BigDecimal eeCpfPrincipal,
        @NotNull BigDecimal eeCpfInterest,
        @NotNull BigDecimal erJcpfPrincipal,
        @NotNull BigDecimal erJcpfInterest,
        @NotNull BigDecimal vpfPrincipal,
        @NotNull BigDecimal vpfInterest,
        @NotNull BigDecimal totalCpfTransferred,
        /** Short scheme code (EPS-95, NPS, ...) - max 10 chars, matching the live column. */
        @NotBlank @Size(max = 10) String pensionScheme,
        BigDecimal pensionCorpusAmount,
        String pranOrPpoNo,
        Integer pastQualifyingServiceYears,
        Integer pastQualifyingServiceDays,
        BigDecimal gratuityTransferredAmount,
        boolean gratuityServiceCounted,
        String annexureKDocRef,
        String sanctionOrderNo,
        LocalDate sanctionDate,
        Long createdByEmployeeId
) {
}
