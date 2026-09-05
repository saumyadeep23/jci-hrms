package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.EmployeeMovementRecordResponse;
import in.gov.jci.hrms.dto.MovementOrderCreateRequest;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.DepartmentalPurchaseCentre;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeEmploymentCategory;
import in.gov.jci.hrms.entity.EmployeeMovementRecord;
import in.gov.jci.hrms.entity.EmploymentCategory;
import in.gov.jci.hrms.entity.FixationReason;
import in.gov.jci.hrms.entity.GradeScaleMaster;
import in.gov.jci.hrms.entity.IncrementCycle;
import in.gov.jci.hrms.entity.MovementOrder;
import in.gov.jci.hrms.entity.MovementOrderType;
import in.gov.jci.hrms.entity.RegionalOffice;
import in.gov.jci.hrms.entity.RegularPayFixation;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.DepartmentRepository;
import in.gov.jci.hrms.repository.DepartmentalPurchaseCentreRepository;
import in.gov.jci.hrms.repository.DesignationRepository;
import in.gov.jci.hrms.repository.EmployeeEmploymentCategoryRepository;
import in.gov.jci.hrms.repository.EmployeeMovementRecordRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.GradeScaleMasterRepository;
import in.gov.jci.hrms.repository.MovementOrderRepository;
import in.gov.jci.hrms.repository.RegionalOfficeRepository;
import in.gov.jci.hrms.repository.RegularPayFixationRepository;
import in.gov.jci.hrms.service.pdf.PromotionOrderPdfGenerator;
import in.gov.jci.hrms.service.pdf.TransferOrderPdfGenerator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/** Tab 1 - Movement Orders. Creates a MovementOrder together with its (first) EmployeeMovementRecord. */
@Service
@Transactional(readOnly = true)
public class MovementOrderService {

    private static final BigDecimal DPE_PROMOTION_INCREMENT_RATE = new BigDecimal("0.03");

    private final MovementOrderRepository movementOrderRepository;
    private final EmployeeMovementRecordRepository movementRecordRepository;
    private final EmployeeRepository employeeRepository;
    private final RegionalOfficeRepository regionalOfficeRepository;
    private final DepartmentalPurchaseCentreRepository departmentalPurchaseCentreRepository;
    private final DepartmentRepository departmentRepository;
    private final DesignationRepository designationRepository;
    private final JoiningTimeCalculatorService joiningTimeCalculatorService;
    private final GeofenceService geofenceService;
    private final TransferOrderPdfGenerator transferOrderPdfGenerator;
    private final PromotionOrderPdfGenerator promotionOrderPdfGenerator;
    private final EmployeeEmploymentCategoryRepository employmentCategoryRepository;
    private final GradeScaleMasterRepository gradeScaleMasterRepository;
    private final RegularPayFixationRepository regularPayFixationRepository;

    public MovementOrderService(MovementOrderRepository movementOrderRepository,
                                 EmployeeMovementRecordRepository movementRecordRepository,
                                 EmployeeRepository employeeRepository,
                                 RegionalOfficeRepository regionalOfficeRepository,
                                 DepartmentalPurchaseCentreRepository departmentalPurchaseCentreRepository,
                                 DepartmentRepository departmentRepository,
                                 DesignationRepository designationRepository,
                                 JoiningTimeCalculatorService joiningTimeCalculatorService,
                                 GeofenceService geofenceService,
                                 TransferOrderPdfGenerator transferOrderPdfGenerator,
                                 PromotionOrderPdfGenerator promotionOrderPdfGenerator,
                                 EmployeeEmploymentCategoryRepository employmentCategoryRepository,
                                 GradeScaleMasterRepository gradeScaleMasterRepository,
                                 RegularPayFixationRepository regularPayFixationRepository) {
        this.movementOrderRepository = movementOrderRepository;
        this.movementRecordRepository = movementRecordRepository;
        this.employeeRepository = employeeRepository;
        this.regionalOfficeRepository = regionalOfficeRepository;
        this.departmentalPurchaseCentreRepository = departmentalPurchaseCentreRepository;
        this.departmentRepository = departmentRepository;
        this.designationRepository = designationRepository;
        this.joiningTimeCalculatorService = joiningTimeCalculatorService;
        this.geofenceService = geofenceService;
        this.transferOrderPdfGenerator = transferOrderPdfGenerator;
        this.promotionOrderPdfGenerator = promotionOrderPdfGenerator;
        this.employmentCategoryRepository = employmentCategoryRepository;
        this.gradeScaleMasterRepository = gradeScaleMasterRepository;
        this.regularPayFixationRepository = regularPayFixationRepository;
    }

    @Transactional
    public EmployeeMovementRecordResponse create(MovementOrderCreateRequest request) {
        if (movementOrderRepository.existsByOrderRefNo(request.orderRefNo())) {
            throw new MasterDataConflictException("An order with reference " + request.orderRefNo() + " already exists.");
        }

        MovementOrder order = new MovementOrder(request.orderType(), request.orderRefNo(), request.orderDate());
        order.setEffectiveDate(request.effectiveDate());
        order.setSanctionedByRole(request.sanctionedByRole());
        if (request.signedByEmployeeId() != null) {
            order.setSignedByEmployee(resolveEmployee(request.signedByEmployeeId()));
        }

        Employee employee = resolveEmployee(request.employeeId());
        Station fromStation = resolveStation(request.fromOfficeId(), request.fromDpcId());
        Station toStation = resolveStation(request.toOfficeId(), request.toDpcId());
        Designation fromDesignation = resolveDesignation(request.fromDesignationId());
        Designation toDesignation = resolveDesignation(request.toDesignationId());

        EmployeeMovementRecord record = new EmployeeMovementRecord(order, employee, fromStation.office(), fromDesignation,
                toStation.office(), toDesignation);
        record.setFromDpc(fromStation.dpc());
        record.setToDpc(toStation.dpc());
        record.setTransferNature(request.transferNature());
        record.setTransferBenefitAdmissible(Boolean.TRUE.equals(request.transferBenefitAdmissible()));
        record.setRequestApplicationRef(request.requestApplicationRef());
        record.setRequestReason(request.requestReason());
        record.setFromDepartment(resolveDepartment(request.fromDepartmentId()));
        record.setFromPayScale(request.fromPayScale());
        record.setToDepartment(resolveDepartment(request.toDepartmentId()));
        record.setToPayScale(request.toPayScale());
        record.setPromotionalBasicPay(request.promotionalBasicPay());
        if (request.probationPeriodMonths() != null) {
            record.setProbationPeriodMonths(request.probationPeriodMonths());
        }

        RegionalOffice fromOffice = fromStation.office();
        RegionalOffice toOffice = toStation.office();

        int distanceKm = request.stationDistanceKmOverride() != null
                ? request.stationDistanceKmOverride()
                : computeStationDistanceKm(fromOffice, toOffice);
        record.setStationDistanceKm(distanceKm);
        record.setAdmissibleJtDays(joiningTimeCalculatorService.computeAdmissibleJtDays(request.transferNature(), distanceKm));

        movementOrderRepository.saveAndFlush(order);
        EmployeeMovementRecord savedRecord = movementRecordRepository.saveAndFlush(record);

        if (isPromotionType(request.orderType()) && request.toScaleCode() != null) {
            applyPromotionPayFixation(employee, request);
        }

        return EmployeeMovementRecordResponse.from(savedRecord);
    }

    private boolean isPromotionType(MovementOrderType orderType) {
        return orderType == MovementOrderType.PROMOTION || orderType == MovementOrderType.TRANSFER_CUM_PROMOTION;
    }

    /**
     * DPE-rule pay fixation on promotion (V50's regular_pay_fixations, additive alongside
     * employee_employment_categories which is kept in sync below so payroll's existing reader is
     * unaffected) - only for employees who are actually on the REGULAR/grade-scale ladder; a
     * promotion order for anyone else (no current EmployeeEmploymentCategory, or one that isn't
     * REGULAR) has nothing to fix here and is silently skipped.
     */
    private void applyPromotionPayFixation(Employee employee, MovementOrderCreateRequest request) {
        EmployeeEmploymentCategory category = employmentCategoryRepository.findByEmployeeId(employee.getId()).orElse(null);
        if (category == null || category.getEmploymentCategory() != EmploymentCategory.REGULAR) {
            return;
        }
        if (request.effectiveDate() == null) {
            throw new BusinessRuleViolationException("effectiveDate is required to fix pay for a PROMOTION/TRANSFER_CUM_PROMOTION order");
        }
        GradeScaleMaster targetScale = gradeScaleMasterRepository.findByScaleCode(request.toScaleCode())
                .orElseThrow(() -> new MasterDataNotFoundException("Grade Scale", request.toScaleCode()));

        Optional<RegularPayFixation> currentFixation = regularPayFixationRepository.findByEmployeeIdAndCurrentTrue(employee.getId());
        BigDecimal currentBasic = currentFixation.map(RegularPayFixation::getBasicPay).orElse(category.getRegularBasicPay());
        if (currentBasic == null) {
            return;
        }

        BigDecimal increment = roundToNearestTen(currentBasic.multiply(DPE_PROMOTION_INCREMENT_RATE));
        BigDecimal fixedBasic = currentBasic.add(increment).max(targetScale.getMinimumBasic()).min(targetScale.getMaximumBasic());

        currentFixation.ifPresent(previous -> {
            previous.setCurrent(false);
            previous.setEffectiveTo(request.effectiveDate().minusDays(1));
        });

        RegularPayFixation newFixation = new RegularPayFixation(employee, targetScale, fixedBasic, request.effectiveDate());
        newFixation.setFixationReason(FixationReason.PROMOTION);
        newFixation.setIncrementCycle(currentFixation.map(RegularPayFixation::getIncrementCycle).orElse(IncrementCycle.JULY));
        newFixation.setOrderRefNo(request.orderRefNo());
        regularPayFixationRepository.save(newFixation);

        category.setRegularBasicPay(fixedBasic);
        category.setGradeScale(targetScale);
    }

    private static BigDecimal roundToNearestTen(BigDecimal amount) {
        return amount.divide(BigDecimal.TEN, 0, RoundingMode.HALF_UP).multiply(BigDecimal.TEN);
    }

    /** Straight-line Haversine distance between the two offices' configured coordinates; 0 when either office has none configured (e.g. HO or a not-yet-geocoded RO). */
    private int computeStationDistanceKm(RegionalOffice fromOffice, RegionalOffice toOffice) {
        if (fromOffice.getLatitude() == null || fromOffice.getLongitude() == null
                || toOffice.getLatitude() == null || toOffice.getLongitude() == null) {
            return 0;
        }
        if (fromOffice.getId().equals(toOffice.getId())) {
            return 0;
        }
        double meters = geofenceService.distanceMeters(fromOffice.getLatitude(), fromOffice.getLongitude(),
                toOffice.getLatitude(), toOffice.getLongitude());
        return (int) Math.round(meters / 1000.0);
    }

    public Page<EmployeeMovementRecordResponse> list(Pageable pageable) {
        return movementRecordRepository.findAllByOrderByCreatedAtDesc(pageable).map(EmployeeMovementRecordResponse::from);
    }

    public EmployeeMovementRecordResponse getById(Long id) {
        return EmployeeMovementRecordResponse.from(findRecordOrThrow(id));
    }

    /** Dispatches to the Transfer or Promotion order PDF template by order type - TRANSFER_CUM_PROMOTION uses the Promotion template (it carries the same mandatory-clause content). */
    public byte[] generateOrderPdf(Long orderId) {
        MovementOrder order = movementOrderRepository.findById(orderId)
                .orElseThrow(() -> new MasterDataNotFoundException("Movement Order", orderId));
        List<EmployeeMovementRecord> records = movementRecordRepository.findByOrderIdOrderById(orderId);
        return order.getOrderType() == MovementOrderType.TRANSFER
                ? transferOrderPdfGenerator.generate(order, records)
                : promotionOrderPdfGenerator.generate(order, records);
    }

    /**
     * ESS: the same document, but scoped through a movement record the caller is actually named
     * in - an order can cover several employees, and the resulting PDF still lists all of them
     * (that's the correct, intended content of a real office order), but the caller must be one of
     * the people it's about, never an arbitrary orderId lookup.
     */
    public byte[] generateOrderPdfForEmployee(Long movementId, Long callerEmployeeId) {
        EmployeeMovementRecord record = findRecordOrThrow(movementId);
        if (callerEmployeeId != null && !callerEmployeeId.equals(record.getEmployee().getId())) {
            throw new BusinessRuleViolationException("You may only download your own movement order");
        }
        return generateOrderPdf(record.getOrder().getId());
    }

    private EmployeeMovementRecord findRecordOrThrow(Long id) {
        return movementRecordRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException("Movement Record", id));
    }

    private Employee resolveEmployee(Long id) {
        return employeeRepository.findById(id).orElseThrow(() -> new EmployeeNotFoundException(id));
    }

    /** A resolved station is either a direct office pick (dpc() null) or a DPC pick, resolved to its own parent RO for office() - see MovementOrderCreateRequest's javadoc. */
    private record Station(RegionalOffice office, DepartmentalPurchaseCentre dpc) {
    }

    private Station resolveStation(Long officeId, Long dpcId) {
        if (dpcId != null) {
            DepartmentalPurchaseCentre dpc = departmentalPurchaseCentreRepository.findById(dpcId)
                    .orElseThrow(() -> new MasterDataNotFoundException("DPC", dpcId));
            return new Station(dpc.getRegionalOffice(), dpc);
        }
        return new Station(resolveOffice(officeId), null);
    }

    private RegionalOffice resolveOffice(Long id) {
        return regionalOfficeRepository.findById(id).orElseThrow(() -> new MasterDataNotFoundException("Regional Office", id));
    }

    private Department resolveDepartment(Long id) {
        if (id == null) return null;
        return departmentRepository.findById(id).orElseThrow(() -> new MasterDataNotFoundException("Department", id));
    }

    private Designation resolveDesignation(Long id) {
        return designationRepository.findById(id).orElseThrow(() -> new MasterDataNotFoundException("Designation", id));
    }
}
