package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.PayrollRunRequest;
import in.gov.jci.hrms.dto.PayrollRunResponse;
import in.gov.jci.hrms.dto.PayslipItemResponse;
import in.gov.jci.hrms.dto.PayslipResponse;
import in.gov.jci.hrms.entity.ApprovalStatus;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.LeaveEncashmentApplication;
import in.gov.jci.hrms.entity.PayrollRun;
import in.gov.jci.hrms.entity.PayrollRunStatus;
import in.gov.jci.hrms.entity.Payslip;
import in.gov.jci.hrms.entity.PayslipItem;
import in.gov.jci.hrms.entity.SalaryHeadMaster;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.LeaveEncashmentApplicationRepository;
import in.gov.jci.hrms.repository.PayrollRunRepository;
import in.gov.jci.hrms.repository.PayslipItemRepository;
import in.gov.jci.hrms.repository.PayslipRepository;
import in.gov.jci.hrms.repository.SalaryHeadMasterRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Draft -> Compute -> Finalize lifecycle for a payroll run. compute() is
 * one-shot (DRAFT only, no re-computation support) and processes every
 * non-deleted employee, including non-ACTIVE ones (whose payslip is marked
 * is_hold rather than being skipped - see PayrollComputationService).
 */
@Service
@Transactional(readOnly = true)
public class PayrollRunService {

    private static final String ENTITY_NAME = "Payroll Run";

    private final PayrollRunRepository payrollRunRepository;
    private final PayslipRepository payslipRepository;
    private final PayslipItemRepository payslipItemRepository;
    private final SalaryHeadMasterRepository salaryHeadMasterRepository;
    private final EmployeeRepository employeeRepository;
    private final PayrollComputationService payrollComputationService;
    private final LeaveEncashmentApplicationRepository encashmentRepository;
    private final LeaveEncashmentService leaveEncashmentService;

    public PayrollRunService(PayrollRunRepository payrollRunRepository, PayslipRepository payslipRepository,
                              PayslipItemRepository payslipItemRepository,
                              SalaryHeadMasterRepository salaryHeadMasterRepository,
                              EmployeeRepository employeeRepository,
                              PayrollComputationService payrollComputationService,
                              LeaveEncashmentApplicationRepository encashmentRepository,
                              LeaveEncashmentService leaveEncashmentService) {
        this.payrollRunRepository = payrollRunRepository;
        this.payslipRepository = payslipRepository;
        this.payslipItemRepository = payslipItemRepository;
        this.salaryHeadMasterRepository = salaryHeadMasterRepository;
        this.employeeRepository = employeeRepository;
        this.payrollComputationService = payrollComputationService;
        this.encashmentRepository = encashmentRepository;
        this.leaveEncashmentService = leaveEncashmentService;
    }

    @Transactional
    public PayrollRunResponse create(PayrollRunRequest request) {
        PayrollComputationService.CycleDates cycleDates =
                payrollComputationService.deriveCycleDates(request.cycleYear(), request.cycleMonth());
        PayrollRun run = new PayrollRun(request.cycleYear(), request.cycleMonth(),
                cycleDates.startDate(), cycleDates.endDate());
        return PayrollRunResponse.from(save(run));
    }

    public PayrollRunResponse getById(Long id) {
        return PayrollRunResponse.from(findOrThrow(id));
    }

    public Page<PayrollRunResponse> list(Pageable pageable) {
        return payrollRunRepository.findAll(pageable).map(PayrollRunResponse::from);
    }

    @Transactional
    public PayrollRunResponse compute(Long id) {
        PayrollRun run = findOrThrow(id);
        requireStatus(run, PayrollRunStatus.DRAFT);

        Map<String, SalaryHeadMaster> headsByCode = salaryHeadMasterRepository.findAll().stream()
                .collect(Collectors.toMap(SalaryHeadMaster::getCode, head -> head, (a, b) -> a));

        // Fetched once for the whole run, not per employee - grouped in memory below.
        Map<Long, List<LeaveEncashmentApplication>> encashmentsByEmployeeId =
                encashmentRepository.findByFinanceApprovalStatusAndPayrollRunIsNull(ApprovalStatus.APPROVED).stream()
                        .collect(Collectors.groupingBy(a -> a.getEmployee().getId()));
        Map<Long, List<LeaveEncashmentApplication>> arrearsByEmployeeId =
                encashmentRepository.findByArrearSettledFalseAndArrearPayrollRunIsNull().stream()
                        .collect(Collectors.groupingBy(a -> a.getEmployee().getId()));

        for (Employee employee : employeeRepository.findAll()) {
            PayrollComputationService.PayrollComputationResult result = payrollComputationService.compute(employee, run);

            Payslip payslip = new Payslip(run, employee, result.basicPay(), result.totalEarnings(),
                    result.totalDeductions(), result.employerContributions(), result.netPay(),
                    result.lopDays(), result.hold());
            payslipRepository.saveAndFlush(payslip);

            addItem(payslip, headsByCode, "BASIC", result.basicPay());
            addItem(payslip, headsByCode, "DA", result.dearnessAllowance());
            addItem(payslip, headsByCode, "HRA", result.houseRentAllowance());
            addItem(payslip, headsByCode, "TA", result.transportAllowance());
            addItem(payslip, headsByCode, "EPF_EE", result.employeeEpf());
            addItem(payslip, headsByCode, "EPF_ER", result.employerEpf());
            addItem(payslip, headsByCode, "EPS_ER", result.employerEps());

            for (LeaveEncashmentApplication encashment : encashmentsByEmployeeId.getOrDefault(employee.getId(), List.of())) {
                addItem(payslip, headsByCode, "EL_ENCASHMENT", encashment.getGrossAmount());
                encashment.setPayrollRun(run);
            }
            for (LeaveEncashmentApplication arrear : arrearsByEmployeeId.getOrDefault(employee.getId(), List.of())) {
                addItem(payslip, headsByCode, "EL_ENCASHMENT_ARREAR", arrear.getArrearAmount());
                arrear.setArrearPayrollRun(run);
            }
        }

        run.setStatus(PayrollRunStatus.COMPUTED);
        return PayrollRunResponse.from(run);
    }

    @Transactional
    public PayrollRunResponse finalizeRun(Long id, String finalizedBy) {
        PayrollRun run = findOrThrow(id);
        requireStatus(run, PayrollRunStatus.COMPUTED);

        for (LeaveEncashmentApplication encashment : encashmentRepository.findByPayrollRunId(run.getId())) {
            encashment.setPayrollProcessed(true);
        }
        for (LeaveEncashmentApplication arrear : encashmentRepository.findByArrearPayrollRunId(run.getId())) {
            arrear.setArrearSettled(true);
            leaveEncashmentService.recordArrearClearance(arrear, run.getCycleYear(), run.getCycleMonth());
        }

        run.setStatus(PayrollRunStatus.FINALIZED);
        run.setFinalizedBy(finalizedBy);
        run.setFinalizedAt(Instant.now());
        return PayrollRunResponse.from(run);
    }

    public List<PayslipResponse> listPayslips(Long payrollRunId) {
        findOrThrow(payrollRunId);
        return payslipRepository.findByPayrollRunId(payrollRunId).stream()
                .map(payslip -> PayslipResponse.from(payslip, itemResponses(payslip.getId())))
                .toList();
    }

    private List<PayslipItemResponse> itemResponses(Long payslipId) {
        return payslipItemRepository.findByPayslipId(payslipId).stream()
                .map(PayslipItemResponse::from)
                .toList();
    }

    private void addItem(Payslip payslip, Map<String, SalaryHeadMaster> headsByCode, String code, java.math.BigDecimal amount) {
        SalaryHeadMaster head = headsByCode.get(code);
        if (head == null) {
            throw new BusinessRuleViolationException(
                    "Required salary head '" + code + "' not found - has the seed data been removed?");
        }
        payslipItemRepository.save(new PayslipItem(payslip, head, amount));
    }

    private void requireStatus(PayrollRun run, PayrollRunStatus expected) {
        if (run.getStatus() != expected) {
            throw new BusinessRuleViolationException(
                    ENTITY_NAME + " " + run.getId() + " must be " + expected + " but is " + run.getStatus());
        }
    }

    private PayrollRun findOrThrow(Long id) {
        return payrollRunRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException(ENTITY_NAME, id));
    }

    private PayrollRun save(PayrollRun run) {
        try {
            return payrollRunRepository.saveAndFlush(run);
        } catch (DataIntegrityViolationException ex) {
            throw new MasterDataConflictException(
                    ENTITY_NAME + " already exists for " + run.getCycleYear() + "-" + run.getCycleMonth());
        }
    }
}
