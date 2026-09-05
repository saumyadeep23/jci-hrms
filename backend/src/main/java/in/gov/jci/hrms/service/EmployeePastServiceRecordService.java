package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.PastServiceRecordRequest;
import in.gov.jci.hrms.dto.PastServiceRecordResponse;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeePastServiceRecord;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.exception.MasterDataValidationException;
import in.gov.jci.hrms.repository.EmployeePastServiceRecordRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class EmployeePastServiceRecordService {

    private static final String ENTITY_NAME = "Past Service Record";

    private final EmployeePastServiceRecordRepository pastServiceRecordRepository;
    private final EmployeeRepository employeeRepository;

    public EmployeePastServiceRecordService(EmployeePastServiceRecordRepository pastServiceRecordRepository,
                                             EmployeeRepository employeeRepository) {
        this.pastServiceRecordRepository = pastServiceRecordRepository;
        this.employeeRepository = employeeRepository;
    }

    public List<PastServiceRecordResponse> listByEmployee(Long employeeId) {
        resolveEmployee(employeeId);
        return pastServiceRecordRepository.findByEmployeeIdOrderByFromDateDesc(employeeId).stream()
                .map(PastServiceRecordResponse::from)
                .toList();
    }

    @Transactional
    public PastServiceRecordResponse create(Long employeeId, PastServiceRecordRequest request) {
        validateDates(request);
        EmployeePastServiceRecord record = new EmployeePastServiceRecord(
                resolveEmployee(employeeId), request.organizationName(), request.organizationType(),
                request.designationHeld(), request.fromDate(), request.toDate());
        applyOptionalFields(record, request);
        return PastServiceRecordResponse.from(pastServiceRecordRepository.saveAndFlush(record));
    }

    @Transactional
    public PastServiceRecordResponse update(Long employeeId, Long id, PastServiceRecordRequest request) {
        validateDates(request);
        EmployeePastServiceRecord record = findOrThrow(employeeId, id);
        record.setOrganizationName(request.organizationName());
        record.setOrganizationType(request.organizationType());
        record.setDesignationHeld(request.designationHeld());
        record.setFromDate(request.fromDate());
        record.setToDate(request.toDate());
        applyOptionalFields(record, request);
        return PastServiceRecordResponse.from(pastServiceRecordRepository.saveAndFlush(record));
    }

    @Transactional
    public PastServiceRecordResponse verify(Long employeeId, Long id, String verifiedBy) {
        EmployeePastServiceRecord record = findOrThrow(employeeId, id);
        record.markVerified(verifiedBy);
        return PastServiceRecordResponse.from(pastServiceRecordRepository.saveAndFlush(record));
    }

    @Transactional
    public void delete(Long employeeId, Long id) {
        EmployeePastServiceRecord record = findOrThrow(employeeId, id);
        record.setDeletedAt(Instant.now());
    }

    private void validateDates(PastServiceRecordRequest request) {
        if (request.toDate().isBefore(request.fromDate())) {
            throw new MasterDataValidationException("toDate must not be before fromDate");
        }
    }

    private void applyOptionalFields(EmployeePastServiceRecord record, PastServiceRecordRequest request) {
        record.setLastPayScalePattern(request.lastPayScalePattern());
        record.setLastDrawnBasic(request.lastDrawnBasic());
        record.setLastDrawnGross(request.lastDrawnGross());
        record.setQualifyingForPensionGratuity(request.qualifyingForPensionGratuity());
        record.setQualifyingServiceOrderRef(request.qualifyingServiceOrderRef());
        record.setReasonForLeaving(request.reasonForLeaving());
        record.setExperienceCertificateS3Key(request.experienceCertificateS3Key());
        record.setRelievingNocDocumentS3Key(request.relievingNocDocumentS3Key());
    }

    private EmployeePastServiceRecord findOrThrow(Long employeeId, Long id) {
        EmployeePastServiceRecord record = pastServiceRecordRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException(ENTITY_NAME, id));
        if (!record.getEmployee().getId().equals(employeeId)) {
            throw new MasterDataNotFoundException(ENTITY_NAME, id);
        }
        return record;
    }

    private Employee resolveEmployee(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
    }
}
