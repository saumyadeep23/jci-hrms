package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.VehicleAllotmentRequest;
import in.gov.jci.hrms.dto.VehicleAllotmentResponse;
import in.gov.jci.hrms.dto.VehicleAllotmentSurrenderRequest;
import in.gov.jci.hrms.service.EmployeeVehicleAllotmentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/** Vehicle Allotment Transaction Management (Employment tab) - current and historical vehicle allotments for one employee. */
@RestController
@RequestMapping("/api/v1/employees/{employeeId}/vehicle-allotments")
@PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN') or @employeeSecurity.isSelf(authentication, #employeeId)")
public class EmployeeVehicleAllotmentController {

    private final EmployeeVehicleAllotmentService vehicleAllotmentService;

    public EmployeeVehicleAllotmentController(EmployeeVehicleAllotmentService vehicleAllotmentService) {
        this.vehicleAllotmentService = vehicleAllotmentService;
    }

    @GetMapping
    public List<VehicleAllotmentResponse> list(@PathVariable Long employeeId) {
        return vehicleAllotmentService.listByEmployee(employeeId);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<VehicleAllotmentResponse> create(@PathVariable Long employeeId,
                                                            @Valid @RequestBody VehicleAllotmentRequest request) {
        VehicleAllotmentResponse created = vehicleAllotmentService.create(employeeId, request);
        return ResponseEntity.created(URI.create("/api/v1/employees/" + employeeId + "/vehicle-allotments/" + created.id()))
                .body(created);
    }

    @PutMapping("/{id}/surrender")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public VehicleAllotmentResponse surrender(@PathVariable Long employeeId, @PathVariable Long id,
                                               @Valid @RequestBody VehicleAllotmentSurrenderRequest request) {
        return vehicleAllotmentService.surrender(employeeId, id, request);
    }
}
