package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.EssSalarySlipDetailResponse;
import in.gov.jci.hrms.dto.EssSalarySlipSummaryResponse;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.PayrollBatchStatus;
import in.gov.jci.hrms.entity.PayrollMonthlyRecord;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.PayrollMonthlyRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Employee self-service payroll history - only PayrollMonthlyRecords belonging to a DISBURSED batch
 * are ever visible here (a DRAFT/CALCULATED/HR_FINALIZED figure isn't final yet and shouldn't be
 * shown to the employee as their pay).
 */
@Service
@Transactional(readOnly = true)
public class EssPayrollService {

    private final PayrollMonthlyRecordRepository recordRepository;
    private final PayrollQueryService payrollQueryService;

    public EssPayrollService(PayrollMonthlyRecordRepository recordRepository, PayrollQueryService payrollQueryService) {
        this.recordRepository = recordRepository;
        this.payrollQueryService = payrollQueryService;
    }

    public List<EssSalarySlipSummaryResponse> list(Long employeeId) {
        return recordRepository.findByEmployee_IdAndBatch_StatusOrderByYearDescMonthDesc(employeeId, PayrollBatchStatus.DISBURSED).stream()
                .map(record -> new EssSalarySlipSummaryResponse(record.getTranId(), record.getMonth(), record.getYear(),
                        record.getBatch().getBatchNo(), record.getGrossAmount(), record.getTotalDeductions(),
                        record.getNetAmount(), record.isSalaryHeld()))
                .toList();
    }

    public EssSalarySlipDetailResponse getDetail(Long employeeId, Long tranId) {
        PayrollMonthlyRecord record = loadOwnedDisbursedRecord(employeeId, tranId);
        Employee employee = record.getEmployee();
        return new EssSalarySlipDetailResponse(record.getTranId(), record.getEmpCode(), employee.getFullName(),
                employee.getDesignation() != null ? employee.getDesignation().getTitle() : null,
                record.getMonth(), record.getYear(), record.getGrossAmount(), record.getTotalDeductions(),
                record.getNetAmount(), payrollQueryService.fullHeadLines(record.getTranId()));
    }

    PayrollMonthlyRecord loadOwnedDisbursedRecord(Long employeeId, Long tranId) {
        PayrollMonthlyRecord record = recordRepository.findById(tranId)
                .orElseThrow(() -> new MasterDataNotFoundException("Salary Slip", tranId));
        if (!record.getEmployee().getId().equals(employeeId)) {
            throw new BusinessRuleViolationException("Salary slip " + tranId + " does not belong to this employee");
        }
        if (record.getBatch().getStatus() != PayrollBatchStatus.DISBURSED) {
            throw new BusinessRuleViolationException("Salary slip " + tranId + " is not yet disbursed");
        }
        return record;
    }
}
