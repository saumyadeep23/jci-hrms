package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.LeaveEntitlementBalance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LeaveEntitlementBalanceRepository extends JpaRepository<LeaveEntitlementBalance, Long> {

    Optional<LeaveEntitlementBalance> findByEmployeeIdAndLeaveTypeIdAndYear(Long employeeId, Long leaveTypeId, Integer year);

    List<LeaveEntitlementBalance> findByLeaveTypeIdAndYear(Long leaveTypeId, Integer year);

    /** GET /api/v1/leave-entitlement-balance/mine - a given employee only ever has an EL row today, but this returns every leave type's row for the year in case that changes. */
    List<LeaveEntitlementBalance> findByEmployeeIdAndYear(Long employeeId, Integer year);
}
