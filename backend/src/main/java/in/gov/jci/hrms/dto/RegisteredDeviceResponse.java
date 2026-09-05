package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.DeviceApprovalStatus;
import in.gov.jci.hrms.entity.DeviceType;
import in.gov.jci.hrms.entity.RegisteredDevice;

import java.time.Instant;

/** officeName mirrors AlmsReportService's COALESCE(dpc, ro, 'Head Office') convention - resolved eagerly here from the (lazy) employee/ro/dpc associations, which is fine at this list's scale (one row per registered device, not per attendance day). */
public record RegisteredDeviceResponse(
        Long id,
        Long employeeId,
        String employeeCode,
        String employeeName,
        String officeName,
        String deviceName,
        DeviceType deviceType,
        String deviceIdentifier,
        String platform,
        DeviceApprovalStatus status,
        String approvedByName,
        Instant approvedAt,
        Instant createdAt
) {
    public static RegisteredDeviceResponse from(RegisteredDevice device) {
        var employee = device.getEmployee();
        String officeName = employee.getDepartmentalPurchaseCentre() != null
                ? employee.getDepartmentalPurchaseCentre().getName()
                : employee.getRegionalOffice() != null ? employee.getRegionalOffice().getName() : "Head Office";
        return new RegisteredDeviceResponse(
                device.getId(),
                employee.getId(),
                employee.getEmployeeCode(),
                employee.getFullName(),
                officeName,
                device.getDeviceName(),
                device.getDeviceType(),
                device.getDeviceIdentifier(),
                device.getPlatform(),
                device.getStatus(),
                device.getApprovedBy() != null ? device.getApprovedBy().getFullName() : null,
                device.getApprovedAt(),
                device.getCreatedAt()
        );
    }
}
