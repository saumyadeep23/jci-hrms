package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.DailyAttendanceDetailResponse;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.security.SecurityUtils;
import in.gov.jci.hrms.service.AttendanceAggregationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Triggers AttendanceAggregationService's evaluation pipeline - there is no
 * scheduler anywhere in this codebase, so nothing runs this automatically;
 * it fires when called, either by an employee re-evaluating their own
 * attendance or by HR/SUPER_ADMIN re-evaluating someone else's (e.g. after
 * correcting a punch or retroactively sanctioning leave).
 */
@RestController
@RequestMapping("/api/attendance/aggregation")
public class AttendanceAggregationController {

    private final AttendanceAggregationService attendanceAggregationService;

    public AttendanceAggregationController(AttendanceAggregationService attendanceAggregationService) {
        this.attendanceAggregationService = attendanceAggregationService;
    }

    @PostMapping("/evaluate")
    @PreAuthorize("isAuthenticated() and @attendanceAggSec.canEvaluateFor(authentication, #employeeId)")
    public List<DailyAttendanceDetailResponse> evaluateMonth(@RequestParam(required = false) Long employeeId,
                                                               @RequestParam int year, @RequestParam int month,
                                                               Authentication authentication) {
        return attendanceAggregationService.evaluateMonth(resolveTarget(employeeId, authentication), year, month);
    }

    @PostMapping("/evaluate-day")
    @PreAuthorize("isAuthenticated() and @attendanceAggSec.canEvaluateFor(authentication, #employeeId)")
    public DailyAttendanceDetailResponse evaluateDay(@RequestParam(required = false) Long employeeId,
                                                       @RequestParam LocalDate date,
                                                       Authentication authentication) {
        return attendanceAggregationService.evaluateDay(resolveTarget(employeeId, authentication), date);
    }

    private Long resolveTarget(Long employeeId, Authentication authentication) {
        if (employeeId != null) {
            return employeeId;
        }
        Long selfId = SecurityUtils.currentEmployeeId(authentication);
        if (selfId == null) {
            throw new BusinessRuleViolationException(
                    "Your token has no employee_id claim - cannot resolve whose attendance to evaluate");
        }
        return selfId;
    }
}
