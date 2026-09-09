package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.PayrollMonthlyHeadItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface PayrollMonthlyHeadItemRepository extends JpaRepository<PayrollMonthlyHeadItem, Long> {

    List<PayrollMonthlyHeadItem> findByRecord_TranId(Long tranId);

    /** SuspensionLifecycleService.revokeAndRegularize()'s back-pay arrear computation - total already actually paid under one head to one employee across a month range, read from real persisted payroll history rather than reconstructed from a subsistence-percentage timeline this schema doesn't retain. */
    @Query("SELECT COALESCE(SUM(h.amount), 0) FROM PayrollMonthlyHeadItem h "
            + "WHERE h.record.employee.id = :employeeId AND h.headCount = :headCount "
            + "AND (h.record.year * 12 + h.record.month) BETWEEN (:fromYear * 12 + :fromMonth) AND (:toYear * 12 + :toMonth)")
    BigDecimal sumAmountForEmployeeAndHeadInRange(@Param("employeeId") Long employeeId, @Param("headCount") int headCount,
                                                   @Param("fromMonth") int fromMonth, @Param("fromYear") int fromYear,
                                                   @Param("toMonth") int toMonth, @Param("toYear") int toYear);
}
