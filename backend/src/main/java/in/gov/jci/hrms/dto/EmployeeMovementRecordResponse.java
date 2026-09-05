package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.EmployeeMovementRecord;
import in.gov.jci.hrms.entity.JoiningStatus;
import in.gov.jci.hrms.entity.MovementOrderType;
import in.gov.jci.hrms.entity.MovementStatus;
import in.gov.jci.hrms.entity.PayrollSyncStatus;
import in.gov.jci.hrms.entity.SessionType;
import in.gov.jci.hrms.entity.TransferNature;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record EmployeeMovementRecordResponse(
        Long id,
        Long orderId,
        MovementOrderType orderType,
        String orderRefNo,
        LocalDate orderDate,
        LocalDate orderEffectiveDate,

        Long employeeId,
        String employeeName,
        String employeeCode,

        TransferNature transferNature,
        boolean transferBenefitAdmissible,
        String requestApplicationRef,
        String requestReason,

        String fromOfficeName,
        String fromDpcName,
        String fromDepartmentName,
        String fromDesignationTitle,
        String fromPayScale,
        String toOfficeName,
        String toDpcName,
        String toDepartmentName,
        String toDesignationTitle,
        String toPayScale,
        int stationDistanceKm,
        BigDecimal promotionalBasicPay,

        String releaseOrderRef,
        LocalDate releaseDate,
        SessionType releaseSession,
        Instant releasedAtDbTimestamp,

        String joiningReportNo,
        LocalDate joiningDate,
        Instant joiningDbTimestamp,
        SessionType joiningSession,
        BigDecimal joiningLatitude,
        BigDecimal joiningLongitude,
        BigDecimal joiningGpsAccuracy,
        Double joiningDistanceMeters,
        boolean geoVerified,
        String joiningRemarks,

        int admissibleJtDays,
        int joiningTimeAvailedDays,
        int unavailedJtDays,
        int elCreditedDays,
        boolean elCredited,

        int probationPeriodMonths,
        LocalDate probationEndDate,

        PayrollSyncStatus payrollSyncStatus,
        String lpcNumber,
        LocalDate effectivePayFixationDate,
        int excessTransitLwpDays,

        MovementStatus movementStatus,
        JoiningStatus joiningStatus,
        String approvedByOfficerName,
        Instant approvedAt,

        String clarificationRemarks,
        String clarificationRequestedByName,
        Instant clarificationRequestedAt,
        int resubmissionCount,
        Instant resubmittedAt,

        LocalDate lpcIssueDate,
        String lpcSignatoryName,
        String lpcSignatoryDesignation
) {
    public static EmployeeMovementRecordResponse from(EmployeeMovementRecord r) {
        return new EmployeeMovementRecordResponse(
                r.getId(), r.getOrder().getId(), r.getOrder().getOrderType(), r.getOrder().getOrderRefNo(), r.getOrder().getOrderDate(),
                r.getOrder().getEffectiveDate(),
                r.getEmployee().getId(), r.getEmployee().getFullName(), r.getEmployee().getEmployeeCode(),
                r.getTransferNature(), r.isTransferBenefitAdmissible(), r.getRequestApplicationRef(), r.getRequestReason(),
                r.getFromOffice().getName(), r.getFromDpc() != null ? r.getFromDpc().getName() : null,
                r.getFromDepartment() != null ? r.getFromDepartment().getName() : null,
                r.getFromDesignation().getTitle(), r.getFromPayScale(),
                r.getToOffice().getName(), r.getToDpc() != null ? r.getToDpc().getName() : null,
                r.getToDepartment() != null ? r.getToDepartment().getName() : null,
                r.getToDesignation().getTitle(), r.getToPayScale(), r.getStationDistanceKm(), r.getPromotionalBasicPay(),
                r.getReleaseOrderRef(), r.getReleaseDate(), r.getReleaseSession(), r.getReleasedAtDbTimestamp(),
                r.getJoiningReportNo(), r.getJoiningDate(), r.getJoiningDbTimestamp(), r.getJoiningSession(),
                r.getJoiningLatitude(), r.getJoiningLongitude(), r.getJoiningGpsAccuracy(), r.getJoiningDistanceMeters(),
                r.isGeoVerified(), r.getJoiningRemarks(),
                r.getAdmissibleJtDays(), r.getJoiningTimeAvailedDays(), r.getUnavailedJtDays(), r.getElCreditedDays(), r.isElCredited(),
                r.getProbationPeriodMonths(), r.getProbationEndDate(),
                r.getPayrollSyncStatus(), r.getLpcNumber(), r.getEffectivePayFixationDate(), r.getExcessTransitLwpDays(),
                r.getMovementStatus(), r.getJoiningStatus(),
                r.getApprovedByOfficer() != null ? r.getApprovedByOfficer().getFullName() : null, r.getApprovedAt(),
                r.getClarificationRemarks(),
                r.getClarificationRequestedBy() != null ? r.getClarificationRequestedBy().getFullName() : null,
                r.getClarificationRequestedAt(), r.getResubmissionCount(), r.getResubmittedAt(),
                r.getLpcIssueDate(), r.getLpcSignatoryName(), r.getLpcSignatoryDesignation());
    }
}
