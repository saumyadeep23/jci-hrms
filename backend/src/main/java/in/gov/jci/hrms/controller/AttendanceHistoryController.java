package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.DailyAttendanceSummaryResponse;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.security.SecurityUtils;
import in.gov.jci.hrms.service.MobilePunchService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Separate from MobilePunchController (mapped at /api/attendance/punch) -
 * this needs to live at /api/attendance directly so the route is
 * /api/attendance/my-history, not nested under /punch.
 */
@RestController
@RequestMapping("/api/attendance")
public class AttendanceHistoryController {

    private final MobilePunchService mobilePunchService;

    public AttendanceHistoryController(MobilePunchService mobilePunchService) {
        this.mobilePunchService = mobilePunchService;
    }

    /** Always the caller's own history - employeeId is resolved from the JWT, never a request parameter, so there is no way to view someone else's punches through this endpoint. */
    @GetMapping("/my-history")
    @PreAuthorize("isAuthenticated()")
    public List<DailyAttendanceSummaryResponse> myHistory(@RequestParam int year, @RequestParam int month,
                                                            Authentication authentication) {
        Long employeeId = SecurityUtils.currentEmployeeId(authentication);
        if (employeeId == null) {
            throw new BusinessRuleViolationException(
                    "Your token has no employee_id claim - cannot resolve whose attendance history to return");
        }
        return mobilePunchService.getMyHistory(employeeId, year, month);
    }
}
