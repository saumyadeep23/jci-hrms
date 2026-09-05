package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.ServiceBookEventRequest;
import in.gov.jci.hrms.dto.ServiceBookEventResponse;
import in.gov.jci.hrms.entity.CareerEventType;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeEmploymentCategory;
import in.gov.jci.hrms.entity.EmployeeServiceBook;
import in.gov.jci.hrms.entity.PayScale;
import in.gov.jci.hrms.entity.RegionalOffice;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.DepartmentRepository;
import in.gov.jci.hrms.repository.DesignationRepository;
import in.gov.jci.hrms.repository.EmployeeEmploymentCategoryRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.EmployeeServiceBookRepository;
import in.gov.jci.hrms.repository.PayScaleRepository;
import in.gov.jci.hrms.repository.RegionalOfficeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/** PIMS_SPEC.md operational-features task, Section 3: the Digital Service Book Career Event Logger. */
@Service
@Transactional(readOnly = true)
public class EmployeeServiceBookService {

    private static final Set<CareerEventType> BASIC_PAY_EVENTS =
            Set.of(CareerEventType.PROMOTION, CareerEventType.TRANSFER, CareerEventType.PAY_FIXATION);

    private final EmployeeServiceBookRepository serviceBookRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeEmploymentCategoryRepository employmentCategoryRepository;
    private final DepartmentRepository departmentRepository;
    private final DesignationRepository designationRepository;
    private final RegionalOfficeRepository regionalOfficeRepository;
    private final PayScaleRepository payScaleRepository;

    public EmployeeServiceBookService(EmployeeServiceBookRepository serviceBookRepository, EmployeeRepository employeeRepository,
                                       EmployeeEmploymentCategoryRepository employmentCategoryRepository,
                                       DepartmentRepository departmentRepository, DesignationRepository designationRepository,
                                       RegionalOfficeRepository regionalOfficeRepository, PayScaleRepository payScaleRepository) {
        this.serviceBookRepository = serviceBookRepository;
        this.employeeRepository = employeeRepository;
        this.employmentCategoryRepository = employmentCategoryRepository;
        this.departmentRepository = departmentRepository;
        this.designationRepository = designationRepository;
        this.regionalOfficeRepository = regionalOfficeRepository;
        this.payScaleRepository = payScaleRepository;
    }

    public List<ServiceBookEventResponse> timeline(Long employeeId) {
        if (!employeeRepository.existsById(employeeId)) {
            throw new EmployeeNotFoundException(employeeId);
        }
        return serviceBookRepository.findByEmployeeIdOrderByEventDateAscIdAsc(employeeId).stream()
                .map(ServiceBookEventResponse::from)
                .toList();
    }

    @Transactional
    public ServiceBookEventResponse recordEvent(Long employeeId, ServiceBookEventRequest request) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));

        EmployeeServiceBook event = new EmployeeServiceBook(employee, request.eventDate(), request.eventType().name());
        event.setOrderNumber(request.orderNumber());
        event.setOrderDate(request.orderDate());
        event.setDepartment(resolveDepartment(request.departmentId()));
        event.setDesignation(resolveDesignation(request.designationId()));
        event.setRegionalOffice(resolveRegionalOffice(request.regionalOfficeId()));
        event.setPayScale(resolvePayScale(request.payScaleId()));
        event.setBasicPay(request.basicPay());
        event.setRemarks(request.remarks());
        event.setMigrated(false);

        if (BASIC_PAY_EVENTS.contains(request.eventType()) && request.basicPay() != null) {
            employmentCategoryRepository.findByEmployeeId(employeeId)
                    .ifPresent(category -> applyBasicPay(category, request.basicPay()));
        }

        return ServiceBookEventResponse.from(serviceBookRepository.save(event));
    }

    private void applyBasicPay(EmployeeEmploymentCategory category, java.math.BigDecimal basicPay) {
        category.setRegularBasicPay(basicPay);
    }

    private Department resolveDepartment(Long id) {
        if (id == null) return null;
        return departmentRepository.findById(id).orElseThrow(() -> new MasterDataNotFoundException("Department", id));
    }

    private Designation resolveDesignation(Long id) {
        if (id == null) return null;
        return designationRepository.findById(id).orElseThrow(() -> new MasterDataNotFoundException("Designation", id));
    }

    private RegionalOffice resolveRegionalOffice(Long id) {
        if (id == null) return null;
        return regionalOfficeRepository.findById(id).orElseThrow(() -> new MasterDataNotFoundException("Regional Office", id));
    }

    private PayScale resolvePayScale(Long id) {
        if (id == null) return null;
        return payScaleRepository.findById(id).orElseThrow(() -> new MasterDataNotFoundException("Pay Scale", id));
    }
}
