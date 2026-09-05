package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PostMasterRequest(
        @NotBlank @Size(max = 30) String postCode,
        @NotBlank @Size(max = 150) String title,
        @NotNull Long departmentId,
        @NotNull Long designationId,
        Long roId,
        Long dpcId,
        Long operationalReportingPostId,
        Long administrativeReportingPostId,
        Long acceptingAuthorityPostId,
        @NotNull Boolean isBudgeted,
        @NotNull Boolean active
) {
}
