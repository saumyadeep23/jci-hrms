package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.AparCycle;
import in.gov.jci.hrms.entity.AparCycleStatus;

import java.time.Instant;
import java.time.LocalDate;

public record AparCycleResponse(
        Long id,
        String cycleYear,
        LocalDate startDate,
        LocalDate endDate,
        AparCycleStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static AparCycleResponse from(AparCycle cycle) {
        return new AparCycleResponse(
                cycle.getId(),
                cycle.getCycleYear(),
                cycle.getStartDate(),
                cycle.getEndDate(),
                cycle.getStatus(),
                cycle.getCreatedAt(),
                cycle.getUpdatedAt()
        );
    }
}
