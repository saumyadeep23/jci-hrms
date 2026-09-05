package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.QualificationRequest;
import in.gov.jci.hrms.dto.QualificationResponse;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeQualification;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.EmployeeQualificationRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class EmployeeQualificationService {

    private static final String ENTITY_NAME = "Employee Qualification";

    private final EmployeeQualificationRepository qualificationRepository;
    private final EmployeeRepository employeeRepository;

    public EmployeeQualificationService(EmployeeQualificationRepository qualificationRepository,
                                         EmployeeRepository employeeRepository) {
        this.qualificationRepository = qualificationRepository;
        this.employeeRepository = employeeRepository;
    }

    public List<QualificationResponse> listByEmployee(Long employeeId) {
        resolveEmployee(employeeId);
        return qualificationRepository.findByEmployeeIdOrderByPassingYearDesc(employeeId).stream()
                .map(QualificationResponse::from)
                .toList();
    }

    @Transactional
    public QualificationResponse create(Long employeeId, QualificationRequest request) {
        EmployeeQualification qualification = new EmployeeQualification(
                resolveEmployee(employeeId), request.qualificationLevel(), request.degreeTitle(),
                request.boardUniversity(), request.passingYear());
        applyOptionalFields(qualification, request);
        return QualificationResponse.from(qualificationRepository.saveAndFlush(qualification));
    }

    @Transactional
    public QualificationResponse update(Long employeeId, Long id, QualificationRequest request) {
        EmployeeQualification qualification = findOrThrow(employeeId, id);
        qualification.setQualificationLevel(request.qualificationLevel());
        qualification.setDegreeTitle(request.degreeTitle());
        qualification.setBoardUniversity(request.boardUniversity());
        qualification.setPassingYear(request.passingYear());
        applyOptionalFields(qualification, request);
        return QualificationResponse.from(qualificationRepository.saveAndFlush(qualification));
    }

    @Transactional
    public QualificationResponse verify(Long employeeId, Long id, String verifiedBy) {
        EmployeeQualification qualification = findOrThrow(employeeId, id);
        qualification.markVerified(verifiedBy);
        return QualificationResponse.from(qualificationRepository.saveAndFlush(qualification));
    }

    @Transactional
    public void delete(Long employeeId, Long id) {
        EmployeeQualification qualification = findOrThrow(employeeId, id);
        qualification.setDeletedAt(Instant.now());
    }

    private void applyOptionalFields(EmployeeQualification qualification, QualificationRequest request) {
        qualification.setSpecialization(request.specialization());
        qualification.setInstitutionName(request.institutionName());
        qualification.setPercentageCgpa(request.percentageCgpa());
        qualification.setDivisionClass(request.divisionClass());
        qualification.setCourseType(request.courseType());
        qualification.setHighestQualification(request.highestQualification());
        qualification.setCertificateDocumentS3Key(request.certificateDocumentS3Key());
    }

    private EmployeeQualification findOrThrow(Long employeeId, Long id) {
        EmployeeQualification qualification = qualificationRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException(ENTITY_NAME, id));
        if (!qualification.getEmployee().getId().equals(employeeId)) {
            throw new MasterDataNotFoundException(ENTITY_NAME, id);
        }
        return qualification;
    }

    private Employee resolveEmployee(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
    }
}
