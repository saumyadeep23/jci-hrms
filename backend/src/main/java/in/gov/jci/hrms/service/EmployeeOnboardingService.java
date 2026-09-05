package in.gov.jci.hrms.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.type.CollectionType;
import in.gov.jci.hrms.dto.EmployeeAddressRequest;
import in.gov.jci.hrms.dto.EmployeeBankAccountRequest;
import in.gov.jci.hrms.dto.EmployeeRequest;
import in.gov.jci.hrms.dto.EmployeeResponse;
import in.gov.jci.hrms.dto.OnboardingDependentEntry;
import in.gov.jci.hrms.dto.OnboardingDocumentEntry;
import in.gov.jci.hrms.dto.OnboardingDraftResponse;
import in.gov.jci.hrms.dto.OnboardingDraftUpsertRequest;
import in.gov.jci.hrms.dto.OnboardingEmploymentStepRequest;
import in.gov.jci.hrms.dto.OnboardingFamilyStepRequest;
import in.gov.jci.hrms.dto.OnboardingNomineeEntry;
import in.gov.jci.hrms.dto.OnboardingPersonalDetailsRequest;
import in.gov.jci.hrms.dto.OnboardingSocialProfileRequest;
import in.gov.jci.hrms.dto.OnboardingSubmitResponse;
import in.gov.jci.hrms.dto.OutsourcedSalaryBreakdownEntry;
import in.gov.jci.hrms.dto.PastServiceRecordRequest;
import in.gov.jci.hrms.dto.PostIncumbencyRequest;
import in.gov.jci.hrms.dto.QualificationRequest;
import in.gov.jci.hrms.entity.AddressType;
import in.gov.jci.hrms.entity.AssignmentType;
import in.gov.jci.hrms.entity.ContractualEngagement;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeAddress;
import in.gov.jci.hrms.entity.EmployeeBankAccount;
import in.gov.jci.hrms.entity.EmployeeDependent;
import in.gov.jci.hrms.entity.EmployeeDocument;
import in.gov.jci.hrms.entity.EmployeeEmploymentCategory;
import in.gov.jci.hrms.entity.EmployeeFamilyDetails;
import in.gov.jci.hrms.entity.EmployeeNominee;
import in.gov.jci.hrms.entity.EmployeeOnboardingDraft;
import in.gov.jci.hrms.entity.EmployeePastServiceRecord;
import in.gov.jci.hrms.entity.EmployeeQualification;
import in.gov.jci.hrms.entity.EmployeeRecruitmentDetails;
import in.gov.jci.hrms.entity.EmployeeSocialProfile;
import in.gov.jci.hrms.entity.EmployeeStatus;
import in.gov.jci.hrms.entity.EmploymentCategory;
import in.gov.jci.hrms.entity.FixationReason;
import in.gov.jci.hrms.entity.GradeScaleMaster;
import in.gov.jci.hrms.entity.IncrementCycle;
import in.gov.jci.hrms.entity.OnboardingStatus;
import in.gov.jci.hrms.entity.OutsourcedDeployment;
import in.gov.jci.hrms.entity.OutsourcedSalaryBreakdownItem;
import in.gov.jci.hrms.entity.PayScale;
import in.gov.jci.hrms.entity.PostMaster;
import in.gov.jci.hrms.entity.RegularPayFixation;
import in.gov.jci.hrms.entity.VacancyStatus;
import in.gov.jci.hrms.entity.VendorMaster;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.exception.OnboardingDraftNotFoundException;
import in.gov.jci.hrms.repository.ContractualEngagementRepository;
import in.gov.jci.hrms.repository.DepartmentRepository;
import in.gov.jci.hrms.repository.DesignationRepository;
import in.gov.jci.hrms.repository.EmployeeAddressRepository;
import in.gov.jci.hrms.repository.EmployeeBankAccountRepository;
import in.gov.jci.hrms.repository.EmployeeDependentRepository;
import in.gov.jci.hrms.repository.EmployeeDocumentRepository;
import in.gov.jci.hrms.repository.EmployeeEmploymentCategoryRepository;
import in.gov.jci.hrms.repository.EmployeeFamilyDetailsRepository;
import in.gov.jci.hrms.repository.EmployeeNomineeRepository;
import in.gov.jci.hrms.repository.EmployeeOnboardingDraftRepository;
import in.gov.jci.hrms.repository.EmployeePastServiceRecordRepository;
import in.gov.jci.hrms.repository.EmployeeQualificationRepository;
import in.gov.jci.hrms.repository.EmployeeRecruitmentDetailsRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.EmployeeSocialProfileRepository;
import in.gov.jci.hrms.repository.GradeScaleMasterRepository;
import in.gov.jci.hrms.repository.OutsourcedDeploymentRepository;
import in.gov.jci.hrms.repository.OutsourcedSalaryBreakdownItemRepository;
import in.gov.jci.hrms.repository.PayScaleRepository;
import in.gov.jci.hrms.repository.PostMasterRepository;
import in.gov.jci.hrms.repository.RegularPayFixationRepository;
import in.gov.jci.hrms.repository.VendorMasterRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Backs the 8-step Employee Onboarding & Draft Saving workflow -
 * PIMS_SPEC.md Features 3 & 4:
 *   1 Personal & Bio-Data, 2 Address, 3 Banking, 4 Qualifications,
 *   5 Past Service, 6 Employment Category & Post Assignment,
 *   7 Family/Dependents/Nominees, 8 Documents & Final Review.
 *
 * One flexible endpoint (upsert()) persists however much of the form is
 * filled in - "Save as Draft" bypasses mandatory validation entirely, only
 * finalize() enforces the full schema. Each of the 8 data-bearing groups
 * (documents included, review has none of its own) is stored as one key of
 * the draft's step_payloads JSON document, merged field-by-field on every
 * upsert so an update never clobbers a step that wasn't part of this
 * particular request.
 */
@Service
@Transactional(readOnly = true)
public class EmployeeOnboardingService {

    private static final int TOTAL_STEPS = 8;
    private static final BigDecimal FULL_SHARE = new BigDecimal("100");

    private final EmployeeOnboardingDraftRepository draftRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeQualificationRepository qualificationRepository;
    private final EmployeePastServiceRecordRepository pastServiceRecordRepository;
    private final EmployeeNomineeRepository nomineeRepository;
    private final EmployeeDependentRepository dependentRepository;
    private final EmployeeAddressRepository addressRepository;
    private final EmployeeBankAccountRepository bankAccountRepository;
    private final EmployeeEmploymentCategoryRepository employmentCategoryRepository;
    private final OutsourcedSalaryBreakdownItemRepository salaryBreakdownRepository;
    private final EmployeeFamilyDetailsRepository familyDetailsRepository;
    private final EmployeeRecruitmentDetailsRepository recruitmentDetailsRepository;
    private final EmployeeDocumentRepository documentRepository;
    private final EmployeeSocialProfileRepository socialProfileRepository;
    private final PostMasterRepository postMasterRepository;
    private final DepartmentRepository departmentRepository;
    private final DesignationRepository designationRepository;
    private final VendorMasterRepository vendorMasterRepository;
    private final PayScaleRepository payScaleRepository;
    private final GradeScaleMasterRepository gradeScaleMasterRepository;
    private final RegularPayFixationRepository regularPayFixationRepository;
    private final ContractualEngagementRepository contractualEngagementRepository;
    private final OutsourcedDeploymentRepository outsourcedDeploymentRepository;
    private final EmployeeCodeGeneratorService employeeCodeGeneratorService;
    private final EmployeeService employeeService;
    private final PostIncumbencyService postIncumbencyService;
    private final Validator validator;
    private final ObjectMapper objectMapper;

    public EmployeeOnboardingService(EmployeeOnboardingDraftRepository draftRepository,
                                      EmployeeRepository employeeRepository,
                                      EmployeeQualificationRepository qualificationRepository,
                                      EmployeePastServiceRecordRepository pastServiceRecordRepository,
                                      EmployeeNomineeRepository nomineeRepository,
                                      EmployeeDependentRepository dependentRepository,
                                      EmployeeAddressRepository addressRepository,
                                      EmployeeBankAccountRepository bankAccountRepository,
                                      EmployeeEmploymentCategoryRepository employmentCategoryRepository,
                                      OutsourcedSalaryBreakdownItemRepository salaryBreakdownRepository,
                                      EmployeeFamilyDetailsRepository familyDetailsRepository,
                                      EmployeeRecruitmentDetailsRepository recruitmentDetailsRepository,
                                      EmployeeDocumentRepository documentRepository,
                                      EmployeeSocialProfileRepository socialProfileRepository,
                                      PostMasterRepository postMasterRepository,
                                      DepartmentRepository departmentRepository,
                                      DesignationRepository designationRepository,
                                      VendorMasterRepository vendorMasterRepository,
                                      PayScaleRepository payScaleRepository,
                                      GradeScaleMasterRepository gradeScaleMasterRepository,
                                      RegularPayFixationRepository regularPayFixationRepository,
                                      ContractualEngagementRepository contractualEngagementRepository,
                                      OutsourcedDeploymentRepository outsourcedDeploymentRepository,
                                      EmployeeCodeGeneratorService employeeCodeGeneratorService,
                                      EmployeeService employeeService,
                                      PostIncumbencyService postIncumbencyService,
                                      Validator validator,
                                      ObjectMapper objectMapper) {
        this.draftRepository = draftRepository;
        this.employeeRepository = employeeRepository;
        this.qualificationRepository = qualificationRepository;
        this.pastServiceRecordRepository = pastServiceRecordRepository;
        this.nomineeRepository = nomineeRepository;
        this.dependentRepository = dependentRepository;
        this.addressRepository = addressRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.employmentCategoryRepository = employmentCategoryRepository;
        this.salaryBreakdownRepository = salaryBreakdownRepository;
        this.familyDetailsRepository = familyDetailsRepository;
        this.recruitmentDetailsRepository = recruitmentDetailsRepository;
        this.documentRepository = documentRepository;
        this.socialProfileRepository = socialProfileRepository;
        this.postMasterRepository = postMasterRepository;
        this.departmentRepository = departmentRepository;
        this.designationRepository = designationRepository;
        this.vendorMasterRepository = vendorMasterRepository;
        this.payScaleRepository = payScaleRepository;
        this.gradeScaleMasterRepository = gradeScaleMasterRepository;
        this.regularPayFixationRepository = regularPayFixationRepository;
        this.contractualEngagementRepository = contractualEngagementRepository;
        this.outsourcedDeploymentRepository = outsourcedDeploymentRepository;
        this.employeeCodeGeneratorService = employeeCodeGeneratorService;
        this.employeeService = employeeService;
        this.postIncumbencyService = postIncumbencyService;
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    /** POST /api/v1/onboarding/draft - creates a new draft (draftId null) or updates an existing one. */
    @Transactional
    public OnboardingDraftResponse upsert(OnboardingDraftUpsertRequest request, String actingUser) {
        EmployeeOnboardingDraft draft = request.draftId() == null
                ? startDraft(actingUser)
                : findInProgressOrThrow(request.draftId());

        ObjectNode root = (ObjectNode) readTree(draft.getStepPayloadsJson());
        mergeField(root, "personal", request.personal());
        mergeField(root, "presentAddress", request.presentAddress());
        mergeField(root, "permanentAddress", request.permanentAddress());
        mergeField(root, "permanentSameAsPresent", request.permanentSameAsPresent());
        mergeField(root, "banking", request.banking());
        mergeField(root, "qualifications", request.qualifications());
        mergeField(root, "pastServiceRecords", request.pastServiceRecords());
        mergeField(root, "employment", request.employment());
        mergeField(root, "family", request.family());
        mergeField(root, "documents", request.documents());
        mergeField(root, "socialProfile", request.socialProfile());
        draft.setStepPayloadsJson(writeTree(root));

        if (request.currentStep() != null) {
            draft.setCurrentStep(Math.max(1, Math.min(request.currentStep(), TOTAL_STEPS)));
        }

        return toResponse(draft);
    }

    public OnboardingDraftResponse getById(Long id) {
        return toResponse(findOrThrow(id));
    }

    /** GET /api/v1/onboarding/drafts - incomplete drafts with completion percentage. */
    public Page<OnboardingDraftResponse> list(OnboardingStatus status, Pageable pageable) {
        Page<EmployeeOnboardingDraft> page = status != null
                ? draftRepository.findByStatus(status, pageable)
                : draftRepository.findAll(pageable);
        return page.map(this::toResponse);
    }

    /** DELETE /api/v1/onboarding/drafts/{id} - permanently removes an incomplete (IN_PROGRESS) draft; a SUBMITTED draft no longer exists to delete (finalizeOnboarding deletes it itself on success). */
    @Transactional
    public void deleteDraft(Long draftId) {
        EmployeeOnboardingDraft draft = findInProgressOrThrow(draftId);
        draftRepository.delete(draft);
    }

    /** POST /api/v1/onboarding/drafts/:id/finalize - full-schema validation, then activation. */
    @Transactional
    public OnboardingSubmitResponse finalizeOnboarding(Long draftId) {
        EmployeeOnboardingDraft draft = findInProgressOrThrow(draftId);
        JsonNode root = readTree(draft.getStepPayloadsJson());

        OnboardingPersonalDetailsRequest personal = readGroup(root, "personal", OnboardingPersonalDetailsRequest.class);
        EmployeeAddressRequest presentAddress = readGroup(root, "presentAddress", EmployeeAddressRequest.class);
        EmployeeAddressRequest permanentAddress = readGroup(root, "permanentAddress", EmployeeAddressRequest.class);
        Boolean permanentSameAsPresent = readGroup(root, "permanentSameAsPresent", Boolean.class);
        EmployeeBankAccountRequest banking = readGroup(root, "banking", EmployeeBankAccountRequest.class);
        List<QualificationRequest> qualifications = readListGroup(root, "qualifications", QualificationRequest.class);
        List<PastServiceRecordRequest> pastServiceRecords = readListGroup(root, "pastServiceRecords", PastServiceRecordRequest.class);
        OnboardingEmploymentStepRequest employment = readGroup(root, "employment", OnboardingEmploymentStepRequest.class);
        OnboardingFamilyStepRequest family = readGroup(root, "family", OnboardingFamilyStepRequest.class);
        List<OnboardingDocumentEntry> documents = readListGroup(root, "documents", OnboardingDocumentEntry.class);
        OnboardingSocialProfileRequest socialProfile = readGroup(root, "socialProfile", OnboardingSocialProfileRequest.class);

        List<String> errors = new ArrayList<>();
        requireAndValidate("personal", personal, errors);
        requireAndValidate("presentAddress", presentAddress, errors);
        if (!Boolean.TRUE.equals(permanentSameAsPresent)) {
            requireAndValidate("permanentAddress", permanentAddress, errors);
        }
        requireAndValidate("banking", banking, errors);
        requireAndValidate("employment", employment, errors);
        requireAndValidate("family", family, errors);
        normalizeList(qualifications).forEach(q -> collectViolations("qualifications", q, errors));
        normalizeList(pastServiceRecords).forEach(p -> collectViolations("pastServiceRecords", p, errors));
        if (documents == null || documents.isEmpty()) {
            errors.add("documents: at least one document (Photo, Signature, PAN Card, Aadhaar, Appointment Letter) is required");
        } else {
            documents.forEach(d -> collectViolations("documents", d, errors));
        }
        validateEmploymentCategorySpecifics(employment, errors);
        validateNomineeShares(family, errors);
        if (socialProfile != null) {
            collectViolations("socialProfile", socialProfile, errors);
        }

        if (!errors.isEmpty()) {
            throw new BusinessRuleViolationException(
                    "Cannot finalize draft " + draft.getDraftCode() + ": " + String.join("; ", errors));
        }

        EmployeeAddressRequest effectivePermanentAddress = Boolean.TRUE.equals(permanentSameAsPresent) ? presentAddress : permanentAddress;

        Department department;
        Designation designation;
        PostMaster regularPost = null;
        if (employment.employmentCategory() == EmploymentCategory.REGULAR) {
            regularPost = postMasterRepository.findById(employment.postId())
                    .orElseThrow(() -> new MasterDataNotFoundException("Post", employment.postId()));
            if (regularPost.getVacancyStatus() != VacancyStatus.VACANT || !regularPost.isBudgeted()) {
                throw new BusinessRuleViolationException(
                        "Post " + regularPost.getPostCode() + " is not a vacant, budgeted post available for a REGULAR assignment");
            }
            department = regularPost.getDepartment();
            designation = regularPost.getDesignation();
        } else {
            department = departmentRepository.findById(employment.departmentId())
                    .orElseThrow(() -> new MasterDataNotFoundException("Department", employment.departmentId()));
            designation = designationRepository.findById(employment.designationId())
                    .orElseThrow(() -> new MasterDataNotFoundException("Designation", employment.designationId()));
        }

        EmployeeRequest employeeRequest = new EmployeeRequest(
                personal.salutation(), personal.firstName(), personal.middleName(), personal.lastName(),
                personal.gender(), personal.dateOfBirth(), personal.maritalStatus(), personal.bloodGroup(),
                personal.nationality(), personal.motherTongue(), personal.panNumber(), personal.cpfAcNo(), personal.aadhaarNumber(),
                personal.personalEmail(), personal.officialEmail(), personal.phone(), personal.officialMobile(),
                employment.dateOfJoiningPsu(), department.getId(), designation.getId(),
                null, null, employment.employmentCategory() == EmploymentCategory.REGULAR ? employment.payScaleId() : null,
                EmployeeStatus.ACTIVE, false, null,
                employment.isNpsEligible(), employment.isEpsEligible(), employment.isEpsHigherPensionEligible(), employment.pranNumber()
        );
        EmployeeResponse createdEmployee = employeeService.create(employeeRequest, draft.getEmployeeCode());
        Employee employee = employeeRepository.getReferenceById(createdEmployee.id());

        saveAddress(employee, AddressType.PRESENT, presentAddress);
        saveAddress(employee, AddressType.PERMANENT, effectivePermanentAddress);

        EmployeeBankAccount bankAccount = new EmployeeBankAccount(
                employee, banking.bankName(), banking.bankBranch(), banking.bankAccountNumber(), banking.bankIfsc());
        bankAccount.setCancelledChequeS3Key(banking.cancelledChequeS3Key());
        bankAccountRepository.save(bankAccount);

        normalizeList(qualifications).forEach(entry -> saveQualification(employee, entry));
        normalizeList(pastServiceRecords).forEach(entry -> savePastService(employee, entry));

        saveEmploymentCategory(employee, employment);
        saveCompensationLedgerEntry(employee, employment);
        recruitmentDetailsRepository.save(buildRecruitmentDetails(employee, employment));

        if (regularPost != null) {
            postIncumbencyService.create(new PostIncumbencyRequest(
                    regularPost.getId(), employee.getId(), AssignmentType.SUBSTANTIVE,
                    employment.dateOfJoiningPsu(), null, employment.appointmentLetterNo()));
            // fn_sync_post_vacancy_and_budget only auto-flips vacancy_status for
            // assignment_type = 'REGULAR', which post_incumbency's own CHECK
            // constraint doesn't actually allow - SUBSTANTIVE is this codebase's
            // existing "primary occupant" type (see PostIncumbencyService), so
            // the flip is done explicitly here rather than relying on that trigger.
            regularPost.setVacancyStatus(VacancyStatus.OCCUPIED);
        }

        EmployeeFamilyDetails familyDetails = new EmployeeFamilyDetails(employee, family.fatherName());
        familyDetails.setMotherName(family.motherName());
        familyDetails.setSpouseName(family.spouseName());
        familyDetails.setSpouseDob(family.spouseDob());
        familyDetails.setDependentCount(normalizeList(family.dependents()).size());
        familyDetailsRepository.save(familyDetails);

        normalizeList(family.dependents()).forEach(entry -> saveDependent(employee, entry));
        normalizeList(family.nominees()).forEach(entry -> saveNominee(employee, entry));
        documents.forEach(entry -> saveDocument(employee, entry));
        if (socialProfile != null) {
            saveSocialProfile(employee, socialProfile);
        }

        // The draft's own record is transient scratch input, not a system of record - the employee
        // row plus everything just saved above from it (address, bank account, employment category/
        // compensation ledger, recruitment details, family, documents) is what onboarding was for,
        // so once that's committed the draft (and its step_payloads JSON) is deleted outright rather
        // than kept around as a SUBMITTED-status row.
        Long deletedDraftId = draft.getId();
        String draftCode = draft.getDraftCode();
        draftRepository.delete(draft);

        return new OnboardingSubmitResponse(deletedDraftId, draftCode, createdEmployee.id(), createdEmployee.employeeCode());
    }

    private EmployeeOnboardingDraft startDraft(String initiatedBy) {
        String employeeCode = employeeCodeGeneratorService.generateNext();
        String draftCode = "OB-%d-%06d".formatted(Year.now().getValue(), draftRepository.nextDraftCodeSequence());
        return draftRepository.saveAndFlush(new EmployeeOnboardingDraft(draftCode, employeeCode, initiatedBy));
    }

    private void saveAddress(Employee employee, AddressType type, EmployeeAddressRequest request) {
        EmployeeAddress address = new EmployeeAddress(
                employee, type, request.addressLine1(), request.city(), request.district(), request.state(), request.pinCode());
        address.setAddressLine2(request.addressLine2());
        address.setPostOffice(request.postOffice());
        address.setPoliceStation(request.policeStation());
        addressRepository.save(address);
    }

    private void saveQualification(Employee employee, QualificationRequest entry) {
        EmployeeQualification qualification = new EmployeeQualification(
                employee, entry.qualificationLevel(), entry.degreeTitle(), entry.boardUniversity(), entry.passingYear());
        qualification.setSpecialization(entry.specialization());
        qualification.setInstitutionName(entry.institutionName());
        qualification.setPercentageCgpa(entry.percentageCgpa());
        qualification.setDivisionClass(entry.divisionClass());
        qualification.setCourseType(entry.courseType());
        qualification.setHighestQualification(entry.highestQualification());
        qualification.setCertificateDocumentS3Key(entry.certificateDocumentS3Key());
        qualificationRepository.save(qualification);
    }

    private void savePastService(Employee employee, PastServiceRecordRequest entry) {
        EmployeePastServiceRecord record = new EmployeePastServiceRecord(
                employee, entry.organizationName(), entry.organizationType(), entry.designationHeld(),
                entry.fromDate(), entry.toDate());
        record.setLastPayScalePattern(entry.lastPayScalePattern());
        record.setLastDrawnBasic(entry.lastDrawnBasic());
        record.setLastDrawnGross(entry.lastDrawnGross());
        record.setQualifyingForPensionGratuity(entry.qualifyingForPensionGratuity());
        record.setQualifyingServiceOrderRef(entry.qualifyingServiceOrderRef());
        record.setReasonForLeaving(entry.reasonForLeaving());
        record.setExperienceCertificateS3Key(entry.experienceCertificateS3Key());
        record.setRelievingNocDocumentS3Key(entry.relievingNocDocumentS3Key());
        pastServiceRecordRepository.save(record);
    }

    private void saveEmploymentCategory(Employee employee, OnboardingEmploymentStepRequest employment) {
        EmployeeEmploymentCategory category = new EmployeeEmploymentCategory(employee, employment.employmentCategory());
        switch (employment.employmentCategory()) {
            case REGULAR -> {
                // payScaleId (legacy pay_scale_master) is no longer collected by the onboarding
                // wizard - REGULAR now carries pay/grade purely via scaleCode below. Kept optional
                // here rather than removed outright so any caller that still supplies it (or a
                // resumed pre-existing draft) is not rejected.
                if (employment.payScaleId() != null) {
                    category.setPayScale(resolvePayScale(employment.payScaleId()));
                }
                category.setRegularBasicPay(employment.regularBasicPay());
            }
            case CASUAL -> {
                category.setDailyWageRate(employment.dailyWageRate());
                category.setWageRevisionOrderNo(employment.wageRevisionOrderNo());
            }
            case CONTRACTUAL -> {
                category.setFixedLumpSumMonthly(employment.fixedLumpSumMonthly());
                category.setContractStartDate(employment.contractStartDate());
                category.setContractEndDate(employment.contractEndDate());
                category.setContractRefOrder(employment.contractRefOrder());
            }
            case OUTSOURCED -> {
                VendorMaster vendor = vendorMasterRepository.findById(employment.vendorId())
                        .orElseThrow(() -> new MasterDataNotFoundException("Vendor", employment.vendorId()));
                category.setVendor(vendor);
                category.setMonthlyCtc(employment.monthlyCtc());
                category.setBillingRateMonthly(employment.billingRateMonthly());
                category.setAgencyEmployeeId(employment.agencyEmployeeId());
                category.setContractStartDate(employment.contractStartDate());
                category.setContractEndDate(employment.contractEndDate());
            }
        }
        if (employment.scaleCode() != null) {
            category.setGradeScale(resolveGradeScale(employment.scaleCode()));
        }
        employmentCategoryRepository.save(category);

        if (employment.employmentCategory() == EmploymentCategory.OUTSOURCED) {
            normalizeList(employment.ctcBreakdown()).forEach(item -> saveSalaryBreakdownItem(employee, item));
        }
    }

    /**
     * V50's historized ledger (regular_pay_fixations / contractual_engagements / outsourced_deployments)
     * - additive alongside saveEmploymentCategory's employee_employment_categories row (which keeps
     * backing payroll/LPC unchanged). CASUAL has no ledger table - daily-wage employees aren't on a
     * grade-scale/contract-term footing.
     */
    private void saveCompensationLedgerEntry(Employee employee, OnboardingEmploymentStepRequest employment) {
        switch (employment.employmentCategory()) {
            case REGULAR -> {
                RegularPayFixation fixation = new RegularPayFixation(
                        employee, resolveGradeScale(employment.scaleCode()), employment.regularBasicPay(), employment.dateOfJoiningPsu());
                fixation.setFixationReason(FixationReason.INITIAL_APPOINTMENT);
                fixation.setIncrementCycle(employment.incrementCycle() != null ? employment.incrementCycle() : IncrementCycle.JULY);
                fixation.setOrderRefNo(employment.appointmentLetterNo());
                regularPayFixationRepository.save(fixation);
            }
            case CONTRACTUAL -> {
                ContractualEngagement engagement = new ContractualEngagement(
                        employee, employment.fixedLumpSumMonthly(), employment.contractStartDate(),
                        employment.contractEndDate(), employment.contractRefOrder());
                if (employment.scaleCode() != null) {
                    engagement.setGradeScale(resolveGradeScale(employment.scaleCode()));
                }
                contractualEngagementRepository.save(engagement);
            }
            case OUTSOURCED -> {
                OutsourcedDeployment deployment = new OutsourcedDeployment(
                        employee, employment.monthlyCtc(), employment.contractStartDate(),
                        employment.contractEndDate(), employment.contractRefOrder());
                deployment.setVendor(vendorMasterRepository.findById(employment.vendorId())
                        .orElseThrow(() -> new MasterDataNotFoundException("Vendor", employment.vendorId())));
                deployment.setAgencyBillingRate(employment.billingRateMonthly());
                if (employment.scaleCode() != null) {
                    deployment.setGradeScale(resolveGradeScale(employment.scaleCode()));
                }
                outsourcedDeploymentRepository.save(deployment);
            }
            case CASUAL -> {
                // No compensation ledger for CASUAL - see this method's javadoc.
            }
        }
    }

    private void saveSalaryBreakdownItem(Employee employee, OutsourcedSalaryBreakdownEntry entry) {
        salaryBreakdownRepository.save(new OutsourcedSalaryBreakdownItem(
                employee, entry.headCode(), entry.headName(), entry.headType(), entry.amount()));
    }

    private EmployeeRecruitmentDetails buildRecruitmentDetails(Employee employee, OnboardingEmploymentStepRequest employment) {
        EmployeeRecruitmentDetails details = new EmployeeRecruitmentDetails(
                employee, employment.recruitmentYear(), employment.recruitmentMode(), employment.selectionMethod(),
                employment.appointmentLetterNo(), employment.appointmentLetterDate(), employment.joiningLetterDate(),
                employment.dateOfJoiningPsu());
        details.setAdvertisementNo(employment.advertisementNo());
        details.setRecruitmentAgency(employment.recruitmentAgency());
        details.setOfferLetterDate(employment.offerLetterDate());
        return details;
    }

    private void saveDependent(Employee employee, OnboardingDependentEntry entry) {
        EmployeeDependent dependent = new EmployeeDependent(
                employee, entry.name(), entry.relationship(), entry.isDependent(), entry.isCoveredMedical());
        dependent.setDateOfBirth(entry.dateOfBirth());
        dependentRepository.save(dependent);
    }

    private void saveNominee(Employee employee, OnboardingNomineeEntry entry) {
        nomineeRepository.save(new EmployeeNominee(employee, entry.name(), entry.relationship(), entry.sharePercentage(), entry.nomineeFor()));
    }

    private void saveSocialProfile(Employee employee, OnboardingSocialProfileRequest entry) {
        EmployeeSocialProfile profile = new EmployeeSocialProfile(employee, entry.socialCategory());
        profile.setSubCasteCommunity(entry.subCasteCommunity());
        profile.setPwbd(entry.isPwbd());
        profile.setDisabilityType(entry.disabilityType());
        profile.setDisabilityPercentage(entry.disabilityPercentage());
        profile.setExServiceman(entry.isExServiceman());
        profile.setSportsQuota(entry.isSportsQuota());
        socialProfileRepository.save(profile);
    }

    private void saveDocument(Employee employee, OnboardingDocumentEntry entry) {
        EmployeeDocument document = new EmployeeDocument(employee, entry.documentCategory(), entry.documentTitle(), entry.fileS3Key());
        if (entry.mimeType() != null) {
            document.setMimeType(entry.mimeType());
        }
        documentRepository.save(document);
    }

    private PayScale resolvePayScale(Long payScaleId) {
        return payScaleRepository.findById(payScaleId)
                .orElseThrow(() -> new MasterDataNotFoundException("Pay Scale", payScaleId));
    }

    private GradeScaleMaster resolveGradeScale(String scaleCode) {
        return gradeScaleMasterRepository.findByScaleCode(scaleCode)
                .orElseThrow(() -> new MasterDataNotFoundException("Grade Scale", scaleCode));
    }

    private void validateEmploymentCategorySpecifics(OnboardingEmploymentStepRequest employment, List<String> errors) {
        if (employment == null) {
            return;
        }
        switch (employment.employmentCategory()) {
            case REGULAR -> {
                if (employment.postId() == null) {
                    errors.add("employment.postId is required for REGULAR");
                }
                if (employment.regularBasicPay() == null) {
                    errors.add("employment.regularBasicPay is required for REGULAR");
                }
                if (employment.scaleCode() == null) {
                    errors.add("employment.scaleCode is required for REGULAR");
                }
                validateBasicPayAgainstGradeScale(employment, errors);
            }
            case CASUAL -> {
                if (employment.dailyWageRate() == null) {
                    errors.add("employment.dailyWageRate is required for CASUAL");
                }
                requireDeptAndDesignation(employment, errors, "CASUAL");
            }
            case CONTRACTUAL -> {
                if (employment.fixedLumpSumMonthly() == null || employment.contractStartDate() == null
                        || employment.contractEndDate() == null || employment.contractRefOrder() == null) {
                    errors.add("employment.fixedLumpSumMonthly, contractStartDate, contractEndDate and contractRefOrder are required for CONTRACTUAL");
                }
                requireDeptAndDesignation(employment, errors, "CONTRACTUAL");
            }
            case OUTSOURCED -> {
                if (employment.vendorId() == null || employment.monthlyCtc() == null
                        || employment.contractStartDate() == null || employment.contractEndDate() == null
                        || employment.contractRefOrder() == null) {
                    errors.add("employment.vendorId, monthlyCtc, contractStartDate, contractEndDate and contractRefOrder (work order ref) are required for OUTSOURCED");
                }
                requireDeptAndDesignation(employment, errors, "OUTSOURCED");
            }
        }
    }

    /** Only enforced when scaleCode is supplied - see OnboardingEmploymentStepRequest.scaleCode's javadoc for why it's optional. */
    private void validateBasicPayAgainstGradeScale(OnboardingEmploymentStepRequest employment, List<String> errors) {
        if (employment.scaleCode() == null || employment.regularBasicPay() == null) {
            return;
        }
        gradeScaleMasterRepository.findByScaleCode(employment.scaleCode()).ifPresentOrElse(scale -> {
            if (employment.regularBasicPay().compareTo(scale.getMinimumBasic()) < 0
                    || employment.regularBasicPay().compareTo(scale.getMaximumBasic()) > 0) {
                errors.add("employment.regularBasicPay must be between " + scale.getMinimumBasic() + " and "
                        + scale.getMaximumBasic() + " for grade scale " + scale.getScaleCode());
            }
        }, () -> errors.add("employment.scaleCode does not match any grade scale: " + employment.scaleCode()));
    }

    private void requireDeptAndDesignation(OnboardingEmploymentStepRequest employment, List<String> errors, String category) {
        if (employment.departmentId() == null || employment.designationId() == null) {
            errors.add("employment.departmentId and designationId are required for " + category);
        }
    }

    private void validateNomineeShares(OnboardingFamilyStepRequest family, List<String> errors) {
        if (family == null || family.nominees() == null || family.nominees().isEmpty()) {
            return;
        }
        Map<String, BigDecimal> totals = new HashMap<>();
        for (OnboardingNomineeEntry nominee : family.nominees()) {
            if (nominee.sharePercentage() != null) {
                totals.merge(nominee.nomineeFor(), nominee.sharePercentage(), BigDecimal::add);
            }
        }
        totals.forEach((nomineeFor, total) -> {
            if (total.compareTo(FULL_SHARE) != 0) {
                errors.add("family.nominees: share percentages for " + nomineeFor + " must sum to exactly 100% (got " + total + ")");
            }
        });
    }

    private <T> void requireAndValidate(String groupName, T value, List<String> errors) {
        if (value == null) {
            errors.add(groupName + " is required");
            return;
        }
        collectViolations(groupName, value, errors);
    }

    private <T> void collectViolations(String groupName, T value, List<String> errors) {
        for (ConstraintViolation<T> violation : validator.validate(value)) {
            errors.add(groupName + "." + violation.getPropertyPath() + " " + violation.getMessage());
        }
    }

    private <T> List<T> normalizeList(List<T> list) {
        return list != null ? list : List.of();
    }

    private OnboardingDraftResponse toResponse(EmployeeOnboardingDraft draft) {
        JsonNode root = readTree(draft.getStepPayloadsJson());
        OnboardingPersonalDetailsRequest personal = readGroup(root, "personal", OnboardingPersonalDetailsRequest.class);
        EmployeeAddressRequest presentAddress = readGroup(root, "presentAddress", EmployeeAddressRequest.class);
        EmployeeAddressRequest permanentAddress = readGroup(root, "permanentAddress", EmployeeAddressRequest.class);
        Boolean permanentSameAsPresent = readGroup(root, "permanentSameAsPresent", Boolean.class);
        EmployeeBankAccountRequest banking = readGroup(root, "banking", EmployeeBankAccountRequest.class);
        List<QualificationRequest> qualifications = readListGroup(root, "qualifications", QualificationRequest.class);
        List<PastServiceRecordRequest> pastServiceRecords = readListGroup(root, "pastServiceRecords", PastServiceRecordRequest.class);
        OnboardingEmploymentStepRequest employment = readGroup(root, "employment", OnboardingEmploymentStepRequest.class);
        OnboardingFamilyStepRequest family = readGroup(root, "family", OnboardingFamilyStepRequest.class);
        List<OnboardingDocumentEntry> documents = readListGroup(root, "documents", OnboardingDocumentEntry.class);
        OnboardingSocialProfileRequest socialProfile = readGroup(root, "socialProfile", OnboardingSocialProfileRequest.class);

        int filled = 0;
        if (personal != null) filled++;
        if (presentAddress != null) filled++;
        if (banking != null) filled++;
        if (qualifications != null) filled++;
        if (pastServiceRecords != null) filled++;
        if (employment != null) filled++;
        if (family != null) filled++;
        if (documents != null) filled++;
        int completionPercentage = (int) Math.round(filled * 100.0 / TOTAL_STEPS);

        return new OnboardingDraftResponse(
                draft.getId(), draft.getDraftCode(), draft.getEmployeeCode(), draft.getCurrentStep(), draft.getStatus(),
                completionPercentage, personal, presentAddress, permanentAddress, permanentSameAsPresent, banking,
                qualifications, pastServiceRecords, employment, family, documents, socialProfile,
                draft.getSubmittedEmployeeId(), draft.getCreatedAt(), draft.getUpdatedAt(), draft.getSubmittedAt());
    }

    private void mergeField(ObjectNode root, String key, Object value) {
        if (value != null) {
            root.set(key, objectMapper.valueToTree(value));
        }
    }

    private <T> T readGroup(JsonNode root, String key, Class<T> type) {
        JsonNode node = root.get(key);
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return objectMapper.treeToValue(node, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt onboarding draft field " + key, e);
        }
    }

    private <T> List<T> readListGroup(JsonNode root, String key, Class<T> type) {
        JsonNode node = root.get(key);
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            CollectionType listType = objectMapper.getTypeFactory().constructCollectionType(List.class, type);
            return objectMapper.readValue(objectMapper.treeAsTokens(node), listType);
        } catch (IOException e) {
            throw new IllegalStateException("Corrupt onboarding draft field " + key, e);
        }
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt onboarding draft step_payloads JSON", e);
        }
    }

    private String writeTree(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize onboarding draft step_payloads", e);
        }
    }

    private EmployeeOnboardingDraft findInProgressOrThrow(Long id) {
        EmployeeOnboardingDraft draft = findOrThrow(id);
        if (draft.getStatus() != OnboardingStatus.IN_PROGRESS) {
            throw new BusinessRuleViolationException(
                    "Onboarding draft " + draft.getDraftCode() + " is not in progress (status=" + draft.getStatus() + ")");
        }
        return draft;
    }

    private EmployeeOnboardingDraft findOrThrow(Long id) {
        return draftRepository.findById(id)
                .orElseThrow(() -> new OnboardingDraftNotFoundException(id));
    }
}
