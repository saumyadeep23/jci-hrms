package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.IncrementBatchProcessRequest;
import in.gov.jci.hrms.dto.IncrementBatchProcessResponse;
import in.gov.jci.hrms.dto.IncrementDueEntry;
import in.gov.jci.hrms.dto.PimsReportFilter;
import in.gov.jci.hrms.dto.TabularReportResponse;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeEmploymentCategory;
import in.gov.jci.hrms.entity.EmployeeServiceBook;
import in.gov.jci.hrms.entity.FixationReason;
import in.gov.jci.hrms.entity.GradeScaleMaster;
import in.gov.jci.hrms.entity.RegularPayFixation;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.EmployeeEmploymentCategoryRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.EmployeeServiceBookRepository;
import in.gov.jci.hrms.repository.RegularPayFixationRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * GET /api/v1/increments/due-list, POST /api/v1/increments/process-batch -
 * PIMS_SPEC.md dashboard card 5. Only REGULAR-cadre employees with a
 * regular_basic_pay on record are increment-eligible here (CASUAL/
 * CONTRACTUAL/OUTSOURCED aren't on an IDA basic-pay ladder). The 3% rate is
 * IDA's own increment rate rather than PayScale.incrementRate, since the
 * spec calls this "Monthly Increment (3% IDA)" explicitly.
 */
@Service
@Transactional(readOnly = true)
public class IncrementProcessingService implements PimsReportExportSource {

    private static final BigDecimal IDA_INCREMENT_RATE = new BigDecimal("0.03");
    private static final BigDecimal ROUNDING_UNIT = BigDecimal.TEN;

    // CAST(? AS BIGINT) rather than a bare "? IS NULL": with the default,
    // no-filter call (departmentId null), PostgreSQL can't infer a type for a
    // parameter whose only use is "$1 IS NULL" with no typed sibling in that
    // expression, and fails with "could not determine data type of
    // parameter" before any row is read (see CadreStrengthReportService).
    //
    // Current basic pay/grade come from regular_pay_fixations (the
    // historized, increment/promotion-aware ledger - V50), falling back to
    // employee_employment_categories.scale_code/grade_scale_master (V60:
    // cat.pay_scale_id/pay_scale_master is gone, cat.scale_code is the sole
    // fallback link now - same join V60 put in vw_jci_employee_master_360)
    // only for the rare REGULAR employee somehow missing a current fixation
    // row - see Manpower4TierReportService's identical COALESCE reasoning.
    // "Increment Month" filters on the employee's date-of-joining month,
    // since that anniversary is what actually determines when an individual
    // REGULAR employee's annual increment falls due.
    // Hard superannuation guard: a REGULAR employee whose
    // employee_superannuation_details.superannuation_date has already
    // passed is excluded outright, never merely flagged - a retired
    // employee must never be returned as increment-due or be
    // batch-processed for a 3% raise even if someone forgets to run Exit
    // Formalities first (SuperannuationScheduledTask's own daily sweep is
    // the belt to this query's braces).
    private static final String DUE_LIST_SQL =
            "SELECT e.id, e.employee_code, e.full_name, "
                    + "COALESCE(rpf.scale_code, cat.scale_code) AS grade, "
                    + "COALESCE(gsm.maximum_basic, csm.maximum_basic) AS maximum_basic, "
                    + "COALESCE(rpf.basic_pay, cat.regular_basic_pay) AS current_basic_pay, "
                    + "EXISTS(SELECT 1 FROM disciplinary_cases dc WHERE dc.employee_id = e.id AND dc.is_deleted = false "
                    + "       AND dc.status NOT IN ('CLOSED', 'EXONERATED')) AS has_active_case "
                    + "FROM employee_employment_categories cat "
                    + "JOIN employees e ON e.id = cat.employee_id AND e.deleted_at IS NULL "
                    + "LEFT JOIN regular_pay_fixations rpf ON rpf.employee_id = e.id AND rpf.is_current = true "
                    + "LEFT JOIN grade_scale_master gsm ON gsm.scale_code = rpf.scale_code "
                    + "LEFT JOIN grade_scale_master csm ON csm.scale_code = cat.scale_code "
                    + "LEFT JOIN employee_superannuation_details sup ON sup.employee_id = e.id "
                    + "WHERE cat.employment_category = 'REGULAR' AND cat.deleted_at IS NULL AND cat.is_active = true "
                    + "  AND COALESCE(rpf.basic_pay, cat.regular_basic_pay) IS NOT NULL "
                    + "  AND NOT (sup.superannuation_date IS NOT NULL AND sup.superannuation_date <= CURRENT_DATE) "
                    + "  AND (CAST(? AS BIGINT) IS NULL OR e.department_id = CAST(? AS BIGINT)) "
                    + "  AND (CAST(? AS INTEGER) IS NULL OR EXTRACT(MONTH FROM e.date_of_joining) = CAST(? AS INTEGER)) "
                    + "ORDER BY e.employee_code";

    private final JdbcTemplate jdbcTemplate;
    private final EmployeeRepository employeeRepository;
    private final EmployeeEmploymentCategoryRepository employmentCategoryRepository;
    private final EmployeeServiceBookRepository serviceBookRepository;
    private final RegularPayFixationRepository regularPayFixationRepository;

    public IncrementProcessingService(JdbcTemplate jdbcTemplate, EmployeeRepository employeeRepository,
                                       EmployeeEmploymentCategoryRepository employmentCategoryRepository,
                                       EmployeeServiceBookRepository serviceBookRepository,
                                       RegularPayFixationRepository regularPayFixationRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.employeeRepository = employeeRepository;
        this.employmentCategoryRepository = employmentCategoryRepository;
        this.serviceBookRepository = serviceBookRepository;
        this.regularPayFixationRepository = regularPayFixationRepository;
    }

    public List<IncrementDueEntry> dueList(PimsReportFilter filter) {
        return jdbcTemplate.query(DUE_LIST_SQL, (rs, i) -> {
            BigDecimal currentBasic = rs.getBigDecimal("current_basic_pay");
            BigDecimal maxBasic = rs.getBigDecimal("maximum_basic");
            BigDecimal increment = roundToNearestTen(currentBasic.multiply(IDA_INCREMENT_RATE));
            BigDecimal newBasic = currentBasic.add(increment);
            boolean atCeiling = maxBasic != null && newBasic.compareTo(maxBasic) > 0;
            if (atCeiling) {
                newBasic = maxBasic;
                increment = newBasic.subtract(currentBasic);
            }
            boolean withheld = rs.getBoolean("has_active_case");
            return new IncrementDueEntry(
                    rs.getLong("id"), rs.getString("employee_code"), rs.getString("full_name"), rs.getString("grade"),
                    currentBasic, increment, newBasic, atCeiling, withheld,
                    withheld ? "Active disciplinary case" : null);
        }, filter.departmentId(), filter.departmentId(), filter.incrementMonth(), filter.incrementMonth());
    }

    /**
     * 3% CPSE increment, rounded UP to the next higher multiple of Rs. 10 -
     * DPE rules round an increment in the employee's favor, never down to
     * the nearest 10 (HALF_UP would silently understate it - e.g. Rs.
     * 22,820 x 3% = 684.60 must become Rs. 690, not Rs. 680 from rounding
     * 68.46 down to 68). NOTE this intentionally diverges from
     * MovementOrderService.roundToNearestTen's HALF_UP promotion-increment
     * rounding - that's a separate, pre-existing convention this fix does
     * not touch since no promotion-specific bug was reported.
     */
    static BigDecimal roundToNearestTen(BigDecimal amount) {
        return amount.divide(ROUNDING_UNIT, 0, RoundingMode.CEILING).multiply(ROUNDING_UNIT);
    }

    /** POST /api/v1/increments/process-batch - recomputes each increment server-side; never trusts a client-supplied amount. */
    @Transactional
    public IncrementBatchProcessResponse processBatch(IncrementBatchProcessRequest request) {
        Map<Long, IncrementDueEntry> dueByEmployeeId = dueList(PimsReportFilter.empty()).stream()
                .collect(java.util.stream.Collectors.toMap(IncrementDueEntry::employeeId, e -> e));

        int processed = 0;
        List<String> skippedReasons = new ArrayList<>();
        for (Long employeeId : request.employeeIds()) {
            IncrementDueEntry due = dueByEmployeeId.get(employeeId);
            if (due == null) {
                skippedReasons.add("Employee " + employeeId + ": not eligible (not REGULAR or no basic pay on record)");
                continue;
            }
            if (due.isWithheld()) {
                skippedReasons.add(due.employeeCode() + ": withheld (" + due.withheldReason() + ")");
                continue;
            }

            Employee employee = employeeRepository.findById(employeeId)
                    .orElseThrow(() -> new MasterDataNotFoundException("Employee", employeeId));
            EmployeeEmploymentCategory category = employmentCategoryRepository.findByEmployeeId(employeeId)
                    .orElseThrow(() -> new MasterDataNotFoundException("Employment Category", employeeId));
            category.setRegularBasicPay(due.newBasicPay());

            // Supersede the current regular_pay_fixations row (same scale - an increment doesn't
            // change grade, unlike a promotion) and insert a new one, mirroring
            // MovementOrderService.applyPromotionPayFixation's exact pattern. An employee somehow
            // missing a current fixation row (no scale_code to satisfy the FK) just keeps the
            // legacy employee_employment_categories-only update above.
            Optional<RegularPayFixation> currentFixation = regularPayFixationRepository.findByEmployeeIdAndCurrentTrue(employeeId);
            currentFixation.ifPresent(previous -> {
                GradeScaleMaster scale = previous.getGradeScale();
                previous.setCurrent(false);
                previous.setEffectiveTo(request.orderDate().minusDays(1));

                RegularPayFixation newFixation = new RegularPayFixation(employee, scale, due.newBasicPay(), request.orderDate());
                newFixation.setFixationReason(FixationReason.ANNUAL_INCREMENT);
                newFixation.setIncrementCycle(previous.getIncrementCycle());
                newFixation.setOrderRefNo(request.orderNumber());
                regularPayFixationRepository.save(newFixation);
            });

            EmployeeServiceBook entry = new EmployeeServiceBook(employee, request.orderDate(), "INCREMENT");
            entry.setOrderNumber(request.orderNumber());
            entry.setOrderDate(request.orderDate());
            entry.setBasicPay(due.newBasicPay());
            entry.setEventDescription("3% IDA increment: " + due.currentBasicPay() + " -> " + due.newBasicPay());
            entry.setRemarks(request.remarks());
            entry.setMigrated(false);
            serviceBookRepository.save(entry);

            processed++;
        }

        return new IncrementBatchProcessResponse(processed, skippedReasons.size(), skippedReasons);
    }

    @Override
    public PimsReportType reportType() {
        return PimsReportType.INCREMENT_DUE_LIST;
    }

    @Override
    public String exportTitle() {
        return "Increment Due List";
    }

    @Override
    public TabularReportResponse tabularData(PimsReportFilter filter) {
        List<IncrementDueEntry> entries = dueList(filter);
        List<Map<String, Object>> rows = entries.stream().map(e -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("Employee Code", e.employeeCode());
            row.put("Name", e.employeeName());
            row.put("Grade", e.payScaleGrade());
            row.put("Current Basic", e.currentBasicPay());
            row.put("Increment", e.incrementAmount());
            row.put("New Basic", e.newBasicPay());
            row.put("At Ceiling", e.atStagnationCeiling() ? "Yes" : "No");
            row.put("Withheld", e.isWithheld() ? "Yes" : "No");
            return row;
        }).toList();
        return new TabularReportResponse(
                List.of("Employee Code", "Name", "Grade", "Current Basic", "Increment", "New Basic", "At Ceiling", "Withheld"),
                rows, rows.size());
    }
}
