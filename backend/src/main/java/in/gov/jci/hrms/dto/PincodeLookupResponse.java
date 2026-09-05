package in.gov.jci.hrms.dto;

import java.util.List;

/** postOffices lists every post office Name in the pincode (for a City/Post Office picker) - empty when not found. */
public record PincodeLookupResponse(String pincode, boolean found, String district, String state, String message,
                                     List<String> postOffices) {
}
