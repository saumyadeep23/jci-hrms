package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.VehicleAllotmentRequest;
import in.gov.jci.hrms.dto.VehicleAllotmentResponse;
import in.gov.jci.hrms.dto.VehicleAllotmentSurrenderRequest;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeVehicleAllotment;
import in.gov.jci.hrms.entity.VehicleAllotmentStatus;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.EmployeeVehicleAllotmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Vehicle Allotment Transaction Management (Employment tab). employee.has_office_car is kept in sync
 * here (not via a DB trigger) because it needs the same "any other ACTIVE allotment?" business check
 * on surrender that create()'s one-active-vehicle-at-a-time rule needs on create - both live
 * naturally next to each other in this service rather than split across a trigger and Java code.
 */
@Service
@Transactional(readOnly = true)
public class EmployeeVehicleAllotmentService {

    private static final String ENTITY_NAME = "Vehicle Allotment";

    private final EmployeeVehicleAllotmentRepository vehicleAllotmentRepository;
    private final EmployeeRepository employeeRepository;

    public EmployeeVehicleAllotmentService(EmployeeVehicleAllotmentRepository vehicleAllotmentRepository,
                                            EmployeeRepository employeeRepository) {
        this.vehicleAllotmentRepository = vehicleAllotmentRepository;
        this.employeeRepository = employeeRepository;
    }

    public List<VehicleAllotmentResponse> listByEmployee(Long employeeId) {
        resolveEmployee(employeeId);
        return vehicleAllotmentRepository.findByEmployee_IdOrderByAllottedFromDesc(employeeId).stream()
                .map(VehicleAllotmentResponse::from)
                .toList();
    }

    @Transactional
    public VehicleAllotmentResponse create(Long employeeId, VehicleAllotmentRequest request) {
        Employee employee = resolveEmployee(employeeId);
        if (!vehicleAllotmentRepository.findByEmployee_IdAndStatus(employeeId, VehicleAllotmentStatus.ACTIVE).isEmpty()) {
            throw new BusinessRuleViolationException(
                    "Employee already has an active vehicle allotment - surrender it before allotting another");
        }

        EmployeeVehicleAllotment allotment = new EmployeeVehicleAllotment(employee, request.allotmentOrderNo(),
                request.vehicleRegNo(), request.vehicleMakeModel(), request.driverProvided(), request.personalUseAllowed(),
                request.deductionApplicable(), request.monthlyDeductionAmount(), request.allottedFrom(), request.remarks());
        VehicleAllotmentResponse saved = VehicleAllotmentResponse.from(vehicleAllotmentRepository.saveAndFlush(allotment));

        employee.setOfficeCarProvided(true);
        return saved;
    }

    @Transactional
    public VehicleAllotmentResponse surrender(Long employeeId, Long allotmentId, VehicleAllotmentSurrenderRequest request) {
        EmployeeVehicleAllotment allotment = findOrThrow(employeeId, allotmentId);
        if (request.surrenderedOn().isBefore(allotment.getAllottedFrom())) {
            throw new BusinessRuleViolationException("surrenderedOn must not be before allottedFrom");
        }

        allotment.setStatus(VehicleAllotmentStatus.SURRENDERED);
        allotment.setSurrenderedOn(request.surrenderedOn());
        if (request.remarks() != null) {
            allotment.setRemarks(request.remarks());
        }
        VehicleAllotmentResponse saved = VehicleAllotmentResponse.from(vehicleAllotmentRepository.saveAndFlush(allotment));

        boolean stillHasActiveVehicle = !vehicleAllotmentRepository
                .findByEmployee_IdAndStatus(employeeId, VehicleAllotmentStatus.ACTIVE).isEmpty();
        if (!stillHasActiveVehicle) {
            allotment.getEmployee().setOfficeCarProvided(false);
        }
        return saved;
    }

    private Employee resolveEmployee(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
    }

    private EmployeeVehicleAllotment findOrThrow(Long employeeId, Long allotmentId) {
        EmployeeVehicleAllotment allotment = vehicleAllotmentRepository.findById(allotmentId)
                .orElseThrow(() -> new MasterDataNotFoundException(ENTITY_NAME, allotmentId));
        if (!allotment.getEmployee().getId().equals(employeeId)) {
            throw new MasterDataNotFoundException(ENTITY_NAME, allotmentId);
        }
        return allotment;
    }
}
