package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.FamilyDetailsRequest;
import in.gov.jci.hrms.dto.FamilyDetailsResponse;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeFamilyDetails;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.repository.EmployeeFamilyDetailsRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One row per employee (PIMS_SPEC.md Step 7). Onboarding creates the initial
 * row (see EmployeeOnboardingService.finalizeOnboarding); this service backs
 * Edit Employee's Family & Nominees tab for an already-onboarded employee -
 * get() returns null (not 404) when no row exists yet so the frontend can
 * render an empty form, and upsert() creates the row on first save.
 */
@Service
@Transactional(readOnly = true)
public class EmployeeFamilyDetailsService {

    private final EmployeeFamilyDetailsRepository familyDetailsRepository;
    private final EmployeeRepository employeeRepository;

    public EmployeeFamilyDetailsService(EmployeeFamilyDetailsRepository familyDetailsRepository, EmployeeRepository employeeRepository) {
        this.familyDetailsRepository = familyDetailsRepository;
        this.employeeRepository = employeeRepository;
    }

    public FamilyDetailsResponse getByEmployee(Long employeeId) {
        resolveEmployee(employeeId);
        return familyDetailsRepository.findByEmployeeId(employeeId)
                .map(FamilyDetailsResponse::from)
                .orElse(null);
    }

    @Transactional
    public FamilyDetailsResponse upsert(Long employeeId, FamilyDetailsRequest request) {
        EmployeeFamilyDetails details = familyDetailsRepository.findByEmployeeId(employeeId)
                .orElseGet(() -> new EmployeeFamilyDetails(resolveEmployee(employeeId), request.fatherName()));
        details.setFatherName(request.fatherName());
        details.setMotherName(request.motherName());
        details.setSpouseName(request.spouseName());
        details.setSpouseDob(request.spouseDob());
        return FamilyDetailsResponse.from(familyDetailsRepository.saveAndFlush(details));
    }

    private Employee resolveEmployee(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
    }
}
