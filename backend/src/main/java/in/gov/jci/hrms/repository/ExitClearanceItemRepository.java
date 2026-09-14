package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.ExitClearanceDepartment;
import in.gov.jci.hrms.entity.ExitClearanceItem;
import in.gov.jci.hrms.entity.ExitClearanceItemStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ExitClearanceItemRepository extends JpaRepository<ExitClearanceItem, Long> {

    List<ExitClearanceItem> findByClearanceRequestId(Long clearanceRequestId);

    /** JciEccsNoDuesService's own department row within a given clearance request (Task4 Phase 3). */
    Optional<ExitClearanceItem> findByClearanceRequestIdAndDepartmentCode(Long clearanceRequestId, ExitClearanceDepartment departmentCode);

    long countByClearanceRequestIdAndStatus(Long clearanceRequestId, ExitClearanceItemStatus status);

    long countByClearanceRequestId(Long clearanceRequestId);

    List<ExitClearanceItem> findByClearanceRequestIdAndStatus(Long clearanceRequestId, ExitClearanceItemStatus status);

    /** Sum of dues raised against an employee's clearance items with unrecovered dues - see TerminalSettlementService's "Dues Deductions" step. Returns null (not 0) when there are no matching rows - callers must coalesce. */
    @Query("SELECT SUM(i.duesRecoveryAmount) FROM ExitClearanceItem i "
            + "WHERE i.clearanceRequest.id = :clearanceRequestId AND i.status = :status")
    BigDecimal sumDuesRecoveryForClearanceRequest(@Param("clearanceRequestId") Long clearanceRequestId,
                                                   @Param("status") ExitClearanceItemStatus status);
}
