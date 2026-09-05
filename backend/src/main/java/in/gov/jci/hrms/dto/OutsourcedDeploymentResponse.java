package in.gov.jci.hrms.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import in.gov.jci.hrms.entity.OutsourcedDeployment;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OutsourcedDeploymentResponse(
        Long id,
        Long employeeId,
        Long vendorId,
        String vendorName,
        String scaleCode,
        BigDecimal monthlyCtc,
        BigDecimal agencyBillingRate,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy") LocalDate deploymentStartDate,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy") LocalDate deploymentEndDate,
        String workOrderRef,
        boolean current
) {
    public static OutsourcedDeploymentResponse from(OutsourcedDeployment deployment) {
        return new OutsourcedDeploymentResponse(
                deployment.getId(),
                deployment.getEmployee().getId(),
                deployment.getVendor() != null ? deployment.getVendor().getId() : null,
                deployment.getVendor() != null ? deployment.getVendor().getVendorName() : null,
                deployment.getGradeScale() != null ? deployment.getGradeScale().getScaleCode() : null,
                deployment.getMonthlyCtc(),
                deployment.getAgencyBillingRate(),
                deployment.getDeploymentStartDate(),
                deployment.getDeploymentEndDate(),
                deployment.getWorkOrderRef(),
                deployment.isCurrent());
    }
}
