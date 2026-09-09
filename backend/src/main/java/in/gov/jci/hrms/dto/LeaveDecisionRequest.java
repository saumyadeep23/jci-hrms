package in.gov.jci.hrms.dto;

/** Shared by sanction (remarks optional) and reject (remarks mandatory - LeaveApplicationService enforces that, not bean validation, since the same record serves both). */
public record LeaveDecisionRequest(
        String remarks
) {
}
