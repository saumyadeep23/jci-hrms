package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.SocialProfileRequest;
import in.gov.jci.hrms.dto.SocialProfileResponse;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeSocialProfile;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.EmployeeSocialProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One row per employee (employee_social_profiles, V31/V32) - optional, feeds
 * vw_jci_employee_master_360 and the Reservation Roster report. Onboarding
 * only creates a row if Step 1/7's fields were filled in; this service backs
 * Edit Employee's Personal tab for an already-onboarded employee - get()
 * returns null (not 404) when no row exists yet.
 */
@Service
@Transactional(readOnly = true)
public class EmployeeSocialProfileService {

    private final EmployeeSocialProfileRepository socialProfileRepository;
    private final EmployeeRepository employeeRepository;

    public EmployeeSocialProfileService(EmployeeSocialProfileRepository socialProfileRepository, EmployeeRepository employeeRepository) {
        this.socialProfileRepository = socialProfileRepository;
        this.employeeRepository = employeeRepository;
    }

    public SocialProfileResponse getByEmployee(Long employeeId) {
        resolveEmployee(employeeId);
        return socialProfileRepository.findByEmployeeId(employeeId)
                .map(SocialProfileResponse::from)
                .orElse(null);
    }

    @Transactional
    public SocialProfileResponse upsert(Long employeeId, SocialProfileRequest request) {
        EmployeeSocialProfile profile = socialProfileRepository.findByEmployeeId(employeeId)
                .orElseGet(() -> new EmployeeSocialProfile(resolveEmployee(employeeId), request.socialCategory()));
        profile.setSocialCategory(request.socialCategory());
        profile.setSubCasteCommunity(request.subCasteCommunity());
        profile.setPwbd(request.isPwbd());
        profile.setDisabilityType(request.disabilityType());
        profile.setDisabilityPercentage(request.disabilityPercentage());
        return SocialProfileResponse.from(socialProfileRepository.saveAndFlush(profile));
    }

    private Employee resolveEmployee(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
    }
}
