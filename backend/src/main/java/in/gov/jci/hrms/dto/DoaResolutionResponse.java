package in.gov.jci.hrms.dto;

public record DoaResolutionResponse(
        Long approverEmployeeId,
        String approverEmployeeCode,
        Long approverPostId,
        String approverPostTitle,
        String routingReason
) {
}
