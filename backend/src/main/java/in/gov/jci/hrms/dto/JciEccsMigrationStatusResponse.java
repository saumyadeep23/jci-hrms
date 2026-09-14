package in.gov.jci.hrms.dto;

import java.util.List;

public record JciEccsMigrationStatusResponse(long pending, long promoted, long rejected, List<RejectedRow> rejectedRows) {
    public record RejectedRow(Long id, String employeeCode, String membershipCode, String rejectionReason) {
    }
}
