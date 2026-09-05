package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.CombinedLeaveApplicationRequest;
import in.gov.jci.hrms.dto.CombinedLeaveApplicationResponse;
import in.gov.jci.hrms.dto.LeaveApplicationResponse;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.Holiday;
import in.gov.jci.hrms.entity.HolidayType;
import in.gov.jci.hrms.entity.LeaveApplication;
import in.gov.jci.hrms.entity.LeaveApplicationStatus;
import in.gov.jci.hrms.entity.LeaveSession;
import in.gov.jci.hrms.entity.LeaveType;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.IncompatibleLeaveCombinationException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.HolidayRepository;
import in.gov.jci.hrms.repository.LeaveApplicationRepository;
import in.gov.jci.hrms.repository.LeaveTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Combined CL+RH applications (PIMS ALMS Phase 2, Section 3): a same-day
 * split (First Half CL + Second Half RH, or vice versa) or a contiguous
 * multi-day prefix/suffix (RH immediately before/after a CL span). Creates
 * two DRAFT LeaveApplication rows sharing one group_application_id - each
 * leg's own totalDays is computed independently (CL via
 * LeaveValidationService, which already excludes intervening
 * Saturdays/Sundays/Gazetted holidays from CL's own debit), so no separate
 * "sandwich rule" logic is needed: a weekend/holiday between the two legs
 * (there normally isn't one, since "contiguous" means no gap) was never
 * going to be debited by either leg's own figure.
 *
 * Submit/approve/reject/cancel of each leg still goes through
 * LeaveApplicationController/LeaveApplicationService individually - this
 * service only creates the linked DRAFT pair; deeper lockstep-lifecycle
 * changes (e.g. approving one leg auto-approving the other) are out of
 * scope for this delivery.
 */
@Service
@Transactional(readOnly = true)
public class CombinedLeaveApplicationService {

    private static final Set<String> INCOMPATIBLE_WITH_CL = Set.of("EL", "HPL", "CCL");
    private static final BigDecimal HALF_DAY = new BigDecimal("0.5");
    private static final BigDecimal ONE_DAY = BigDecimal.ONE;

    private final EmployeeRepository employeeRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final HolidayRepository holidayRepository;
    private final LeaveApplicationRepository leaveApplicationRepository;
    private final LeaveValidationService leaveValidationService;

    public CombinedLeaveApplicationService(EmployeeRepository employeeRepository, LeaveTypeRepository leaveTypeRepository,
                                            HolidayRepository holidayRepository, LeaveApplicationRepository leaveApplicationRepository,
                                            LeaveValidationService leaveValidationService) {
        this.employeeRepository = employeeRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.holidayRepository = holidayRepository;
        this.leaveApplicationRepository = leaveApplicationRepository;
        this.leaveValidationService = leaveValidationService;
    }

    @Transactional
    public CombinedLeaveApplicationResponse create(CombinedLeaveApplicationRequest request) {
        Employee employee = employeeRepository.findById(request.employeeId())
                .orElseThrow(() -> new EmployeeNotFoundException(request.employeeId()));
        LeaveType clType = resolveLeaveType(request.clLeaveTypeId());
        LeaveType rhType = resolveLeaveType(request.rhLeaveTypeId());
        if (!"CL".equals(clType.getCode())) {
            throw new BusinessRuleViolationException("clLeaveTypeId must resolve to the CL leave type");
        }
        if (!"RH".equals(rhType.getCode())) {
            throw new BusinessRuleViolationException("rhLeaveTypeId must resolve to the RH leave type");
        }
        Holiday rhHoliday = holidayRepository.findById(request.rhHolidayId())
                .orElseThrow(() -> new MasterDataNotFoundException("Holiday", request.rhHolidayId()));
        if (rhHoliday.getHolidayType() != HolidayType.RESTRICTED) {
            throw new BusinessRuleViolationException("rhHolidayId must reference a RESTRICTED holiday");
        }

        LeaveSession clSession = request.clSession() != null ? request.clSession() : LeaveSession.FULL_DAY;
        LeaveSession rhSession = request.rhSession() != null ? request.rhSession() : LeaveSession.FULL_DAY;
        validateCombinationShape(request, clSession, rhSession);
        rejectIfClAdjacentToStatutoryLeave(employee, request.clStartDate(), request.clEndDate());

        BigDecimal clDebitableDays = leaveValidationService.validateAndComputeDebitableDays(
                employee, clType, request.clStartDate(), request.clEndDate(), clSession);
        BigDecimal rhDebitableDays = rhSession == LeaveSession.FULL_DAY ? ONE_DAY : HALF_DAY;

        UUID groupId = UUID.randomUUID();

        LeaveApplication clApplication = new LeaveApplication(employee, clType, request.clStartDate(), request.clEndDate(),
                clDebitableDays, request.reason(), clSession);
        clApplication.setGroupApplicationId(groupId);

        LeaveApplication rhApplication = new LeaveApplication(employee, rhType, request.rhDate(), request.rhDate(),
                rhDebitableDays, request.reason(), rhSession);
        rhApplication.setGroupApplicationId(groupId);
        rhApplication.setRhEntry(rhHoliday);

        LeaveApplication savedCl = leaveApplicationRepository.saveAndFlush(clApplication);
        LeaveApplication savedRh = leaveApplicationRepository.saveAndFlush(rhApplication);

        return new CombinedLeaveApplicationResponse(groupId, LeaveApplicationResponse.from(savedCl), LeaveApplicationResponse.from(savedRh));
    }

    /**
     * Either a same-day split (clStartDate == clEndDate == rhDate, with CL
     * and RH on strictly opposite halves) or a contiguous multi-day span (RH
     * is exactly one full day immediately before clStartDate or immediately
     * after clEndDate, and both legs are FULL_DAY).
     */
    private void validateCombinationShape(CombinedLeaveApplicationRequest request, LeaveSession clSession, LeaveSession rhSession) {
        boolean sameDaySplit = request.clStartDate().equals(request.clEndDate()) && request.clStartDate().equals(request.rhDate());
        if (sameDaySplit) {
            boolean oppositeHalves = (clSession == LeaveSession.FIRST_HALF && rhSession == LeaveSession.SECOND_HALF)
                    || (clSession == LeaveSession.SECOND_HALF && rhSession == LeaveSession.FIRST_HALF);
            if (!oppositeHalves) {
                throw new BusinessRuleViolationException(
                        "A same-day CL+RH combination requires opposite half-day sessions (First Half + Second Half)");
            }
            return;
        }

        boolean rhIsPrefix = request.rhDate().equals(request.clStartDate().minusDays(1));
        boolean rhIsSuffix = request.rhDate().equals(request.clEndDate().plusDays(1));
        if (!rhIsPrefix && !rhIsSuffix) {
            throw new BusinessRuleViolationException(
                    "RH must be immediately contiguous with the CL span (prefix or suffix), or fall on the same day as a half-day CL split");
        }
        if (clSession != LeaveSession.FULL_DAY || rhSession != LeaveSession.FULL_DAY) {
            throw new BusinessRuleViolationException("A contiguous multi-day CL+RH combination must use FULL_DAY sessions on both legs");
        }
    }

    /**
     * Strict CL vs EL/HPL/CCL rejection for this endpoint specifically (422,
     * via IncompatibleLeaveCombinationException) - independent of
     * LeaveValidationService's own contiguous-CL check (which stays 400 for
     * every ordinary single-application call site; not modified here).
     */
    private void rejectIfClAdjacentToStatutoryLeave(Employee employee, java.time.LocalDate startDate, java.time.LocalDate endDate) {
        List<LeaveApplication> adjacent = new java.util.ArrayList<>();
        adjacent.addAll(leaveApplicationRepository.findByEmployeeIdAndStatusAndEndDate(
                employee.getId(), LeaveApplicationStatus.APPROVED, startDate.minusDays(1)));
        adjacent.addAll(leaveApplicationRepository.findByEmployeeIdAndStatusAndStartDate(
                employee.getId(), LeaveApplicationStatus.APPROVED, endDate.plusDays(1)));

        for (LeaveApplication existing : adjacent) {
            if (INCOMPATIBLE_WITH_CL.contains(existing.getLeaveType().getCode())) {
                throw new IncompatibleLeaveCombinationException(
                        "CL cannot be combined into an application contiguous with an existing " + existing.getLeaveType().getCode() + " application");
            }
        }
    }

    private LeaveType resolveLeaveType(Long id) {
        return leaveTypeRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException("Leave Type", id));
    }
}
