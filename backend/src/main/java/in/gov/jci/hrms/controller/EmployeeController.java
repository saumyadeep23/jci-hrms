package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.Employee360Response;
import in.gov.jci.hrms.dto.EmployeeRequest;
import in.gov.jci.hrms.dto.EmployeeResponse;
import in.gov.jci.hrms.dto.MinistryExtensionRequest;
import in.gov.jci.hrms.dto.SeparatedEmployeeResponse;
import in.gov.jci.hrms.dto.ServiceBookEventRequest;
import in.gov.jci.hrms.dto.ServiceBookEventResponse;
import in.gov.jci.hrms.dto.SuperannuationCalculationPreviewResponse;
import in.gov.jci.hrms.dto.SuperannuationExtensionResponse;
import in.gov.jci.hrms.entity.EmploymentCategory;
import in.gov.jci.hrms.service.Employee360Service;
import in.gov.jci.hrms.service.EmployeeService;
import in.gov.jci.hrms.service.EmployeeServiceBookService;
import in.gov.jci.hrms.service.SeparatedEmployeeDirectoryService;
import in.gov.jci.hrms.service.SuperannuationExtensionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
import java.util.Map;

/** Mapped under both /api/employees (legacy) and /api/v1/employees (PIMS_SPEC.md's versioned convention for newer endpoints below). */
@RestController
@RequestMapping({"/api/employees", "/api/v1/employees"})
public class EmployeeController {

    private final EmployeeService employeeService;
    private final Employee360Service employee360Service;
    private final EmployeeServiceBookService employeeServiceBookService;
    private final SuperannuationExtensionService superannuationExtensionService;
    private final SeparatedEmployeeDirectoryService separatedEmployeeDirectoryService;

    public EmployeeController(EmployeeService employeeService, Employee360Service employee360Service,
                               EmployeeServiceBookService employeeServiceBookService,
                               SuperannuationExtensionService superannuationExtensionService,
                               SeparatedEmployeeDirectoryService separatedEmployeeDirectoryService) {
        this.employeeService = employeeService;
        this.employee360Service = employee360Service;
        this.employeeServiceBookService = employeeServiceBookService;
        this.superannuationExtensionService = superannuationExtensionService;
        this.separatedEmployeeDirectoryService = separatedEmployeeDirectoryService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<EmployeeResponse> create(@Valid @RequestBody EmployeeRequest request) {
        EmployeeResponse created = employeeService.create(request);
        return ResponseEntity.created(URI.create("/api/employees/" + created.id())).body(created);
    }

    /**
     * Onboarding Step 1's CPF A/C No placeholder - a preview only, not a
     * reservation (see EmployeeService.generateNextCpfAcNo()'s javadoc):
     * the real value is resolved fresh at draft finalize time, so this can
     * go stale between fetch and submit without causing a collision.
     */
    @GetMapping("/next-cpf-ac-no")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, String>> getNextCpfAcNo() {
        return ResponseEntity.ok(Map.of("nextCpfAcNo", employeeService.generateNextCpfAcNo()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN') or @employeeSecurity.isSelf(authentication, #id)")
    public EmployeeResponse getById(@PathVariable Long id) {
        return employeeService.getById(id);
    }

    /**
     * Employee Directory - search/roId/designationId/departmentId/employmentType/status filters,
     * defaulting to a stable employeeCode-ascending sort (an unsorted findAll() previously returned
     * rows in whatever unstable physical order Postgres happened to store them in, which could - and
     * did - shift a freshly-updated row off the first page entirely).
     */
    /** Widened to CPF_ADMIN/FINANCE_ADMIN: the CPF Trust module's employee pickers (Incoming Transfers, Loan Origination) need to search/resolve an employee by code or name, and those roles already see full employee-level CPF ledger/withdrawal data elsewhere in this same module (PfLedgerController, CpfTrustController). */
    @GetMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'CPF_ADMIN', 'FINANCE_ADMIN', 'SUPER_ADMIN')")
    public Page<EmployeeResponse> list(@RequestParam(required = false) String search,
                                        @RequestParam(required = false) Long roId,
                                        @RequestParam(required = false) Long designationId,
                                        @RequestParam(required = false) Long departmentId,
                                        @RequestParam(required = false) EmploymentCategory employmentType,
                                        @RequestParam(required = false) String status,
                                        @PageableDefault(size = 15, sort = "employeeCode", direction = Sort.Direction.ASC) Pageable pageable) {
        return employeeService.list(search, roId, designationId, departmentId, employmentType, status, pageable);
    }

    /** PIMS "Separated Staff" tab - richer than the plain status=SEPARATED filter on the main list() above (which returns the ordinary EmployeeResponse shape); this one joins in clearance/settlement/last-post details. */
    @GetMapping("/separated")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public Page<SeparatedEmployeeResponse> separated(@RequestParam(required = false) String status,
                                                       @RequestParam(required = false) String search,
                                                       @PageableDefault(size = 15) Pageable pageable) {
        return separatedEmployeeDirectoryService.list(status, search, pageable);
    }

    /** PIMS_SPEC.md reporting-hub drill-through: the Employee 360 profile drawer. */
    @GetMapping("/{id}/360")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN') or @employeeSecurity.isSelf(authentication, #id)")
    public Employee360Response get360(@PathVariable Long id) {
        return employee360Service.getById(id);
    }

    /** Digital Service Book Career Event Logger (operational-features task, Section 3). */
    @GetMapping("/{id}/service-book")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN') or @employeeSecurity.isSelf(authentication, #id)")
    public List<ServiceBookEventResponse> serviceBookTimeline(@PathVariable Long id) {
        return employeeServiceBookService.timeline(id);
    }

    @PostMapping("/{id}/service-book")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ServiceBookEventResponse> recordServiceBookEvent(@PathVariable Long id, @Valid @RequestBody ServiceBookEventRequest request) {
        ServiceBookEventResponse created = employeeServiceBookService.recordEvent(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** Director Ministry Extension + Superannuation Calculator (operational-features task, Section 4). */
    @PostMapping("/{id}/superannuation/ministry-extension")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public SuperannuationExtensionResponse applyMinistryExtension(@PathVariable Long id, @Valid @RequestBody MinistryExtensionRequest request) {
        return superannuationExtensionService.applyMinistryExtension(id, request);
    }

    @GetMapping("/{id}/superannuation/calculation-preview")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN') or @employeeSecurity.isSelf(authentication, #id)")
    public SuperannuationCalculationPreviewResponse calculationPreview(@PathVariable Long id) {
        return superannuationExtensionService.calculationPreview(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public EmployeeResponse update(@PathVariable Long id, @Valid @RequestBody EmployeeRequest request) {
        return employeeService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        employeeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
