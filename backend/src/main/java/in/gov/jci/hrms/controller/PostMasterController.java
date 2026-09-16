package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.AssignIncumbencyRequest;
import in.gov.jci.hrms.dto.PostIncumbencyRequest;
import in.gov.jci.hrms.dto.PostIncumbencyResponse;
import in.gov.jci.hrms.dto.PostInventorySummaryResponse;
import in.gov.jci.hrms.dto.PostMasterRequest;
import in.gov.jci.hrms.dto.PostMasterResponse;
import in.gov.jci.hrms.dto.PostStatusUpdateRequest;
import in.gov.jci.hrms.entity.VacancyStatus;
import in.gov.jci.hrms.service.PostIncumbencyService;
import in.gov.jci.hrms.service.PostMasterService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

/** PIMS_SPEC.md Feature 2 (Sanctioned Seat Inventory) / Section 2 (admin GUI). */
@RestController
@RequestMapping("/api/v1/posts")
@PreAuthorize("hasRole('HR_ADMIN')")
public class PostMasterController {

    private final PostMasterService postMasterService;
    private final PostIncumbencyService postIncumbencyService;

    public PostMasterController(PostMasterService postMasterService, PostIncumbencyService postIncumbencyService) {
        this.postMasterService = postMasterService;
        this.postIncumbencyService = postIncumbencyService;
    }

    @PostMapping
    public ResponseEntity<PostMasterResponse> create(@Valid @RequestBody PostMasterRequest request) {
        PostMasterResponse created = postMasterService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/posts/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public PostMasterResponse getById(@PathVariable Long id) {
        return postMasterService.getById(id);
    }

    /** Multi-filter toolbar (Section 2.A) - department/designation/RO/DPC/vacancy-status/budgeted, plus a locationType and free-text quick search. */
    @GetMapping
    public Page<PostMasterResponse> list(@RequestParam(required = false) Long departmentId,
                                          @RequestParam(required = false) Long designationId,
                                          @RequestParam(required = false) Long roId,
                                          @RequestParam(required = false) Long dpcId,
                                          @RequestParam(required = false) VacancyStatus vacancyStatus,
                                          @RequestParam(required = false) Boolean isBudgeted,
                                          @RequestParam(required = false) String locationType,
                                          @RequestParam(required = false) String search,
                                          Pageable pageable) {
        return postMasterService.search(departmentId, designationId, roId, dpcId, vacancyStatus, isBudgeted, locationType, search, pageable);
    }

    /** Budgeted, vacant posts eligible for a new REGULAR assignment - backs the onboarding wizard's Step 6 post picker. */
    @GetMapping("/vacant")
    public Page<PostMasterResponse> listVacant(Pageable pageable) {
        return postMasterService.listVacant(pageable);
    }

    /** Edit Employee's "Vacant Sanctioned Post" selector - VACANT posts plus the employee's own current post(s). */
    @GetMapping("/available-for-assignment")
    public Page<PostMasterResponse> availableForAssignment(@RequestParam(required = false) Long employeeId,
                                                             @RequestParam(required = false) Long designationId,
                                                             @RequestParam(required = false) Long departmentId,
                                                             Pageable pageable) {
        return postMasterService.listAvailableForAssignment(employeeId, designationId, departmentId, pageable);
    }

    /** Top metric cards (Section 2.A). */
    @GetMapping("/summary")
    public PostInventorySummaryResponse summary() {
        return postMasterService.summary();
    }

    /** Post Incumbency History Drawer (Section 2.C) - full chronological ledger, most recent first. */
    @GetMapping("/{id}/incumbency-history")
    public List<PostIncumbencyResponse> incumbencyHistory(@PathVariable Long id) {
        return postMasterService.incumbencyHistory(id);
    }

    /**
     * Dual/Additional Charge assignment (Section 2 of the PIMS operational-features
     * task). Delegates to PostIncumbencyService.create(), which already never
     * touches post_master.vacancy_status for a non-SUBSTANTIVE assignment type -
     * a seat already held by a SUBSTANTIVE incumbent is left OCCUPIED.
     */
    @PostMapping("/{id}/incumbency")
    public ResponseEntity<PostIncumbencyResponse> assignIncumbency(@PathVariable Long id, @Valid @RequestBody AssignIncumbencyRequest request) {
        String orderReference = request.orderDate() != null
                ? (request.orderReference() != null ? request.orderReference() + " (dated " + request.orderDate() + ")" : "dated " + request.orderDate())
                : request.orderReference();
        PostIncumbencyResponse created = postIncumbencyService.create(new PostIncumbencyRequest(
                id, request.employeeId(), request.assignmentType(), request.startDate(), null, orderReference));
        return ResponseEntity.created(URI.create("/api/v1/posts/" + id + "/incumbency-history")).body(created);
    }

    /** Ends a dual/additional-charge (or any) incumbency as of today - PostIncumbencyService.end() with CURRENT_DATE. */
    @PatchMapping("/incumbency/{incumbencyId}/relieve")
    public PostIncumbencyResponse relieveIncumbency(@PathVariable Long incumbencyId) {
        return postIncumbencyService.end(incumbencyId, LocalDate.now());
    }

    @PutMapping("/{id}")
    public PostMasterResponse update(@PathVariable Long id, @Valid @RequestBody PostMasterRequest request) {
        return postMasterService.update(id, request);
    }

    /** Change Status: Freeze / Unfreeze / Abolish, with an audit remark (Section 2.C). */
    @PatchMapping("/{id}/status")
    public PostMasterResponse updateStatus(@PathVariable Long id, @Valid @RequestBody PostStatusUpdateRequest request) {
        return postMasterService.updateStatus(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        postMasterService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
