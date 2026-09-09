package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.DependentRequest;
import in.gov.jci.hrms.dto.DependentResponse;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeDependent;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.EmployeeDependentRepository;
import in.gov.jci.hrms.repository.EmployeeFamilyDetailsRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class EmployeeDependentService {

    private static final String ENTITY_NAME = "Dependent";

    private final EmployeeDependentRepository dependentRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeFamilyDetailsRepository familyDetailsRepository;

    public EmployeeDependentService(EmployeeDependentRepository dependentRepository, EmployeeRepository employeeRepository,
                                     EmployeeFamilyDetailsRepository familyDetailsRepository) {
        this.dependentRepository = dependentRepository;
        this.employeeRepository = employeeRepository;
        this.familyDetailsRepository = familyDetailsRepository;
    }

    public List<DependentResponse> listByEmployee(Long employeeId) {
        resolveEmployee(employeeId);
        return dependentRepository.findByEmployeeId(employeeId).stream()
                .map(DependentResponse::from)
                .toList();
    }

    @Transactional
    public DependentResponse create(Long employeeId, DependentRequest request) {
        EmployeeDependent dependent = new EmployeeDependent(
                resolveEmployee(employeeId), request.name(), request.relationship(), request.isDependent(), request.isCoveredMedical());
        dependent.setDateOfBirth(request.dateOfBirth());
        applyDivyangAndBirthFields(dependent, request);
        DependentResponse saved = DependentResponse.from(dependentRepository.saveAndFlush(dependent));
        syncDependentCount(employeeId);
        return saved;
    }

    @Transactional
    public DependentResponse update(Long employeeId, Long id, DependentRequest request) {
        EmployeeDependent dependent = findOrThrow(employeeId, id);
        dependent.setName(request.name());
        dependent.setRelationship(request.relationship());
        dependent.setDateOfBirth(request.dateOfBirth());
        dependent.setDependent(request.isDependent());
        dependent.setCoveredMedical(request.isCoveredMedical());
        applyDivyangAndBirthFields(dependent, request);
        return DependentResponse.from(dependentRepository.saveAndFlush(dependent));
    }

    private void applyDivyangAndBirthFields(EmployeeDependent dependent, DependentRequest request) {
        dependent.setGender(request.gender());
        dependent.setDivyang(request.isDivyang());
        dependent.setDisabilityPercentage(request.disabilityPercentage());
        dependent.setMultipleBirthSecondDelivery(request.isMultipleBirthSecondDelivery());
    }

    @Transactional
    public void delete(Long employeeId, Long id) {
        EmployeeDependent dependent = findOrThrow(employeeId, id);
        dependent.setDeletedAt(Instant.now());
        syncDependentCount(employeeId);
    }

    /** employee_family_details.dependent_count is a denormalized snapshot - see EmployeeOnboardingService.finalizeOnboarding. */
    private void syncDependentCount(Long employeeId) {
        familyDetailsRepository.findByEmployeeId(employeeId)
                .ifPresent(details -> details.setDependentCount(dependentRepository.findByEmployeeId(employeeId).size()));
    }

    private EmployeeDependent findOrThrow(Long employeeId, Long id) {
        EmployeeDependent dependent = dependentRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException(ENTITY_NAME, id));
        if (!dependent.getEmployee().getId().equals(employeeId)) {
            throw new MasterDataNotFoundException(ENTITY_NAME, id);
        }
        return dependent;
    }

    private Employee resolveEmployee(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
    }
}
