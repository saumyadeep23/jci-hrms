package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.EmployeeQuarterAllotment;
import in.gov.jci.hrms.entity.QuarterAllotmentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record QuarterAllotmentResponse(
        Long id,
        Long employeeId,
        String allotmentOrderNo,
        String addressLine1,
        String addressLine2,
        String city,
        String stateCode,
        String pincode,
        boolean syncCurrentAddress,
        BigDecimal licenseFee,
        BigDecimal waterCharges,
        BigDecimal electricCharges,
        LocalDate allottedFrom,
        LocalDate vacatedOn,
        QuarterAllotmentStatus status,
        String remarks,
        Instant createdAt,
        Instant updatedAt
) {
    public static QuarterAllotmentResponse from(EmployeeQuarterAllotment allotment) {
        return new QuarterAllotmentResponse(
                allotment.getId(),
                allotment.getEmployee().getId(),
                allotment.getAllotmentOrderNo(),
                allotment.getAddressLine1(),
                allotment.getAddressLine2(),
                allotment.getCity(),
                allotment.getStateCode(),
                allotment.getPincode(),
                allotment.isSyncCurrentAddress(),
                allotment.getLicenseFee(),
                allotment.getWaterCharges(),
                allotment.getElectricCharges(),
                allotment.getAllottedFrom(),
                allotment.getVacatedOn(),
                allotment.getStatus(),
                allotment.getRemarks(),
                allotment.getCreatedAt(),
                allotment.getUpdatedAt());
    }
}
