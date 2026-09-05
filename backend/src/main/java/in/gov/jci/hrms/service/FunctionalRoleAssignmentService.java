package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.FunctionalRoleAssignmentRequest;
import in.gov.jci.hrms.dto.FunctionalRoleAssignmentResponse;
import in.gov.jci.hrms.dto.FunctionalRoleMasterResponse;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeFunctionalRoleAssignment;
import in.gov.jci.hrms.entity.FunctionalRoleMaster;
import in.gov.jci.hrms.entity.RegionalOffice;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.exception.MasterDataValidationException;
import in.gov.jci.hrms.repository.DepartmentRepository;
import in.gov.jci.hrms.repository.EmployeeFunctionalRoleAssignmentRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.FunctionalRoleMasterRepository;
import in.gov.jci.hrms.repository.RegionalOfficeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * PIMS/ALMS Functional & Statutory Role Management Subsystem - assigns
 * non-sanctioned concurrent roles (HOD, CISO, CPIO, FAA, BOT_SEC,
 * HINDI_OFFICER, VIGILANCE_OFFICER, ZONAL_MGR) to an employee, on top of
 * whatever substantive post they already hold via PostIncumbency.
 *
 * Jurisdiction requirement per role is intentionally light-touch: only the
 * roles the frontend gives a specific selector (HOD -> department, CPIO/FAA
 * -> office, ZONAL_MGR -> zone) are required here. The other four roles
 * (CISO, BOT_SEC, HINDI_OFFICER, VIGILANCE_OFFICER)
 * are organization-wide appointments with no jurisdiction to validate.
 */
@Service
@Transactional(readOnly = true)
public class FunctionalRoleAssignmentService {

    private static final String ENTITY_NAME = "Functional Role Assignment";

    private final FunctionalRoleMasterRepository roleMasterRepository;
    private final EmployeeFunctionalRoleAssignmentRepository assignmentRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final RegionalOfficeRepository regionalOfficeRepository;

    public FunctionalRoleAssignmentService(FunctionalRoleMasterRepository roleMasterRepository,
                                            EmployeeFunctionalRoleAssignmentRepository assignmentRepository,
                                            EmployeeRepository employeeRepository,
                                            DepartmentRepository departmentRepository,
                                            RegionalOfficeRepository regionalOfficeRepository) {
        this.roleMasterRepository = roleMasterRepository;
        this.assignmentRepository = assignmentRepository;
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.regionalOfficeRepository = regionalOfficeRepository;
    }

    public List<FunctionalRoleMasterResponse> listRoles() {
        return roleMasterRepository.findAllByOrderByRoleCodeAsc().stream()
                .map(FunctionalRoleMasterResponse::from)
                .toList();
    }

    public List<FunctionalRoleAssignmentResponse> listAssignments(String roleCode, Long departmentId, Long officeId,
                                                                    Long employeeId, boolean activeOnly) {
        return assignmentRepository.search(roleCode, departmentId, officeId, employeeId, activeOnly).stream()
                .map(FunctionalRoleAssignmentResponse::from)
                .toList();
    }

    @Transactional
    public FunctionalRoleAssignmentResponse create(FunctionalRoleAssignmentRequest request) {
        FunctionalRoleMaster role = roleMasterRepository.findById(request.roleId())
                .orElseThrow(() -> new MasterDataNotFoundException("Functional Role", request.roleId()));
        Employee employee = employeeRepository.findById(request.employeeId())
                .orElseThrow(() -> new EmployeeNotFoundException(request.employeeId()));

        if (request.validTo() != null && request.validTo().isBefore(request.validFrom())) {
            throw new MasterDataValidationException("validTo must not be before validFrom");
        }

        Department department = null;
        if (request.departmentId() != null) {
            department = departmentRepository.findById(request.departmentId())
                    .orElseThrow(() -> new MasterDataNotFoundException("Department", request.departmentId()));
        }

        RegionalOffice office = null;
        if (request.officeId() != null) {
            office = regionalOfficeRepository.findById(request.officeId())
                    .orElseThrow(() -> new MasterDataNotFoundException("Office", request.officeId()));
        }

        requireJurisdiction(role.getRoleCode(), department, office, request.zoneCode());

        EmployeeFunctionalRoleAssignment assignment = new EmployeeFunctionalRoleAssignment(
                role, employee, request.officeOrderRef(), request.orderDate(), request.validFrom());
        assignment.setDepartment(department);
        assignment.setOffice(office);
        assignment.setZoneCode(request.zoneCode());
        assignment.setValidTo(request.validTo());
        assignment.setPrimaryRole(request.isPrimaryRole());
        assignment.setActive(true);

        return FunctionalRoleAssignmentResponse.from(assignmentRepository.saveAndFlush(assignment));
    }

    @Transactional
    public FunctionalRoleAssignmentResponse relieve(UUID id, LocalDate validTo) {
        EmployeeFunctionalRoleAssignment assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException(ENTITY_NAME, id));
        if (!assignment.isActive()) {
            throw new MasterDataValidationException(ENTITY_NAME + " " + id + " is already relieved");
        }
        if (validTo.isBefore(assignment.getValidFrom())) {
            throw new MasterDataValidationException("validTo must not be before validFrom");
        }
        assignment.setValidTo(validTo);
        assignment.setActive(false);
        return FunctionalRoleAssignmentResponse.from(assignment);
    }

    /** HOD needs a department, CPIO needs an office, ZONAL_MGR needs a zone code - the three roles the admin UI gives a dedicated jurisdiction selector for. */
    private void requireJurisdiction(String roleCode, Department department, RegionalOffice office, String zoneCode) {
        switch (roleCode) {
            case "HOD" -> {
                if (department == null) {
                    throw new MasterDataValidationException("departmentId is required for the HOD role");
                }
            }
            case "CPIO", "FAA" -> {
                if (office == null) {
                    throw new MasterDataValidationException("officeId is required for the " + roleCode + " role");
                }
            }
            case "ZONAL_MGR" -> {
                if (zoneCode == null || zoneCode.isBlank()) {
                    throw new MasterDataValidationException("zoneCode is required for the ZONAL_MGR role");
                }
            }
            default -> {
                // CISO, BOT_SEC, HINDI_OFFICER, VIGILANCE_OFFICER - organization-wide, no jurisdiction to require.
            }
        }
    }
}
