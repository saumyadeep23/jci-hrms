package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.DeputationInitiateRequest;
import in.gov.jci.hrms.dto.DeputationRepatriationRequest;
import in.gov.jci.hrms.entity.DeputationStatus;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeDeputationRecord;
import in.gov.jci.hrms.entity.LspcBorneBy;
import in.gov.jci.hrms.entity.PayOption;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.EmployeeDeputationRecordRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Deputation lifecycle: initiateDeputation applies the DPE 3rd PRC deputation (duty) allowance rate/cap
 * (5% up to Rs. 4,500 for same-station, 10% up to Rs. 9,000 for different-station), then
 * processRepatriation closes the spell out. PayrollBatchComputationService's own deputation hook is the
 * actual payroll-side consumer (Head 67) - see its own javadoc.
 */
@Service
@Transactional(readOnly = true)
public class DeputationLifecycleService {

    private static final BigDecimal SAME_STATION_RATE = new BigDecimal("5.00");
    private static final BigDecimal SAME_STATION_CAP = new BigDecimal("4500.00");
    private static final BigDecimal DIFFERENT_STATION_RATE = new BigDecimal("10.00");
    private static final BigDecimal DIFFERENT_STATION_CAP = new BigDecimal("9000.00");

    private final EmployeeDeputationRecordRepository deputationRepository;
    private final EmployeeRepository employeeRepository;

    public DeputationLifecycleService(EmployeeDeputationRecordRepository deputationRepository, EmployeeRepository employeeRepository) {
        this.deputationRepository = deputationRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional
    public EmployeeDeputationRecord initiateDeputation(DeputationInitiateRequest request) {
        Employee employee = resolveEmployee(request.employeeId());
        if (deputationRepository.findByEmployee_IdAndStatus(employee.getId(), DeputationStatus.ACTIVE).isPresent()) {
            throw new BusinessRuleViolationException("Employee " + employee.getId() + " already has an active deputation");
        }
        if (request.periodTo().isBefore(request.periodFrom())) {
            throw new BusinessRuleViolationException("periodTo must not be before periodFrom");
        }

        EmployeeDeputationRecord record = new EmployeeDeputationRecord(employee, request.deputationDirection(),
                request.organizationName(), request.organizationType(), request.postingStation(), request.isSameStation(),
                request.periodFrom(), request.periodTo(), request.payOption(), null);

        if (request.payOption() == PayOption.PARENT_CADRE_BASIC_PLUS_DEP_ALLOWANCE) {
            if (request.isSameStation()) {
                record.setDeputationAllowanceRate(SAME_STATION_RATE);
                record.setDeputationAllowanceCap(SAME_STATION_CAP);
            } else {
                record.setDeputationAllowanceRate(DIFFERENT_STATION_RATE);
                record.setDeputationAllowanceCap(DIFFERENT_STATION_CAP);
            }
        }
        // FOREIGN_POST_PAY_SCALE: no deputation allowance through JCI's own payroll - rate/cap stay 0.00 (the entity's own default).

        record.setLspcApplicable(request.lspcApplicable());
        if (request.lspcBorneBy() != null) {
            record.setLspcBorneBy(request.lspcBorneBy());
        } else if (!request.lspcApplicable()) {
            record.setLspcBorneBy(LspcBorneBy.BORROWING_ORG);
        }
        if (request.lspcMonthlyRate() != null) {
            record.setLspcMonthlyRate(request.lspcMonthlyRate());
        }

        return deputationRepository.save(record);
    }

    @Transactional
    public EmployeeDeputationRecord processRepatriation(Long deputationId, DeputationRepatriationRequest request) {
        EmployeeDeputationRecord record = findDeputationOrThrow(deputationId);
        if (record.getStatus() != DeputationStatus.ACTIVE) {
            throw new BusinessRuleViolationException("Deputation " + deputationId + " is not currently active (is " + record.getStatus() + ")");
        }
        record.setStatus(DeputationStatus.REPATRIATED);
        record.setRepatriationOrderNo(request.repatriationOrderNo());
        record.setRepatriationDate(request.repatriationDate());
        if (request.remarks() != null) {
            record.setRemarks(request.remarks());
        }
        return deputationRepository.save(record);
    }

    private EmployeeDeputationRecord findDeputationOrThrow(Long id) {
        return deputationRepository.findById(id).orElseThrow(() -> new MasterDataNotFoundException("Deputation Record", id));
    }

    private Employee resolveEmployee(Long id) {
        return employeeRepository.findById(id).orElseThrow(() -> new EmployeeNotFoundException(id));
    }
}
