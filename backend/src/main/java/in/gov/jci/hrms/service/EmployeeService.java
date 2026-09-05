package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.EmployeeRequest;
import in.gov.jci.hrms.dto.EmployeeResponse;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.DepartmentalPurchaseCentre;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeEmploymentCategory;
import in.gov.jci.hrms.entity.EmployeeStatus;
import in.gov.jci.hrms.entity.EmployeeSuperannuationDetails;
import in.gov.jci.hrms.entity.EmploymentCategory;
import in.gov.jci.hrms.entity.PayScale;
import in.gov.jci.hrms.entity.PostIncumbency;
import in.gov.jci.hrms.entity.PostMaster;
import in.gov.jci.hrms.entity.RegionalOffice;
import in.gov.jci.hrms.entity.VacancyStatus;
import in.gov.jci.hrms.dto.PostIncumbencyRequest;
import in.gov.jci.hrms.entity.AssignmentType;
import in.gov.jci.hrms.exception.DuplicateEmployeeException;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.DepartmentRepository;
import in.gov.jci.hrms.repository.DepartmentalPurchaseCentreRepository;
import in.gov.jci.hrms.repository.DesignationRepository;
import in.gov.jci.hrms.repository.EmployeeEmploymentCategoryRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.EmployeeSpecification;
import in.gov.jci.hrms.repository.EmployeeSuperannuationDetailsRepository;
import in.gov.jci.hrms.repository.PayScaleRepository;
import in.gov.jci.hrms.repository.PostIncumbencyRepository;
import in.gov.jci.hrms.repository.PostMasterRepository;
import in.gov.jci.hrms.repository.RegionalOfficeRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final DesignationRepository designationRepository;
    private final RegionalOfficeRepository regionalOfficeRepository;
    private final DepartmentalPurchaseCentreRepository dpcRepository;
    private final PayScaleRepository payScaleRepository;
    private final EmployeeEmploymentCategoryRepository employmentCategoryRepository;
    private final EmployeeSuperannuationDetailsRepository superannuationDetailsRepository;
    private final EmployeeCodeGeneratorService employeeCodeGeneratorService;
    private final CpfAcNoGeneratorService cpfAcNoGeneratorService;
    private final PostMasterRepository postMasterRepository;
    private final PostIncumbencyRepository postIncumbencyRepository;
    private final PostIncumbencyService postIncumbencyService;

    public EmployeeService(EmployeeRepository employeeRepository,
                            DepartmentRepository departmentRepository,
                            DesignationRepository designationRepository,
                            RegionalOfficeRepository regionalOfficeRepository,
                            DepartmentalPurchaseCentreRepository dpcRepository,
                            PayScaleRepository payScaleRepository,
                            EmployeeEmploymentCategoryRepository employmentCategoryRepository,
                            EmployeeSuperannuationDetailsRepository superannuationDetailsRepository,
                            EmployeeCodeGeneratorService employeeCodeGeneratorService,
                            CpfAcNoGeneratorService cpfAcNoGeneratorService,
                            PostMasterRepository postMasterRepository,
                            PostIncumbencyRepository postIncumbencyRepository,
                            PostIncumbencyService postIncumbencyService) {
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.designationRepository = designationRepository;
        this.regionalOfficeRepository = regionalOfficeRepository;
        this.dpcRepository = dpcRepository;
        this.payScaleRepository = payScaleRepository;
        this.employmentCategoryRepository = employmentCategoryRepository;
        this.superannuationDetailsRepository = superannuationDetailsRepository;
        this.employeeCodeGeneratorService = employeeCodeGeneratorService;
        this.cpfAcNoGeneratorService = cpfAcNoGeneratorService;
        this.postMasterRepository = postMasterRepository;
        this.postIncumbencyRepository = postIncumbencyRepository;
        this.postIncumbencyService = postIncumbencyService;
    }

    @Transactional
    public EmployeeResponse create(EmployeeRequest request) {
        return create(request, employeeCodeGeneratorService.generateNext());
    }

    /** GET /api/employees/next-cpf-ac-no - a preview/placeholder only (see Step1Personal's use of it), never reserved - the real value is resolved fresh in create() below to avoid a stale suggestion colliding with one assigned in the meantime. */
    public String generateNextCpfAcNo() {
        return cpfAcNoGeneratorService.generateNext();
    }

    /** Used by EmployeeOnboardingService, which allocates the code once up front at draft-start time. */
    @Transactional
    public EmployeeResponse create(EmployeeRequest request, String employeeCode) {
        String cpfAcNo = request.cpfAcNo() == null || request.cpfAcNo().isBlank()
                ? cpfAcNoGeneratorService.generateNext()
                : request.cpfAcNo().trim();
        Employee employee = new Employee(
                employeeCode, request.salutation(), request.firstName(), request.lastName(), request.gender(),
                request.dateOfBirth(), request.maritalStatus(), request.panNumber(), cpfAcNo, request.personalEmail(),
                request.phone(), request.dateOfJoining(),
                resolveDepartment(request.departmentId()), resolveDesignation(request.designationId())
        );
        applyOptionalFields(employee, request);
        Employee saved = save(employee);
        syncPostAssignment(saved, request.postId());

        return EmployeeResponse.from(saved, null, null, resolveCurrentPost(saved.getId()));
    }

    public EmployeeResponse getById(Long id) {
        Employee employee = findEmployeeOrThrow(id);
        EmployeeEmploymentCategory category = employmentCategoryRepository.findByEmployeeId(id).orElse(null);
        EmployeeSuperannuationDetails superannuation = superannuationDetailsRepository.findByEmployeeId(id).orElse(null);
        return EmployeeResponse.from(employee, category != null ? category.getEmploymentCategory() : null,
                superannuation != null ? superannuation.getSuperannuationDate() : null, resolveCurrentPost(id));
    }

    /**
     * Employee Directory listing - search (employeeCode/fullName), roId/designationId/departmentId/
     * employmentType filters, and a status filter defaulting to ACTIVE (so terminated/inactive
     * employees don't clutter the directory unless explicitly asked for). employmentCategory is
     * still resolved with one batched (not per-row) query, same as before, for the Renew-vs-
     * View-Profile action gate.
     */
    public Page<EmployeeResponse> list(String search, Long roId, Long designationId, Long departmentId,
                                        EmploymentCategory employmentType, String status, Pageable pageable) {
        Specification<Employee> spec = EmployeeSpecification.filterEmployees(search, roId, designationId, departmentId, employmentType, status);
        Page<Employee> employees = employeeRepository.findAll(spec, pageable);
        List<Long> employeeIds = employees.getContent().stream().map(Employee::getId).toList();
        Map<Long, EmploymentCategory> categoryByEmployeeId = employmentCategoryRepository.findByEmployeeIdIn(employeeIds).stream()
                .collect(Collectors.toMap(cat -> cat.getEmployee().getId(), EmployeeEmploymentCategory::getEmploymentCategory));
        return employees.map(employee -> EmployeeResponse.from(employee, categoryByEmployeeId.get(employee.getId()), null));
    }

    @Transactional
    public EmployeeResponse update(Long id, EmployeeRequest request) {
        Employee employee = findEmployeeOrThrow(id);

        employee.setSalutation(request.salutation());
        employee.setFirstName(request.firstName());
        employee.setLastName(request.lastName());
        employee.setGender(request.gender());
        employee.setDateOfBirth(request.dateOfBirth());
        employee.setMaritalStatus(request.maritalStatus());
        employee.setPanNumber(request.panNumber());
        if (request.cpfAcNo() != null && !request.cpfAcNo().isBlank()) {
            employee.setCpfAcNo(request.cpfAcNo().trim());
        }
        employee.setPersonalEmail(request.personalEmail());
        employee.setPhone(request.phone());
        employee.setDateOfJoining(request.dateOfJoining());
        employee.setDepartment(resolveDepartment(request.departmentId()));
        employee.setDesignation(resolveDesignation(request.designationId()));
        applyOptionalFields(employee, request);
        Employee saved = save(employee);
        syncPostAssignment(saved, request.postId());

        return EmployeeResponse.from(saved, null, null, resolveCurrentPost(saved.getId()));
    }

    /**
     * Assigns/releases the employee's Sanctioned Post (post_master), keeping
     * vacancy_status in sync. Neither PostIncumbencyService.create()/end()
     * nor the DB (trg_sync_post_vacancy only fires for assignment_type =
     * 'REGULAR', a value post_incumbency's own CHECK constraint doesn't
     * actually permit) ever flip post_master.vacancy_status for a
     * SUBSTANTIVE incumbency - see EmployeeOnboardingService.finalizeOnboarding()
     * for the same explicit-flip workaround this mirrors. newPostId == null
     * means "release the currently held post, assign none"; a no-op if the
     * employee already holds newPostId.
     */
    @Transactional
    void syncPostAssignment(Employee employee, Long newPostId) {
        PostIncumbency currentSubstantive = postIncumbencyRepository.findByEmployeeIdAndActiveTrue(employee.getId()).stream()
                .filter(pi -> pi.getAssignmentType() == AssignmentType.SUBSTANTIVE)
                .findFirst()
                .orElse(null);
        Long currentPostId = currentSubstantive != null ? currentSubstantive.getPost().getId() : null;

        if (Objects.equals(currentPostId, newPostId)) {
            return;
        }

        if (currentSubstantive != null) {
            PostMaster oldPost = currentSubstantive.getPost();
            postIncumbencyService.end(currentSubstantive.getId(), LocalDate.now());
            oldPost.setVacancyStatus(VacancyStatus.VACANT);
            postMasterRepository.save(oldPost);
        }

        if (newPostId != null) {
            PostMaster newPost = postMasterRepository.findById(newPostId)
                    .orElseThrow(() -> new MasterDataNotFoundException("Post", newPostId));
            postIncumbencyService.create(new PostIncumbencyRequest(
                    newPost.getId(), employee.getId(), AssignmentType.SUBSTANTIVE, LocalDate.now(), null,
                    "Assigned via Edit Employee"));
            newPost.setVacancyStatus(VacancyStatus.OCCUPIED);
            postMasterRepository.save(newPost);

            employee.setDepartment(newPost.getDepartment());
            employee.setDesignation(newPost.getDesignation());
            employee.setRegionalOffice(newPost.getRegionalOffice());
            employee.setDepartmentalPurchaseCentre(newPost.getDepartmentalPurchaseCentre());
            employeeRepository.saveAndFlush(employee);
        }
    }

    private PostMaster resolveCurrentPost(Long employeeId) {
        return postIncumbencyRepository.findByEmployeeIdAndActiveTrue(employeeId).stream()
                .filter(pi -> pi.getAssignmentType() == AssignmentType.SUBSTANTIVE)
                .map(PostIncumbency::getPost)
                .findFirst()
                .orElse(null);
    }

    @Transactional
    public void delete(Long id) {
        Employee employee = findEmployeeOrThrow(id);
        employee.setStatus(EmployeeStatus.TERMINATED);
        employee.setDeletedAt(Instant.now());
    }

    /** "XXXX-XXXX-1234" - PIMS_SPEC.md Step 1 ("Aadhaar Ref (12 digits, stored masked")). */
    static String maskAadhaar(String rawTwelveDigits) {
        if (rawTwelveDigits == null) {
            return null;
        }
        return "XXXX-XXXX-" + rawTwelveDigits.substring(8);
    }

    private void applyOptionalFields(Employee employee, EmployeeRequest request) {
        employee.setMiddleName(request.middleName());
        employee.setBloodGroup(request.bloodGroup());
        employee.setNationality(request.nationality());
        employee.setMotherTongue(request.motherTongue());
        employee.setAadhaarRefNumber(maskAadhaar(request.aadhaarNumber()));
        employee.setOfficialEmail(request.officialEmail());
        employee.setOfficialMobile(request.officialMobile());
        employee.setRegionalOffice(resolveRegionalOffice(request.roId()));
        employee.setDepartmentalPurchaseCentre(resolveDpc(request.dpcId()));
        employee.setPayScale(resolvePayScale(request.payScaleId()));
        employee.setStatus(request.status());
        employee.setGeofenceExempted(request.geofenceExempted());

        boolean epsEligible = Boolean.TRUE.equals(request.isEpsEligible());
        employee.setNpsEligible(request.isNpsEligible() == null || request.isNpsEligible());
        employee.setEpsEligible(epsEligible);
        // Higher pension only ever makes sense when EPS itself applies - force-cleared rather than
        // rejected, since a client that flips EPS off isn't expected to also remember to clear this.
        employee.setEpsHigherPensionEligible(epsEligible && Boolean.TRUE.equals(request.isEpsHigherPensionEligible()));
        employee.setPranNumber(request.pranNumber());
    }

    private Department resolveDepartment(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException("Department", id));
    }

    private Designation resolveDesignation(Long id) {
        return designationRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException("Designation", id));
    }

    private RegionalOffice resolveRegionalOffice(Long id) {
        if (id == null) {
            return null;
        }
        return regionalOfficeRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException("Regional Office", id));
    }

    private DepartmentalPurchaseCentre resolveDpc(Long id) {
        if (id == null) {
            return null;
        }
        return dpcRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException("DPC", id));
    }

    private PayScale resolvePayScale(Long id) {
        if (id == null) {
            return null;
        }
        return payScaleRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException("Pay Scale", id));
    }

    private Employee findEmployeeOrThrow(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException(id));
    }

    private Employee save(Employee employee) {
        try {
            return employeeRepository.saveAndFlush(employee);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateEmployeeException(
                    "Employee code, PAN, or personal email already in use: " + employee.getEmployeeCode() + " / " + employee.getPanNumber());
        }
    }
}
