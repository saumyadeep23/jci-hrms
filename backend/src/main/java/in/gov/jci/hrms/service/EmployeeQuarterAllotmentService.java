package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.EmployeeAddressRequest;
import in.gov.jci.hrms.dto.QuarterAllotmentRequest;
import in.gov.jci.hrms.dto.QuarterAllotmentResponse;
import in.gov.jci.hrms.entity.AddressType;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeQuarterAllotment;
import in.gov.jci.hrms.entity.QuarterAllotmentStatus;
import in.gov.jci.hrms.entity.StateMaster;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.EmployeeQuarterAllotmentRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.StateMasterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Employee Company Accommodation (Onboarding/Edit Tab 9). JCI does not own residential quarters -
 * accommodation is leased or company-provided at an address. Two rules are enforced here rather than
 * at the database level:
 * <ul>
 *   <li>vacatedOn >= allottedFrom - the DB's own chk_allotment_dates constraint would catch this too,
 *       but as a raw DataIntegrityViolationException with no GlobalExceptionHandler mapping (500);
 *       checking here first gives a clear 400 instead.</li>
 *   <li>at most one OCCUPIED allotment per employee at a time - an employee occupying two
 *       company-provided addresses simultaneously makes no operational sense and would
 *       double-suppress/double-recover HRA and license fees; there is no DB constraint for this (it
 *       would need a partial unique index keyed on a non-terminal status set), so it is checked
 *       here.</li>
 * </ul>
 * When syncCurrentAddress is true, the accommodation address is also propagated to the employee's
 * PRESENT employee_addresses row via EmployeeAddressService - one-way, accommodation to employee
 * record, on every create/update, matching the task's "updating accommodation address updates the
 * employee's current residential address" rule.
 */
@Service
@Transactional(readOnly = true)
public class EmployeeQuarterAllotmentService {

    private static final String ENTITY_NAME = "Quarter Allotment";

    private final EmployeeQuarterAllotmentRepository quarterAllotmentRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeAddressService employeeAddressService;
    private final StateMasterRepository stateMasterRepository;

    public EmployeeQuarterAllotmentService(EmployeeQuarterAllotmentRepository quarterAllotmentRepository,
                                            EmployeeRepository employeeRepository,
                                            EmployeeAddressService employeeAddressService,
                                            StateMasterRepository stateMasterRepository) {
        this.quarterAllotmentRepository = quarterAllotmentRepository;
        this.employeeRepository = employeeRepository;
        this.employeeAddressService = employeeAddressService;
        this.stateMasterRepository = stateMasterRepository;
    }

    public List<QuarterAllotmentResponse> listByEmployee(Long employeeId) {
        resolveEmployee(employeeId);
        return quarterAllotmentRepository.findByEmployee_IdOrderByAllottedFromDesc(employeeId).stream()
                .map(QuarterAllotmentResponse::from)
                .toList();
    }

    /** True if the employee has an OCCUPIED accommodation allotment covering asOfDate - HRA (Head 9) is suppressed to zero for that period. */
    public boolean isHraSuppressed(Long employeeId, LocalDate asOfDate) {
        return findActiveOccupancy(employeeId, asOfDate, asOfDate).isPresent();
    }

    /**
     * Period-aware overload for the Payroll Computation Engine - true if an OCCUPIED allotment
     * overlaps any day of [periodStart, periodEnd] (a payroll month), not just a single date.
     */
    public boolean isHraSuppressed(Long employeeId, LocalDate periodStart, LocalDate periodEnd) {
        return findActiveOccupancy(employeeId, periodStart, periodEnd).isPresent();
    }

    /** The OCCUPIED allotment (if any) overlapping [periodStart, periodEnd] - also how the engine reads license fee/water/electric recovery amounts for that period. */
    public Optional<EmployeeQuarterAllotment> findActiveOccupancy(Long employeeId, LocalDate periodStart, LocalDate periodEnd) {
        return quarterAllotmentRepository.findByEmployee_IdAndStatus(employeeId, QuarterAllotmentStatus.OCCUPIED).stream()
                .filter(allotment -> !allotment.getAllottedFrom().isAfter(periodEnd)
                        && (allotment.getVacatedOn() == null || !allotment.getVacatedOn().isBefore(periodStart)))
                .findFirst();
    }

    @Transactional
    public QuarterAllotmentResponse create(Long employeeId, QuarterAllotmentRequest request) {
        validateDateOrder(request);
        Employee employee = resolveEmployee(employeeId);
        if (request.status() == QuarterAllotmentStatus.OCCUPIED) {
            validateNoOtherOccupiedAllotment(employeeId, null);
        }

        EmployeeQuarterAllotment allotment = new EmployeeQuarterAllotment(employee, request.allotmentOrderNo(),
                request.addressLine1(), request.addressLine2(), request.city(), request.stateCode(), request.pincode(),
                request.syncCurrentAddress(), request.licenseFee(), request.waterCharges(), request.electricCharges(),
                request.allottedFrom(), request.remarks());
        allotment.setVacatedOn(request.vacatedOn());
        allotment.setStatus(request.status());
        QuarterAllotmentResponse saved = QuarterAllotmentResponse.from(quarterAllotmentRepository.saveAndFlush(allotment));

        syncEmployeeAddressIfRequested(employeeId, request);
        return saved;
    }

    @Transactional
    public QuarterAllotmentResponse update(Long employeeId, Long allotmentId, QuarterAllotmentRequest request) {
        validateDateOrder(request);
        EmployeeQuarterAllotment allotment = findOrThrow(employeeId, allotmentId);
        if (request.status() == QuarterAllotmentStatus.OCCUPIED) {
            validateNoOtherOccupiedAllotment(employeeId, allotmentId);
        }

        allotment.setAllotmentOrderNo(request.allotmentOrderNo());
        allotment.setAddressLine1(request.addressLine1());
        allotment.setAddressLine2(request.addressLine2());
        allotment.setCity(request.city());
        allotment.setStateCode(request.stateCode());
        allotment.setPincode(request.pincode());
        allotment.setSyncCurrentAddress(request.syncCurrentAddress());
        allotment.setLicenseFee(request.licenseFee());
        allotment.setWaterCharges(request.waterCharges());
        allotment.setElectricCharges(request.electricCharges());
        allotment.setAllottedFrom(request.allottedFrom());
        allotment.setVacatedOn(request.vacatedOn());
        allotment.setStatus(request.status());
        allotment.setRemarks(request.remarks());
        QuarterAllotmentResponse saved = QuarterAllotmentResponse.from(quarterAllotmentRepository.saveAndFlush(allotment));

        syncEmployeeAddressIfRequested(employeeId, request);
        return saved;
    }

    /**
     * employee_addresses.state is free-text (unlike this table's state_code FK to state_master), so
     * the code is resolved to its full name before syncing; district/postOffice/policeStation have no
     * accommodation-form equivalent and are synced blank.
     */
    private void syncEmployeeAddressIfRequested(Long employeeId, QuarterAllotmentRequest request) {
        if (!request.syncCurrentAddress()) {
            return;
        }
        String stateName = stateMasterRepository.findByStateCode(request.stateCode())
                .map(StateMaster::getStateName)
                .orElse(request.stateCode());
        EmployeeAddressRequest addressRequest = new EmployeeAddressRequest(AddressType.PRESENT, request.addressLine1(),
                request.addressLine2(), null, null, request.city(), request.city(), stateName, request.pincode());
        employeeAddressService.upsert(employeeId, addressRequest);
    }

    private void validateDateOrder(QuarterAllotmentRequest request) {
        if (request.vacatedOn() != null && request.vacatedOn().isBefore(request.allottedFrom())) {
            throw new BusinessRuleViolationException("vacatedOn must not be before allottedFrom");
        }
    }

    private void validateNoOtherOccupiedAllotment(Long employeeId, Long excludingAllotmentId) {
        boolean alreadyOccupied = quarterAllotmentRepository.findByEmployee_IdAndStatus(employeeId, QuarterAllotmentStatus.OCCUPIED)
                .stream()
                .anyMatch(existing -> !existing.getId().equals(excludingAllotmentId));
        if (alreadyOccupied) {
            throw new BusinessRuleViolationException("Employee already has an active (OCCUPIED) accommodation allotment - vacate or surrender it first");
        }
    }

    private Employee resolveEmployee(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
    }

    private EmployeeQuarterAllotment findOrThrow(Long employeeId, Long allotmentId) {
        EmployeeQuarterAllotment allotment = quarterAllotmentRepository.findById(allotmentId)
                .orElseThrow(() -> new MasterDataNotFoundException(ENTITY_NAME, allotmentId));
        if (!allotment.getEmployee().getId().equals(employeeId)) {
            throw new MasterDataNotFoundException(ENTITY_NAME, allotmentId);
        }
        return allotment;
    }
}
