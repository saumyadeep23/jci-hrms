package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.HolidayCalendarResponse;
import in.gov.jci.hrms.dto.HolidayRequest;
import in.gov.jci.hrms.dto.HolidayResponse;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.security.SecurityUtils;
import in.gov.jci.hrms.service.HolidayService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * GET is open to any authenticated employee (calendar visibility - everyone
 * needs to know which days are holidays), mutations are HR_ADMIN/SUPER_ADMIN
 * only - unlike most master-data controllers in this codebase, which gate the
 * whole class behind one role.
 */
@RestController
@RequestMapping("/api/holidays")
public class HolidayController {

    private final HolidayService holidayService;

    public HolidayController(HolidayService holidayService) {
        this.holidayService = holidayService;
    }

    @PostMapping
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<HolidayResponse> create(@Valid @RequestBody HolidayRequest request) {
        HolidayResponse created = holidayService.create(request);
        return ResponseEntity.created(URI.create("/api/holidays/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public HolidayResponse getById(@PathVariable Long id) {
        return holidayService.getById(id);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Page<HolidayResponse> list(Pageable pageable) {
        return holidayService.list(pageable);
    }

    /**
     * Location-aware holiday calendar for the dashboard widget: the union of
     * national/CENTRAL gazetted holidays, the caller's state-specific
     * gazetted holidays, and restricted holidays available at their
     * location, for one calendar month.
     */
    @GetMapping("/my-calendar")
    @PreAuthorize("isAuthenticated()")
    public HolidayCalendarResponse getMyCalendar(@RequestParam int year, @RequestParam int month,
                                                   Authentication authentication) {
        Long employeeId = SecurityUtils.currentEmployeeId(authentication);
        if (employeeId == null) {
            throw new BusinessRuleViolationException(
                    "Your token has no employee_id claim - cannot resolve your posting location");
        }
        return holidayService.getMyCalendar(employeeId, year, month);
    }

    /**
     * Restricted holidays applicable to the caller's own posting location for
     * a whole year - e.g. the Combined CL+RH form's RH dropdown - scoped and
     * deduplicated the same way getMyCalendar() is (see HolidayService).
     */
    @GetMapping("/my-restricted")
    @PreAuthorize("isAuthenticated()")
    public List<HolidayResponse> getMyRestrictedHolidays(@RequestParam int year, Authentication authentication) {
        Long employeeId = SecurityUtils.currentEmployeeId(authentication);
        if (employeeId == null) {
            throw new BusinessRuleViolationException(
                    "Your token has no employee_id claim - cannot resolve your posting location");
        }
        return holidayService.getMyRestrictedHolidays(employeeId, year);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public HolidayResponse update(@PathVariable Long id, @Valid @RequestBody HolidayRequest request) {
        return holidayService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        holidayService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
