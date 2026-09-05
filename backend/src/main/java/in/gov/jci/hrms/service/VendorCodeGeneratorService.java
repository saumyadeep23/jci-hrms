package in.gov.jci.hrms.service;

import in.gov.jci.hrms.repository.VendorMasterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * PIMS_SPEC.md Section 1.A.7 (Manpower Vendors): vendor_code is system-generated
 * as "VND-" plus the current highest numeric suffix across all vendors, zero-padded
 * to 4 digits (e.g. "VND-0001", "VND-0104"), never entered manually. Same
 * MAX+1-under-advisory-lock approach as EmployeeCodeGeneratorService, since a plain
 * MAX+1 has no built-in concurrency protection.
 */
@Service
public class VendorCodeGeneratorService {

    private static final String PREFIX = "VND-";

    private final VendorMasterRepository vendorMasterRepository;

    public VendorCodeGeneratorService(VendorMasterRepository vendorMasterRepository) {
        this.vendorMasterRepository = vendorMasterRepository;
    }

    /**
     * REQUIRES_NEW so a caller's surrounding transaction rolling back never rolls
     * back the advisory lock/number allocation - see EmployeeCodeGeneratorService.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String generateNext() {
        vendorMasterRepository.acquireVendorCodeGenerationLock();
        int nextNumber = vendorMasterRepository.nextVendorCodeNumber();
        return PREFIX + "%04d".formatted(nextNumber);
    }
}
