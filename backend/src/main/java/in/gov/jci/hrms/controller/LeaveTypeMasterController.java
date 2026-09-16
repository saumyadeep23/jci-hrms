package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.LeaveTypeMasterResponse;
import in.gov.jci.hrms.dto.LeaveTypeMasterUpdateRequest;
import in.gov.jci.hrms.service.LeaveTypeService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Leave Type Master &amp; Employee Category Eligibility (ALMS operational gap
 * #2) - a narrower, admin-only sibling of LeaveTypeController (/api/leave-types,
 * which stays the endpoint every applicant-facing leave-type dropdown reads
 * from) scoped to what the master console edits: accumulation cap,
 * encashability, and cadre eligibility. Deliberately no POST/DELETE here -
 * leave types themselves are still created/retired via /api/leave-types.
 */
@RestController
@RequestMapping("/api/v1/master/leave-types")
@PreAuthorize("hasRole('HR_ADMIN')")
public class LeaveTypeMasterController {

    private final LeaveTypeService leaveTypeService;

    public LeaveTypeMasterController(LeaveTypeService leaveTypeService) {
        this.leaveTypeService = leaveTypeService;
    }

    @GetMapping
    public List<LeaveTypeMasterResponse> list() {
        return leaveTypeService.listForMaster();
    }

    @PutMapping("/{id}")
    public LeaveTypeMasterResponse update(@PathVariable Long id, @Valid @RequestBody LeaveTypeMasterUpdateRequest request) {
        return leaveTypeService.updateMaster(id, request);
    }
}
