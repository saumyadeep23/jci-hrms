package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.PayrollRunRequest;
import in.gov.jci.hrms.dto.PayrollRunResponse;
import in.gov.jci.hrms.dto.PayslipResponse;
import in.gov.jci.hrms.entity.ApprovalStatus;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EncashmentType;
import in.gov.jci.hrms.entity.HeadType;
import in.gov.jci.hrms.entity.LeaveEncashmentApplication;
import in.gov.jci.hrms.entity.PayrollRun;
import in.gov.jci.hrms.entity.PayrollRunStatus;
import in.gov.jci.hrms.entity.Payslip;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayrollRunServiceTest {

    @Mock
    private PayrollRunRepository payrollRunRepository;
    @Mock
    private PayslipRepository payslipRepository;
    @Mock
    private PayslipItemRepository payslipItemRepository;
    @Mock
    private SalaryHeadMasterRepository salaryHeadMasterRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private PayrollComputationService payrollComputationService;
    @Mock
    private LeaveEncashmentApplicationRepository encashmentRepository;
    @Mock
    private LeaveEncashmentService leaveEncashmentService;

    private PayrollRunService payrollRunService;

    @BeforeEach
    void setUp() {
        payrollRunService = new PayrollRunService(payrollRunRepository, payslipRepository, payslipItemRepository,
                salaryHeadMasterRepository, employeeRepository, payrollComputationService, encashmentRepository, leaveEncashmentService);

        lenient().when(encashmentRepository.findByFinanceApprovalStatusAndPayrollRunIsNull(ApprovalStatus.APPROVED)).thenReturn(List.of());
        lenient().when(encashmentRepository.findByArrearSettledFalseAndArrearPayrollRunIsNull()).thenReturn(List.of());
        lenient().when(encashmentRepository.findByPayrollRunId(any())).thenReturn(List.of());
        lenient().when(encashmentRepository.findByArrearPayrollRunId(any())).thenReturn(List.of());
    }

    private PayrollRun runFrom(Long id, PayrollRunStatus status) {
        PayrollRun run = new PayrollRun(2026, 8, LocalDate.of(2026, 7, 26), LocalDate.of(2026, 8, 25));
        ReflectionTestUtils.setField(run, "id", id);
        run.setStatus(status);
        return run;
    }

    private SalaryHeadMaster head(Long id, String code, HeadType type) {
        SalaryHeadMaster head = new SalaryHeadMaster(code, code, type, false, true, true);
        ReflectionTestUtils.setField(head, "id", id);
        return head;
    }

    private Employee employee(Long id) {
        Department department = new Department("ENG", "Engineering");
        Designation designation = new Designation("Manager");
        Employee employee = new Employee("EMP-00" + id, "First", "Last", "e" + id + "@example.com",
                LocalDate.of(2020, 1, 1), department, designation);
        ReflectionTestUtils.setField(employee, "id", id);
        return employee;
    }

    // ---- create ----

    @Test
    void create_derivesCycleDatesAndSavesInDraftStatus() {
        when(payrollComputationService.deriveCycleDates(2026, 8))
                .thenReturn(new PayrollComputationService.CycleDates(LocalDate.of(2026, 7, 26), LocalDate.of(2026, 8, 25)));
        when(payrollRunRepository.saveAndFlush(any(PayrollRun.class))).thenAnswer(inv -> {
            PayrollRun saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);
            return saved;
        });

        PayrollRunResponse response = payrollRunService.create(new PayrollRunRequest(2026, 8));

        assertThat(response.status()).isEqualTo(PayrollRunStatus.DRAFT);
        assertThat(response.startDate()).isEqualTo(LocalDate.of(2026, 7, 26));
        assertThat(response.endDate()).isEqualTo(LocalDate.of(2026, 8, 25));
    }

    @Test
    void create_whenCycleAlreadyExists_throwsMasterDataConflictException() {
        when(payrollComputationService.deriveCycleDates(2026, 8))
                .thenReturn(new PayrollComputationService.CycleDates(LocalDate.of(2026, 7, 26), LocalDate.of(2026, 8, 25)));
        when(payrollRunRepository.saveAndFlush(any(PayrollRun.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> payrollRunService.create(new PayrollRunRequest(2026, 8)))
                .isInstanceOf(MasterDataConflictException.class);
    }

    // ---- compute ----

    @Test
    void compute_whenDraft_createsPayslipAndItemsForEveryEmployee() {
        PayrollRun run = runFrom(1L, PayrollRunStatus.DRAFT);
        Employee emp1 = employee(10L);
        Employee emp2 = employee(20L);

        when(payrollRunRepository.findById(1L)).thenReturn(Optional.of(run));
        when(salaryHeadMasterRepository.findAll()).thenReturn(List.of(
                head(1L, "BASIC", HeadType.EARNING), head(2L, "DA", HeadType.EARNING),
                head(3L, "HRA", HeadType.EARNING), head(4L, "TA", HeadType.EARNING),
                head(5L, "EPF_EE", HeadType.DEDUCTION), head(6L, "EPF_ER", HeadType.EMPLOYER_CONTRIBUTION),
                head(7L, "EPS_ER", HeadType.EMPLOYER_CONTRIBUTION)));
        when(employeeRepository.findAll()).thenReturn(List.of(emp1, emp2));

        PayrollComputationService.PayrollComputationResult result = new PayrollComputationService.PayrollComputationResult(
                new BigDecimal("50000.00"), new BigDecimal("8500.00"), new BigDecimal("14040.00"), new BigDecimal("1872.00"),
                new BigDecimal("7020.00"), new BigDecimal("5770.50"), new BigDecimal("1249.50"),
                new BigDecimal("74412.00"), new BigDecimal("7020.00"), new BigDecimal("7020.00"),
                new BigDecimal("67392.00"), BigDecimal.ZERO, false);
        when(payrollComputationService.compute(any(Employee.class), any(PayrollRun.class))).thenReturn(result);
        when(payslipRepository.saveAndFlush(any(Payslip.class))).thenAnswer(inv -> inv.getArgument(0));

        PayrollRunResponse response = payrollRunService.compute(1L);

        assertThat(response.status()).isEqualTo(PayrollRunStatus.COMPUTED);
        org.mockito.Mockito.verify(payslipRepository, org.mockito.Mockito.times(2)).saveAndFlush(any(Payslip.class));
        org.mockito.Mockito.verify(payslipItemRepository, org.mockito.Mockito.times(14)).save(any());
    }

    @Test
    void compute_queuesApprovedEncashmentsAndPendingArrearsForTheirEmployees() {
        PayrollRun run = runFrom(1L, PayrollRunStatus.DRAFT);
        Employee emp1 = employee(10L);

        when(payrollRunRepository.findById(1L)).thenReturn(Optional.of(run));
        when(salaryHeadMasterRepository.findAll()).thenReturn(List.of(
                head(1L, "BASIC", HeadType.EARNING), head(2L, "DA", HeadType.EARNING),
                head(3L, "HRA", HeadType.EARNING), head(4L, "TA", HeadType.EARNING),
                head(5L, "EPF_EE", HeadType.DEDUCTION), head(6L, "EPF_ER", HeadType.EMPLOYER_CONTRIBUTION),
                head(7L, "EPS_ER", HeadType.EMPLOYER_CONTRIBUTION),
                head(8L, "EL_ENCASHMENT", HeadType.EARNING), head(9L, "EL_ENCASHMENT_ARREAR", HeadType.EARNING)));
        when(employeeRepository.findAll()).thenReturn(List.of(emp1));

        PayrollComputationService.PayrollComputationResult result = new PayrollComputationService.PayrollComputationResult(
                new BigDecimal("50000.00"), new BigDecimal("8500.00"), new BigDecimal("14040.00"), new BigDecimal("1872.00"),
                new BigDecimal("7020.00"), new BigDecimal("5770.50"), new BigDecimal("1249.50"),
                new BigDecimal("74412.00"), new BigDecimal("7020.00"), new BigDecimal("7020.00"),
                new BigDecimal("67392.00"), BigDecimal.ZERO, false);
        when(payrollComputationService.compute(any(Employee.class), any(PayrollRun.class))).thenReturn(result);
        when(payslipRepository.saveAndFlush(any(Payslip.class))).thenAnswer(inv -> inv.getArgument(0));

        LeaveEncashmentApplication encashment = new LeaveEncashmentApplication(emp1, EncashmentType.IN_SERVICE_EL,
                new BigDecimal("20.00"), BigDecimal.ZERO);
        encashment.setGrossAmount(new BigDecimal("64148.40"));
        LeaveEncashmentApplication arrear = new LeaveEncashmentApplication(emp1, EncashmentType.IN_SERVICE_EL,
                new BigDecimal("20.00"), BigDecimal.ZERO);
        arrear.setArrearAmount(new BigDecimal("1500.00"));
        when(encashmentRepository.findByFinanceApprovalStatusAndPayrollRunIsNull(ApprovalStatus.APPROVED)).thenReturn(List.of(encashment));
        when(encashmentRepository.findByArrearSettledFalseAndArrearPayrollRunIsNull()).thenReturn(List.of(arrear));

        payrollRunService.compute(1L);

        assertThat(encashment.getPayrollRun()).isSameAs(run);
        assertThat(arrear.getArrearPayrollRun()).isSameAs(run);
        verify(payslipItemRepository, org.mockito.Mockito.times(9)).save(any());
    }

    @Test
    void finalizeRun_marksQueuedEncashmentsProcessedAndClearsArrears() {
        PayrollRun run = runFrom(1L, PayrollRunStatus.COMPUTED);
        when(payrollRunRepository.findById(1L)).thenReturn(Optional.of(run));

        Employee emp1 = employee(10L);
        LeaveEncashmentApplication encashment = new LeaveEncashmentApplication(emp1, EncashmentType.IN_SERVICE_EL,
                new BigDecimal("20.00"), BigDecimal.ZERO);
        LeaveEncashmentApplication arrear = new LeaveEncashmentApplication(emp1, EncashmentType.IN_SERVICE_EL,
                new BigDecimal("20.00"), BigDecimal.ZERO);
        arrear.setArrearAmount(new BigDecimal("1500.00"));
        when(encashmentRepository.findByPayrollRunId(1L)).thenReturn(List.of(encashment));
        when(encashmentRepository.findByArrearPayrollRunId(1L)).thenReturn(List.of(arrear));

        payrollRunService.finalizeRun(1L, "finance.admin");

        assertThat(encashment.isPayrollProcessed()).isTrue();
        assertThat(arrear.isArrearSettled()).isTrue();
        verify(leaveEncashmentService).recordArrearClearance(arrear, 2026, 8);
    }

    @Test
    void compute_whenNotDraft_throwsBusinessRuleViolationException() {
        PayrollRun run = runFrom(1L, PayrollRunStatus.COMPUTED);
        when(payrollRunRepository.findById(1L)).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> payrollRunService.compute(1L))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void compute_whenRunMissing_throwsMasterDataNotFoundException() {
        when(payrollRunRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> payrollRunService.compute(99L))
                .isInstanceOf(MasterDataNotFoundException.class);
    }

    // ---- finalizeRun ----

    @Test
    void finalizeRun_whenComputed_setsFinalizedByAndAt() {
        PayrollRun run = runFrom(1L, PayrollRunStatus.COMPUTED);
        when(payrollRunRepository.findById(1L)).thenReturn(Optional.of(run));

        PayrollRunResponse response = payrollRunService.finalizeRun(1L, "hr.admin");

        assertThat(response.status()).isEqualTo(PayrollRunStatus.FINALIZED);
        assertThat(response.finalizedBy()).isEqualTo("hr.admin");
        assertThat(response.finalizedAt()).isNotNull();
    }

    @Test
    void finalizeRun_whenNotComputed_throwsBusinessRuleViolationException() {
        PayrollRun run = runFrom(1L, PayrollRunStatus.DRAFT);
        when(payrollRunRepository.findById(1L)).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> payrollRunService.finalizeRun(1L, "hr.admin"))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    // ---- listPayslips ----

    @Test
    void listPayslips_whenRunMissing_throwsMasterDataNotFoundException() {
        when(payrollRunRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> payrollRunService.listPayslips(99L))
                .isInstanceOf(MasterDataNotFoundException.class);
    }

    @Test
    void listPayslips_whenRunExists_returnsPayslipsWithEmptyItemsListWhenNoneStored() {
        PayrollRun run = runFrom(1L, PayrollRunStatus.COMPUTED);
        when(payrollRunRepository.findById(1L)).thenReturn(Optional.of(run));
        when(payslipRepository.findByPayrollRunId(1L)).thenReturn(List.of());

        List<PayslipResponse> responses = payrollRunService.listPayslips(1L);

        assertThat(responses).isEmpty();
    }
}
