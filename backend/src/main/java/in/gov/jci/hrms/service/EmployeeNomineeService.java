package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.NomineeRequest;
import in.gov.jci.hrms.dto.NomineeResponse;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeNominee;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
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

    private final EmployeeNomineeRepository nomineeRepository;
    private final EmployeeRepository employeeRepository;

    public EmployeeNomineeService(EmployeeNomineeRepository nomineeRepository, EmployeeRepository employeeRepository) {
        this.nomineeRepository = nomineeRepository;
        this.employeeRepository = employeeRepository;
    }

    public List<NomineeResponse> listByEmployee(Long employeeId) {
        resolveEmployee(employeeId);
        return nomineeRepository.findByEmployeeId(employeeId).stream()
                .map(NomineeResponse::from)
                .toList();
    }

    @Transactional
    public NomineeResponse create(Long employeeId, NomineeRequest request) {
        EmployeeNominee nominee = new EmployeeNominee(
                resolveEmployee(employeeId), request.name(), request.relationship(), request.sharePercentage(), request.nomineeFor());
        return NomineeResponse.from(nomineeRepository.saveAndFlush(nominee));
    }

    @Transactional
    public NomineeResponse update(Long employeeId, Long id, NomineeRequest request) {
        EmployeeNominee nominee = findOrThrow(employeeId, id);
        nominee.setName(request.name());
        nominee.setRelationship(request.relationship());
        nominee.setSharePercentage(request.sharePercentage());
        nominee.setNomineeFor(request.nomineeFor());
        return NomineeResponse.from(nomineeRepository.saveAndFlush(nominee));
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
