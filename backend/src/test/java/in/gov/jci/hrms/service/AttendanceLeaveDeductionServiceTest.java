package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.DailyAttendance;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.LeaveBalance;
import in.gov.jci.hrms.entity.LeaveLedgerEntry;
import in.gov.jci.hrms.entity.LeaveLedgerSource;
import in.gov.jci.hrms.entity.LeaveType;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.repository.LeaveBalanceRepository;
import in.gov.jci.hrms.repository.LeaveLedgerEntryRepository;
import in.gov.jci.hrms.repository.LeaveTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceLeaveDeductionServiceTest {

    private static final Long EMPLOYEE_ID = 1L;
    private static final Long DAILY_ATTENDANCE_ID = 500L;

    @Mock
    private LeaveBalanceRepository leaveBalanceRepository;
    @Mock
    private LeaveTypeRepository leaveTypeRepository;
    @Mock
    private LeaveLedgerEntryRepository leaveLedgerEntryRepository;

    private AttendanceLeaveDeductionService service;
    private Employee employee;
    private LeaveType cl;
    private LeaveType el;
    private LeaveType lwp;
    private DailyAttendance dailyAttendance;

    @BeforeEach
    void setUp() {
        service = new AttendanceLeaveDeductionService(leaveBalanceRepository, leaveTypeRepository, leaveLedgerEntryRepository);

        Department department = new Department("ENG", "Engineering");
        Designation designation = new Designation("Field Officer");
        employee = new Employee("EMP-001", "Asha", "Rao", "asha.rao@example.com",
                LocalDate.of(2024, 1, 15), department, designation);
        ReflectionTestUtils.setField(employee, "id", EMPLOYEE_ID);

        cl = leaveType("CL", 10L);
        el = leaveType("EL", 11L);
        lwp = leaveType("LWP", 12L);

        dailyAttendance = new DailyAttendance(employee, LocalDate.of(2026, 8, 20), in.gov.jci.hrms.entity.AttendanceStatus.PRESENT);
        ReflectionTestUtils.setField(dailyAttendance, "id", DAILY_ATTENDANCE_ID);
    }

    private LeaveType leaveType(String code, Long id) {
        LeaveType type = new LeaveType(code, code + " Leave", BigDecimal.TEN, false, true);
        ReflectionTestUtils.setField(type, "id", id);
        return type;
    }

    private LeaveBalance balanceWith(LeaveType type, BigDecimal creditedDays, BigDecimal usedDays) {
        LeaveBalance balance = new LeaveBalance(employee, type, 2026, creditedDays);
        balance.setUsedDays(usedDays);
        return balance;
    }

    @Test
    void debit_alreadyLedgedForThisDay_isNoOp() {
        when(leaveLedgerEntryRepository.existsByRelatedDailyAttendanceId(DAILY_ATTENDANCE_ID)).thenReturn(true);

        service.debitForUnauthorizedAttendance(employee, dailyAttendance);

        verify(leaveLedgerEntryRepository, never()).saveAndFlush(any());
    }

    @Test
    void debit_withClAvailable_debitsClAndWritesLedgerEntry() {
        when(leaveLedgerEntryRepository.existsByRelatedDailyAttendanceId(DAILY_ATTENDANCE_ID)).thenReturn(false);
        when(leaveTypeRepository.findByCode("CL")).thenReturn(Optional.of(cl));
        LeaveBalance clBalance = balanceWith(cl, BigDecimal.valueOf(8), BigDecimal.ZERO);
        when(leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(EMPLOYEE_ID, 10L, 2026))
                .thenReturn(Optional.of(clBalance));

        ArgumentCaptor<LeaveLedgerEntry> captor = ArgumentCaptor.forClass(LeaveLedgerEntry.class);
        service.debitForUnauthorizedAttendance(employee, dailyAttendance);

        assertThat(clBalance.getUsedDays()).isEqualByComparingTo("0.5");
        verify(leaveLedgerEntryRepository).saveAndFlush(captor.capture());
        LeaveLedgerEntry entry = captor.getValue();
        assertThat(entry.getLeaveType()).isEqualTo(cl);
        assertThat(entry.getDeltaDays()).isEqualByComparingTo("-0.5");
        assertThat(entry.getDescription()).isEqualTo("Auto-debited 0.5 CL for unauthorized late attendance / short hours on 20-08-2026");
        assertThat(entry.getSource()).isEqualTo(LeaveLedgerSource.AUTO_LATE_DEDUCTION);
        assertThat(entry.getRelatedDailyAttendance()).isEqualTo(dailyAttendance);
    }

    @Test
    void debit_withClExhausted_fallsBackToEl() {
        when(leaveLedgerEntryRepository.existsByRelatedDailyAttendanceId(DAILY_ATTENDANCE_ID)).thenReturn(false);
        when(leaveTypeRepository.findByCode("CL")).thenReturn(Optional.of(cl));
        when(leaveTypeRepository.findByCode("EL")).thenReturn(Optional.of(el));
        LeaveBalance clBalance = balanceWith(cl, BigDecimal.valueOf(8), BigDecimal.valueOf(8)); // 0 available
        LeaveBalance elBalance = balanceWith(el, BigDecimal.valueOf(30), BigDecimal.ZERO);
        when(leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(EMPLOYEE_ID, 10L, 2026))
                .thenReturn(Optional.of(clBalance));
        when(leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(EMPLOYEE_ID, 11L, 2026))
                .thenReturn(Optional.of(elBalance));

        ArgumentCaptor<LeaveLedgerEntry> captor = ArgumentCaptor.forClass(LeaveLedgerEntry.class);
        service.debitForUnauthorizedAttendance(employee, dailyAttendance);

        assertThat(clBalance.getUsedDays()).isEqualByComparingTo("8");
        assertThat(elBalance.getUsedDays()).isEqualByComparingTo("0.5");
        verify(leaveLedgerEntryRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getLeaveType()).isEqualTo(el);
        assertThat(captor.getValue().getDescription()).contains("Auto-debited 0.5 EL").contains("CL balance exhausted");
    }

    @Test
    void debit_withClAndElExhausted_marksLwpWithoutTouchingAnyBalance() {
        when(leaveLedgerEntryRepository.existsByRelatedDailyAttendanceId(DAILY_ATTENDANCE_ID)).thenReturn(false);
        when(leaveTypeRepository.findByCode("CL")).thenReturn(Optional.of(cl));
        when(leaveTypeRepository.findByCode("EL")).thenReturn(Optional.of(el));
        when(leaveTypeRepository.findByCode("LWP")).thenReturn(Optional.of(lwp));
        LeaveBalance clBalance = balanceWith(cl, BigDecimal.valueOf(8), BigDecimal.valueOf(8));
        LeaveBalance elBalance = balanceWith(el, BigDecimal.valueOf(30), BigDecimal.valueOf(30));
        when(leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(EMPLOYEE_ID, 10L, 2026))
                .thenReturn(Optional.of(clBalance));
        when(leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(EMPLOYEE_ID, 11L, 2026))
                .thenReturn(Optional.of(elBalance));

        ArgumentCaptor<LeaveLedgerEntry> captor = ArgumentCaptor.forClass(LeaveLedgerEntry.class);
        service.debitForUnauthorizedAttendance(employee, dailyAttendance);

        verify(leaveLedgerEntryRepository).saveAndFlush(captor.capture());
        LeaveLedgerEntry entry = captor.getValue();
        assertThat(entry.getLeaveType()).isEqualTo(lwp);
        assertThat(entry.getDescription()).contains("LEAVE_WITHOUT_PAY (LWP)/DIES-NON").contains("20-08-2026");
        // Neither balance was touched - LWP has no balance concept of its own.
        assertThat(clBalance.getUsedDays()).isEqualByComparingTo("8");
        assertThat(elBalance.getUsedDays()).isEqualByComparingTo("30");
    }

    @Test
    void debit_withNoClBalanceProvisionedAtAll_treatedAsZeroAvailable_fallsBackToEl() {
        when(leaveLedgerEntryRepository.existsByRelatedDailyAttendanceId(DAILY_ATTENDANCE_ID)).thenReturn(false);
        when(leaveTypeRepository.findByCode("CL")).thenReturn(Optional.of(cl));
        when(leaveTypeRepository.findByCode("EL")).thenReturn(Optional.of(el));
        when(leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(EMPLOYEE_ID, 10L, 2026))
                .thenReturn(Optional.empty());
        LeaveBalance elBalance = balanceWith(el, BigDecimal.valueOf(30), BigDecimal.ZERO);
        when(leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(EMPLOYEE_ID, 11L, 2026))
                .thenReturn(Optional.of(elBalance));

        service.debitForUnauthorizedAttendance(employee, dailyAttendance);

        assertThat(elBalance.getUsedDays()).isEqualByComparingTo("0.5");
    }

    @Test
    void debit_whenClLeaveTypeNotConfigured_throwsBusinessRuleViolationException() {
        when(leaveLedgerEntryRepository.existsByRelatedDailyAttendanceId(DAILY_ATTENDANCE_ID)).thenReturn(false);
        when(leaveTypeRepository.findByCode("CL")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.debitForUnauthorizedAttendance(employee, dailyAttendance))
                .isInstanceOf(BusinessRuleViolationException.class);
    }
}
