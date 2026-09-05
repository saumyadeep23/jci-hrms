package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.DependencyCheckResponse;
import in.gov.jci.hrms.dto.DepartmentRequest;
import in.gov.jci.hrms.dto.DepartmentResponse;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataInUseException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.DepartmentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class DepartmentService {

    private static final String ENTITY_NAME = "Department";

    /** PIMS_SPEC.md Section 1.B: block deactivation if post_master or employees still actively reference it. */
    private static final List<MasterDependencyService.DependencyProbe> DEPENDENCY_PROBES = List.of(
            new MasterDependencyService.DependencyProbe("employees", "department_id", "Employees", true),
            new MasterDependencyService.DependencyProbe("post_master", "department_id", "Sanctioned Posts", true),
            new MasterDependencyService.DependencyProbe("designations", "department_id", "Designations", true)
    );

    private final DepartmentRepository departmentRepository;
    private final MasterDependencyService masterDependencyService;

    public DepartmentService(DepartmentRepository departmentRepository, MasterDependencyService masterDependencyService) {
        this.departmentRepository = departmentRepository;
        this.masterDependencyService = masterDependencyService;
    }

    @Transactional
    public DepartmentResponse create(DepartmentRequest request) {
        Department department = new Department(request.code(), request.name());
        department.setDescription(request.description());
        return DepartmentResponse.from(save(department));
    }

    public DepartmentResponse getById(Long id) {
        return DepartmentResponse.from(findOrThrow(id));
    }

    public Page<DepartmentResponse> list(Pageable pageable) {
        return departmentRepository.findAll(pageable).map(DepartmentResponse::from);
    }

    @Transactional
    public DepartmentResponse update(Long id, DepartmentRequest request) {
        Department department = findOrThrow(id);
        department.setCode(request.code());
        department.setName(request.name());
        department.setDescription(request.description());
        return DepartmentResponse.from(save(department));
    }

    /** PATCH .../{id}/status - Department has no separate is_active column, only deleted_at, so "active" here just means "not soft-deleted". */
    @Transactional
    public DepartmentResponse updateStatus(Long id, boolean active) {
        Department department = findOrThrow(id);
        if (!active) {
            DependencyCheckResponse dependencies = masterDependencyService.check(DEPENDENCY_PROBES, id);
            if (dependencies.hasActiveDependencies()) {
                throw new MasterDataInUseException(ENTITY_NAME, id);
            }
            department.setDeletedAt(Instant.now());
        } else {
            department.setDeletedAt(null);
        }
        return DepartmentResponse.from(department);
    }

    public DependencyCheckResponse dependencies(Long id) {
        findOrThrow(id);
        return masterDependencyService.check(DEPENDENCY_PROBES, id);
    }

    @Transactional
    public void delete(Long id) {
        updateStatus(id, false);
    }

    private Department findOrThrow(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException(ENTITY_NAME, id));
    }

    private Department save(Department department) {
        try {
            return departmentRepository.saveAndFlush(department);
        } catch (DataIntegrityViolationException ex) {
            throw new MasterDataConflictException(ENTITY_NAME + " code or name already in use: " + department.getCode());
        }
    }
}
