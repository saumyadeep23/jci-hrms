package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.PayrollRun;
import in.gov.jci.hrms.entity.PayrollRunStatus;
import in.gov.jci.hrms.entity.PayrollRunType;

import java.time.Instant;
import java.time.LocalDate;

public record PayrollRunResponse(
        Long id,
        Integer cycleYear,
        Integer cycleMonth,
        LocalDate startDate,
        LocalDate endDate,
        PayrollRunStatus status,
        String finalizedBy,
        Instant finalizedAt,
        PayrollRunType runType,
        boolean isMigrated,
        Instant createdAt
) {
    public static PayrollRunResponse from(PayrollRun run) {
        return new PayrollRunResponse(
                run.getId(),
                run.getCycleYear(),
                run.getCycleMonth(),
                run.getStartDate(),
                run.getEndDate(),
                run.getStatus(),
                run.getFinalizedBy(),
                run.getFinalizedAt(),
                run.getRunType(),
                run.isMigrated(),
                run.getCreatedAt()
        );
    }
}
