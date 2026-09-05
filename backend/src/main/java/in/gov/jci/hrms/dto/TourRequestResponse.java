package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.TourRequest;
import in.gov.jci.hrms.entity.TourRequestStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record TourRequestResponse(
        Long id,
        String requestNumber,
        Long employeeId,
        String employeeCode,
        String purpose,
        String origin,
        String destination,
        LocalDate startDate,
        LocalDate endDate,
        boolean isPostFacto,
        TourRequestStatus status,
        BigDecimal directFlightCost,
        BigDecimal directHotelCost,
        BigDecimal directVehicleCost,
        Instant createdAt,
        Instant updatedAt
) {
    public static TourRequestResponse from(TourRequest tourRequest) {
        return new TourRequestResponse(
                tourRequest.getId(),
                tourRequest.getRequestNumber(),
                tourRequest.getEmployee().getId(),
                tourRequest.getEmployee().getEmployeeCode(),
                tourRequest.getPurpose(),
                tourRequest.getOrigin(),
                tourRequest.getDestination(),
                tourRequest.getStartDate(),
                tourRequest.getEndDate(),
                tourRequest.isPostFacto(),
                tourRequest.getStatus(),
                tourRequest.getDirectFlightCost(),
                tourRequest.getDirectHotelCost(),
                tourRequest.getDirectVehicleCost(),
                tourRequest.getCreatedAt(),
                tourRequest.getUpdatedAt()
        );
    }
}
