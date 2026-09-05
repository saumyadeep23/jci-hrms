package in.gov.jci.hrms.dto;

public record IfscLookupResponse(String ifsc, boolean found, String bankName, String branch, String message) {
}
