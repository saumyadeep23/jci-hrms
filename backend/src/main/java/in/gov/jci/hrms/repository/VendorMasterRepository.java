package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.VendorMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface VendorMasterRepository extends JpaRepository<VendorMaster, Long> {

    boolean existsByVendorCode(String vendorCode);

    boolean existsByGstinIgnoreCase(String gstin);

    boolean existsByGstinIgnoreCaseAndIdNot(String gstin, Long id);

    /** Mirrors EmployeeRepository's "current highest numeric suffix + 1" vendor_code algorithm, formatted as VND-0001, VND-0002, ... */
    @Query(value = "SELECT COALESCE(MAX(SUBSTRING(vendor_code FROM 5)::INTEGER), 0) + 1 "
            + "FROM vendor_master WHERE vendor_code ~ '^VND-[0-9]+$'", nativeQuery = true)
    int nextVendorCodeNumber();

    /** Session-scoped advisory lock serializing concurrent vendor_code allocations - see EmployeeRepository.acquireEmployeeCodeGenerationLock(). */
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(778899002)) lock_acquired", nativeQuery = true)
    int acquireVendorCodeGenerationLock();
}
