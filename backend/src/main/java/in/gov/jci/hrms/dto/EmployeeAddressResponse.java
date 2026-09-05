package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.AddressType;
import in.gov.jci.hrms.entity.EmployeeAddress;

public record EmployeeAddressResponse(
        Long id,
        Long employeeId,
        AddressType addressType,
        String addressLine1,
        String addressLine2,
        String postOffice,
        String policeStation,
        String city,
        String district,
        String state,
        String pinCode
) {
    public static EmployeeAddressResponse from(EmployeeAddress address) {
        return new EmployeeAddressResponse(
                address.getId(),
                address.getEmployee().getId(),
                address.getAddressType(),
                address.getAddressLine1(),
                address.getAddressLine2(),
                address.getPostOffice(),
                address.getPoliceStation(),
                address.getCity(),
                address.getDistrict(),
                address.getState(),
                address.getPinCode()
        );
    }
}
