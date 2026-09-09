package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.EmployeeIncomingFundTransfer;
import in.gov.jci.hrms.entity.IncomingTransferPaymentMode;
import in.gov.jci.hrms.entity.IncomingTransferStatus;
import in.gov.jci.hrms.entity.IncomingTransferType;
import in.gov.jci.hrms.entity.PastServiceOrganizationType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record IncomingFundTransferResponse(
        Long id,
        String transferReferenceNo,
        Long employeeId,
        String employeeCode,
        String employeeName,
        Long pastServiceRecordId,
        String sourceOrganizationName,
        PastServiceOrganizationType sourceOrganizationType,
        IncomingTransferType transferType,
        LocalDate relievingDate,
        LocalDate jciJoiningDate,
        IncomingTransferPaymentMode paymentMode,
        String instrumentOrUtrNo,
        LocalDate instrumentDate,
        LocalDate bankRealizationDate,
        String bankAccountCode,
        BigDecimal eeCpfPrincipal,
        BigDecimal eeCpfInterest,
        BigDecimal erJcpfPrincipal,
        BigDecimal erJcpfInterest,
        BigDecimal vpfPrincipal,
        BigDecimal vpfInterest,
        BigDecimal totalCpfTransferred,
        String pensionScheme,
        BigDecimal pensionCorpusAmount,
        String pranOrPpoNo,
        Integer pastQualifyingServiceYears,
        Integer pastQualifyingServiceDays,
        BigDecimal gratuityTransferredAmount,
        boolean gratuityServiceCounted,
        String annexureKDocRef,
        String sanctionOrderNo,
        LocalDate sanctionDate,
        IncomingTransferStatus status,
        Instant creditedAt,
        Long creditedByEmployeeId,
        String rejectionRemarks,
        Instant createdAt
) {
    public static IncomingFundTransferResponse from(EmployeeIncomingFundTransfer t) {
        return new IncomingFundTransferResponse(
                t.getId(), t.getTransferReferenceNo(), t.getEmployee().getId(), t.getEmployee().getEmployeeCode(),
                t.getEmployee().getFullName(), t.getPastServiceRecord() != null ? t.getPastServiceRecord().getId() : null,
                t.getSourceOrganizationName(), t.getSourceOrganizationType(), t.getTransferType(), t.getRelievingDate(),
                t.getJciJoiningDate(), t.getPaymentMode(), t.getInstrumentOrUtrNo(), t.getInstrumentDate(),
                t.getBankRealizationDate(), t.getBankAccountCode(), t.getEeCpfPrincipal(), t.getEeCpfInterest(),
                t.getErJcpfPrincipal(), t.getErJcpfInterest(), t.getVpfPrincipal(), t.getVpfInterest(),
                t.getTotalCpfTransferred(), t.getPensionScheme(), t.getPensionCorpusAmount(), t.getPranOrPpoNo(),
                t.getPastQualifyingServiceYears(), t.getPastQualifyingServiceDays(), t.getGratuityTransferredAmount(),
                t.isGratuityServiceCounted(), t.getAnnexureKDocRef(), t.getSanctionOrderNo(), t.getSanctionDate(),
                t.getStatus(), t.getCreditedAt(), t.getCreditedBy() != null ? t.getCreditedBy().getId() : null,
                t.getRejectionRemarks(), t.getCreatedAt());
    }
}
