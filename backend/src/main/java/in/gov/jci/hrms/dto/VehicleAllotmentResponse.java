package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.EmployeeVehicleAllotment;
import in.gov.jci.hrms.entity.VehicleAllotmentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record VehicleAllotmentResponse(
        Long id,
        Long employeeId,
        String allotmentOrderNo,
        String vehicleRegNo,
        String vehicleMakeModel,
        boolean driverProvided,
        boolean personalUseAllowed,
        boolean deductionApplicable,
        BigDecimal monthlyDeductionAmount,
        LocalDate allottedFrom,
        LocalDate surrenderedOn,
        VehicleAllotmentStatus status,
        String remarks,
        Instant createdAt,
        Instant updatedAt
) {
    public static VehicleAllotmentResponse from(EmployeeVehicleAllotment allotment) {
        return new VehicleAllotmentResponse(
                allotment.getId(),
                allotment.getEmployee().getId(),
                allotment.getAllotmentOrderNo(),
                allotment.getVehicleRegNo(),
                allotment.getVehicleMakeModel(),
                allotment.isDriverProvided(),
                allotment.isPersonalUseAllowed(),
                allotment.isDeductionApplicable(),
                allotment.getMonthlyDeductionAmount(),
                allotment.getAllottedFrom(),
                allotment.getSurrenderedOn(),
                allotment.getStatus(),
                allotment.getRemarks(),
                allotment.getCreatedAt(),
                allotment.getUpdatedAt());
    }
}
