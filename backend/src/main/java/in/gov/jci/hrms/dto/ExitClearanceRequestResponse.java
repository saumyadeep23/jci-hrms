package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.ExitClearanceRequest;
import in.gov.jci.hrms.entity.ExitClearanceStatus;
import in.gov.jci.hrms.entity.SeparationType;

import java.time.LocalDate;
import java.util.List;

public record ExitClearanceRequestResponse(
        Long id,
        Long employeeId,
        String employeeCode,
        String employeeFullName,
        SeparationType separationType,
        LocalDate initiatedDate,
        LocalDate targetReleaseDate,
        ExitClearanceStatus status,
        String releaseOrderRefNo,
        LocalDate releaseOrderDate,
        String remarks,
        List<ExitClearanceItemResponse> items
) {
    public static ExitClearanceRequestResponse from(ExitClearanceRequest request, List<ExitClearanceItemResponse> items) {
        return new ExitClearanceRequestResponse(
                request.getId(), request.getEmployee().getId(), request.getEmployee().getEmployeeCode(),
                request.getEmployee().getFullName(), request.getSeparationType(), request.getInitiatedDate(),
                request.getTargetReleaseDate(), request.getStatus(), request.getReleaseOrderRefNo(),
                request.getReleaseOrderDate(), request.getRemarks(), items);
    }
}
