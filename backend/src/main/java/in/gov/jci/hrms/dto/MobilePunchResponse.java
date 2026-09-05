package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.MobilePunch;
import in.gov.jci.hrms.entity.PunchType;
import in.gov.jci.hrms.entity.ReviewStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record MobilePunchResponse(
        Long id,
        Long employeeId,
        String employeeCode,
        Instant punchTime,
        PunchType punchType,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal accuracyMeters,
        boolean isWithinGeofence,
        ReviewStatus reviewStatus,
        String deviceId,
        String photoS3Key,
        Instant createdAt
) {
    public static MobilePunchResponse from(MobilePunch punch) {
        return new MobilePunchResponse(
                punch.getId(),
                punch.getEmployee().getId(),
                punch.getEmployee().getEmployeeCode(),
                punch.getPunchTime(),
                punch.getPunchType(),
                punch.getLatitude(),
                punch.getLongitude(),
                punch.getAccuracyMeters(),
                punch.isWithinGeofence(),
                punch.getReviewStatus(),
                punch.getDeviceId(),
                punch.getPhotoS3Key(),
                punch.getCreatedAt()
        );
    }
}
