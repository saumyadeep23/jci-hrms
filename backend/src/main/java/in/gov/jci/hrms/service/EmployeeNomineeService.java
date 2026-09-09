package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.NomineeRequest;
import in.gov.jci.hrms.dto.NomineeResponse;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeDependent;
import in.gov.jci.hrms.entity.EmployeeNominee;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.EmployeeDependentRepository;
import in.gov.jci.hrms.repository.EmployeeNomineeRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class EmployeeNomineeService {

    private static final String ENTITY_NAME = "Nominee";
    private static final String DEPENDENT_ENTITY_NAME = "Dependent";

    private final EmployeeNomineeRepository nomineeRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeDependentRepository dependentRepository;

    public EmployeeNomineeService(EmployeeNomineeRepository nomineeRepository, EmployeeRepository employeeRepository,
                                   EmployeeDependentRepository dependentRepository) {
        this.nomineeRepository = nomineeRepository;
        this.employeeRepository = employeeRepository;
        this.dependentRepository = dependentRepository;
    }

    public List<NomineeResponse> listByEmployee(Long employeeId) {
        resolveEmployee(employeeId);
        return nomineeRepository.findByEmployeeId(employeeId).stream()
                .map(NomineeResponse::from)
                .toList();
    }

    @Transactional
    public NomineeResponse create(Long employeeId, NomineeRequest request) {
        Employee employee = resolveEmployee(employeeId);
        EmployeeDependent linkedDependent = resolveOptionalDependent(employeeId, request.dependentId());
        EmployeeNominee nominee = linkedDependent != null
                ? new EmployeeNominee(employee, linkedDependent.getName(), linkedDependent.getRelationship(), request.sharePercentage(), request.nomineeFor())
                : new EmployeeNominee(employee, request.name(), request.relationship(), request.sharePercentage(), request.nomineeFor());
        nominee.setDependent(linkedDependent);
        return NomineeResponse.from(nomineeRepository.saveAndFlush(nominee));
    }

    @Transactional
    public NomineeResponse update(Long employeeId, Long id, NomineeRequest request) {
        EmployeeNominee nominee = findOrThrow(employeeId, id);
        EmployeeDependent linkedDependent = resolveOptionalDependent(employeeId, request.dependentId());
        if (linkedDependent != null) {
            nominee.setName(linkedDependent.getName());
            nominee.setRelationship(linkedDependent.getRelationship());
        } else {
            nominee.setName(request.name());
            nominee.setRelationship(request.relationship());
        }
        nominee.setDependent(linkedDependent);
        nominee.setSharePercentage(request.sharePercentage());
        nominee.setNomineeFor(request.nomineeFor());
        return NomineeResponse.from(nomineeRepository.saveAndFlush(nominee));
    }

    /**
     * When a dependentId is supplied, name/relationship are ALWAYS derived from that Family Register
     * row server-side (see create()/update()) rather than trusted from the request - this is what
     * actually eliminates redundant entry, not just hiding it in the UI. dependentId is optional here
     * (unlike the composite endpoint's own nominee entry) for the standalone API's backward
     * compatibility.
     */
    private EmployeeDependent resolveOptionalDependent(Long employeeId, Long dependentId) {
        if (dependentId == null) {
            return null;
        }
        EmployeeDependent dependent = dependentRepository.findById(dependentId)
                .orElseThrow(() -> new MasterDataNotFoundException(DEPENDENT_ENTITY_NAME, dependentId));
        if (!dependent.getEmployee().getId().equals(employeeId)) {
            throw new MasterDataNotFoundException(DEPENDENT_ENTITY_NAME, dependentId);
        }
        return dependent;
    }

    @Transactional
    public void delete(Long employeeId, Long id) {
        EmployeeNominee nominee = findOrThrow(employeeId, id);
        nominee.setDeletedAt(Instant.now());
    }

    private EmployeeNominee findOrThrow(Long employeeId, Long id) {
        EmployeeNominee nominee = nomineeRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException(ENTITY_NAME, id));
        if (!nominee.getEmployee().getId().equals(employeeId)) {
            throw new MasterDataNotFoundException(ENTITY_NAME, id);
        }
        return nominee;
    }

    private Employee resolveEmployee(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
    }
}
