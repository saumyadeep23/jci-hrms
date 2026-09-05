package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.VendorMaster;

import java.math.BigDecimal;
import java.time.LocalDate;

public record VendorMasterResponse(
        Long id,
        String vendorCode,
        String vendorName,
        String tradeName,
        String gstin,
        String panNumber,
        String epfRegistrationNo,
        String esicRegistrationNo,
        LocalDate contractStartDate,
        LocalDate contractEndDate,
        String contactPerson,
        String contactPhone,
        String contactEmail,
        String officeAddress,
        BigDecimal serviceChargePercentage,
        boolean active
) {
    public static VendorMasterResponse from(VendorMaster vendor) {
        return new VendorMasterResponse(
                vendor.getId(), vendor.getVendorCode(), vendor.getVendorName(), vendor.getTradeName(),
                vendor.getGstin(), vendor.getPanNumber(), vendor.getEpfRegistrationNo(), vendor.getEsicRegistrationNo(),
                vendor.getContractStartDate(), vendor.getContractEndDate(), vendor.getContactPerson(),
                vendor.getContactPhone(), vendor.getContactEmail(), vendor.getOfficeAddress(),
                vendor.getServiceChargePercentage(), vendor.isActive());
    }
}
