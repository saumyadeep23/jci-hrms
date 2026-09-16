package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.IdaProjectionEmployeeResponse;
import in.gov.jci.hrms.dto.IdaProjectionMonthWiseSummary;
import in.gov.jci.hrms.dto.IdaProjectionMonthlyBreakupResponse;
import in.gov.jci.hrms.dto.IdaProjectionSimulateRequest;
import in.gov.jci.hrms.dto.IdaProjectionSummaryResponse;
import in.gov.jci.hrms.entity.DaProjectionBatch;
import in.gov.jci.hrms.entity.DaProjectionEmployee;
import in.gov.jci.hrms.entity.DaProjectionMonthlyBreakup;
import in.gov.jci.hrms.security.SecurityUtils;
import in.gov.jci.hrms.service.IdaProjectionEngineService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** CPSE IDA/CDA DA-rate-revision projection & simulation - same role set as PayrollMasterController's own class-level @PreAuthorize, since this is a payroll-masters-adjacent function. */
@RestController
@PreAuthorize("hasAnyRole('HR_ADMIN', 'BILL_SUPERVISOR', 'FINANCE_ADMIN')")
public class IdaProjectionController {

    private final IdaProjectionEngineService projectionEngineService;

    public IdaProjectionController(IdaProjectionEngineService projectionEngineService) {
        this.projectionEngineService = projectionEngineService;
    }

    @PostMapping("/api/v1/payroll/ida-projections/simulate")
    public ResponseEntity<IdaProjectionSummaryResponse> simulate(@Valid @RequestBody IdaProjectionSimulateRequest request,
                                                                  Authentication authentication) {
        Long createdBy = SecurityUtils.currentEmployeeId(authentication);
        DaProjectionBatch batch = projectionEngineService.executeSimulation(request.scaleType(), request.newDaRate(),
                request.effectiveFrom(), request.drawalMonth(), request.drawalYear(), createdBy);
        return ResponseEntity.status(HttpStatus.CREATED).body(toSummary(batch));
    }

    @GetMapping("/api/v1/payroll/ida-projections/{id}")
    public IdaProjectionSummaryResponse getBatch(@PathVariable Long id) {
        return toSummary(projectionEngineService.getBatch(id));
    }

    /**
     * Scenario A cutoff/rollover preview, WITHOUT committing - RolloverConfirmationModal's own read
     * before it asks HR to confirm. Safe to call repeatedly: checkCutoffAndRollOver() itself is a no-op
     * once the target month is no longer locked, and never sets ORDER_COMMITTED.
     */
    @PostMapping("/api/v1/payroll/ida-projections/{id}/check-rollover")
    public IdaProjectionSummaryResponse checkRollover(@PathVariable Long id) {
        return toSummary(projectionEngineService.checkCutoffAndRollOver(id));
    }

    @PostMapping("/api/v1/payroll/ida-projections/{id}/commit-order")
    public IdaProjectionSummaryResponse commitOrder(@PathVariable Long id) {
        DaProjectionBatch batch = projectionEngineService.commitOrder(id);
        return toSummary(batch);
    }

    /** EmployeeArrearScheduleTable's parent rows, each carrying its own month-by-month accordion. */
    @GetMapping("/api/v1/payroll/ida-projections/{id}/employees")
    public List<IdaProjectionEmployeeResponse> getEmployees(@PathVariable Long id) {
        List<DaProjectionEmployee> employees = projectionEngineService.getProjectionEmployees(id);
        return employees.stream()
                .map(pe -> IdaProjectionEmployeeResponse.from(pe, monthlyBreakupsFor(pe)))
                .toList();
    }

    private List<IdaProjectionMonthlyBreakupResponse> monthlyBreakupsFor(DaProjectionEmployee pe) {
        return projectionEngineService.getMonthlyBreakupsForEmployee(pe.getId()).stream()
                .map(IdaProjectionMonthlyBreakupResponse::from)
                .toList();
    }

    private IdaProjectionSummaryResponse toSummary(DaProjectionBatch batch) {
        List<DaProjectionMonthlyBreakup> breakups = projectionEngineService.getMonthlyBreakups(batch.getId());
        return IdaProjectionSummaryResponse.from(batch, aggregateMonthWise(breakups));
    }

    /** Sums every employee's per-month breakup rows into one org-wide row per calendar month, ordered by month. */
    private List<IdaProjectionMonthWiseSummary> aggregateMonthWise(List<DaProjectionMonthlyBreakup> breakups) {
        Map<Long, IdaProjectionMonthWiseSummary> byMonthKey = new LinkedHashMap<>();
        for (DaProjectionMonthlyBreakup b : breakups) {
            long key = b.getSalYear() * 100L + b.getSalMonth();
            IdaProjectionMonthWiseSummary existing = byMonthKey.get(key);
            IdaProjectionMonthWiseSummary merged = existing == null
                    ? new IdaProjectionMonthWiseSummary(b.getSalMonth(), b.getSalYear(), b.getMonthLabel(),
                            b.getDeltaDa(), b.getEmployeeCpfArrear(), b.getEmployerJcpfArrear(), b.getEmployeeNpsArrear(),
                            b.getEmployerNpsArrear(), b.getNetMonthlyArrear(), b.getEmployerCostMonthly())
                    : new IdaProjectionMonthWiseSummary(b.getSalMonth(), b.getSalYear(), b.getMonthLabel(),
                            existing.totalDeltaDa().add(b.getDeltaDa()),
                            existing.totalEmployeeCpfArrear().add(b.getEmployeeCpfArrear()),
                            existing.totalEmployerJcpfArrear().add(b.getEmployerJcpfArrear()),
                            existing.totalEmployeeNpsArrear().add(b.getEmployeeNpsArrear()),
                            existing.totalEmployerNpsArrear().add(b.getEmployerNpsArrear()),
                            existing.totalNetMonthlyArrear().add(b.getNetMonthlyArrear()),
                            existing.totalEmployerCostMonthly().add(b.getEmployerCostMonthly()));
            byMonthKey.put(key, merged);
        }
        return byMonthKey.values().stream()
                .sorted((a, c) -> a.salYear() != c.salYear() ? Integer.compare(a.salYear(), c.salYear()) : Integer.compare(a.salMonth(), c.salMonth()))
                .toList();
    }
}
