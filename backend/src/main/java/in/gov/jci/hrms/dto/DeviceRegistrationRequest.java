package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.DeviceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** POST /api/v1/attendance/devices/register - employeeId is never trusted from the body, always resolved from the caller's JWT (see RegisteredDeviceController). */
public record DeviceRegistrationRequest(
        @NotBlank @Size(max = 150) String deviceName,
        @NotNull DeviceType deviceType,
        @NotBlank @Size(max = 150) String deviceIdentifier,
        @Size(max = 100) String platform
) {
}
