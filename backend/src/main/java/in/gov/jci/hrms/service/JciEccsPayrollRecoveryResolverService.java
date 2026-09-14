package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.JciEccsCollectionDetail;
import in.gov.jci.hrms.entity.PayrollBatch;
import in.gov.jci.hrms.repository.JciEccsCollectionDetailRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Resolves this employee's JCIECCS deduction amounts for a payroll batch, read-only, by looking up the
 * already-LOCKED {@code jcieccs_collection_detail} row for this (payrollRunId, employeeId) - never
 * recomputing loan schedules itself (spec section 1.4: Payroll imports the snapshot as read-only
 * deductions, administrators cannot edit these values). payrollRunId is PayrollBatch.id.toString() - the
 * same opaque string key JCIECCS's own snapshot endpoint is keyed on.
 * <p>
 * If Payroll runs "Calculate" before requesting a JCIECCS snapshot for this batch (spec section 1.2 says
 * the snapshot should be requested "during Salary Run Batch Creation", but nothing enforces that
 * ordering), no collection_detail rows exist yet for this payrollRunId and every employee's JCIECCS
 * deduction resolves to {@link RecoveryAmounts#ZERO} - a safe default (no phantom deductions), not an
 * error; a subsequent recalculation after the snapshot is requested will pick the real amounts up.
 */
@Service
@Transactional(readOnly = true)
public class JciEccsPayrollRecoveryResolverService {

    private final JciEccsCollectionDetailRepository collectionDetailRepository;

    public JciEccsPayrollRecoveryResolverService(JciEccsCollectionDetailRepository collectionDetailRepository) {
        this.collectionDetailRepository = collectionDetailRepository;
    }

    public RecoveryAmounts resolve(Employee employee, PayrollBatch batch) {
        String payrollRunId = batch.getId().toString();
        return collectionDetailRepository.findByBatch_PayrollRunIdAndEmployeeId(payrollRunId, employee.getId())
                .map(RecoveryAmounts::from)
                .orElse(RecoveryAmounts.ZERO);
    }

    public record RecoveryAmounts(BigDecimal thrift, BigDecimal termPrincipal, BigDecimal termInterest,
                                   BigDecimal emergencyPrincipal, BigDecimal emergencyInterest) {
        public static final RecoveryAmounts ZERO =
                new RecoveryAmounts(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        static RecoveryAmounts from(JciEccsCollectionDetail detail) {
            return new RecoveryAmounts(detail.getThriftAmount(), BigDecimal.valueOf(detail.getTermPrincipal()), detail.getTermInterest(),
                    BigDecimal.valueOf(detail.getEmergencyPrincipal()), detail.getEmergencyInterest());
        }
    }
}
