package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.CityClass;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeMovementRecord;
import in.gov.jci.hrms.entity.MovementOrder;
import in.gov.jci.hrms.entity.MovementOrderType;
import in.gov.jci.hrms.entity.PayrollMovementInput;
import in.gov.jci.hrms.entity.RegionalOffice;
import in.gov.jci.hrms.entity.SessionType;
import in.gov.jci.hrms.repository.PayrollMovementInputRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayrollMovementIntegrationServiceTest {

    @Mock
    private PayrollMovementInputRepository payrollMovementInputRepository;

    private PayrollMovementIntegrationService service;
    private Employee employee;
    private RegionalOffice fromOffice;
    private RegionalOffice toOffice;
    private MovementOrder order;

    @BeforeEach
    void setUp() {
        service = new PayrollMovementIntegrationService(payrollMovementInputRepository);

        Department department = new Department("ENG", "Engineering");
        Designation designation = new Designation("Manager");
        employee = new Employee("EMP-001", "Asha", "Rao", "asha.rao@example.com", LocalDate.of(2015, 1, 1), department, designation);
        ReflectionTestUtils.setField(employee, "id", 1L);

        fromOffice = new RegionalOffice("RO-A", "Kolkata RO", "West Bengal", CityClass.Y, true);
        ReflectionTestUtils.setField(fromOffice, "id", 10L);
        toOffice = new RegionalOffice("RO-B", "Mumbai RO", "Maharashtra", CityClass.X, true);
        ReflectionTestUtils.setField(toOffice, "id", 20L);

        order = new MovementOrder(MovementOrderType.TRANSFER, "JCI/Transfer/2026/01", LocalDate.of(2026, 3, 1));
        ReflectionTestUtils.setField(order, "id", 100L);

        lenient().when(payrollMovementInputRepository.findByMovementIdAndPayMonthAndPayYear(any(), anyInt(), anyInt()))
                .thenReturn(Optional.empty());
        lenient().when(payrollMovementInputRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private EmployeeMovementRecord movementRecord(LocalDate releaseDate, SessionType releaseSession,
                                                    LocalDate joiningDate, SessionType joiningSession) {
        EmployeeMovementRecord record = new EmployeeMovementRecord(order, employee, fromOffice, employee.getDesignation(),
                toOffice, employee.getDesignation());
        ReflectionTestUtils.setField(record, "id", 500L);
        record.setReleaseDate(releaseDate);
        record.setReleaseSession(releaseSession);
        record.setJoiningDate(joiningDate);
        record.setJoiningSession(joiningSession);
        return record;
    }

    @Test
    void generateInputs_sameMonth_afternoonReleaseForenoonJoin_splitsDaysCorrectly() {
        // Released 14th AFTERNOON (old office pays through the 14th), joined 20th FORENOON
        // (new office pays from the 20th) - both in March (31 days).
        EmployeeMovementRecord record = movementRecord(
                LocalDate.of(2026, 3, 14), SessionType.AFTERNOON, LocalDate.of(2026, 3, 20), SessionType.FORENOON);
        record.setAdmissibleJtDays(10);
        record.setJoiningTimeAvailedDays(6);
        record.setExcessTransitLwpDays(0);

        List<PayrollMovementInput> inputs = service.generateInputs(record);

        assertThat(inputs).hasSize(1);
        PayrollMovementInput input = inputs.get(0);
        assertThat(input.getPayMonth()).isEqualTo(3);
        assertThat(input.getPayYear()).isEqualTo(2026);
        assertThat(input.getReleasingOfficeDays()).isEqualTo(14);
        assertThat(input.getReceivingOfficeDays()).isEqualTo(12); // 31 - 20 + 1
        assertThat(input.getRevisedHraTier()).isEqualTo(CityClass.X);
        assertThat(input.getTransitJtDays()).isEqualTo(6);
        assertThat(input.getTransitLwpDays()).isZero();
    }

    @Test
    void generateInputs_forenoonRelease_lastPayableDayIsThePriorDay() {
        EmployeeMovementRecord record = movementRecord(
                LocalDate.of(2026, 3, 14), SessionType.FORENOON, LocalDate.of(2026, 3, 20), SessionType.AFTERNOON);

        PayrollMovementInput input = service.generateInputs(record).get(0);

        assertThat(input.getReleasingOfficeDays()).isEqualTo(13);
        assertThat(input.getReceivingOfficeDays()).isEqualTo(11); // AFTERNOON join -> pays from the 21st: 31 - 21 + 1
    }

    @Test
    void generateInputs_crossingMonthBoundary_writesTwoRows() {
        EmployeeMovementRecord record = movementRecord(
                LocalDate.of(2026, 3, 30), SessionType.AFTERNOON, LocalDate.of(2026, 4, 3), SessionType.FORENOON);
        record.setAdmissibleJtDays(10);
        record.setJoiningTimeAvailedDays(4);
        record.setExcessTransitLwpDays(0);

        List<PayrollMovementInput> inputs = service.generateInputs(record);

        assertThat(inputs).hasSize(2);
        PayrollMovementInput releaseRow = inputs.stream().filter(i -> i.getPayMonth() == 3).findFirst().orElseThrow();
        PayrollMovementInput joinRow = inputs.stream().filter(i -> i.getPayMonth() == 4).findFirst().orElseThrow();

        assertThat(releaseRow.getReleasingOfficeDays()).isEqualTo(30);
        assertThat(releaseRow.getReceivingOfficeDays()).isZero();
        assertThat(joinRow.getReceivingOfficeDays()).isEqualTo(28); // April has 30 days: 30 - 3 + 1
        assertThat(joinRow.getReleasingOfficeDays()).isZero();
    }

    @Test
    void generateInputs_promotionalBasicPay_carriedThroughAsRevisedBasicPay() {
        EmployeeMovementRecord record = movementRecord(
                LocalDate.of(2026, 3, 14), SessionType.AFTERNOON, LocalDate.of(2026, 3, 20), SessionType.FORENOON);
        record.setPromotionalBasicPay(new BigDecimal("65000.00"));

        PayrollMovementInput input = service.generateInputs(record).get(0);

        assertThat(input.getRevisedBasicPay()).isEqualByComparingTo("65000.00");
    }
}
