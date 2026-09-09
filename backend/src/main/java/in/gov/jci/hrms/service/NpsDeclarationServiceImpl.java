package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.NpsAdminSummaryResponse;
import in.gov.jci.hrms.dto.NpsDeclarationRequest;
import in.gov.jci.hrms.dto.NpsDeclarationResponse;
import in.gov.jci.hrms.dto.NpsPendingRow;
import in.gov.jci.hrms.dto.NpsPreviewResponse;
import in.gov.jci.hrms.dto.NpsSubmittedRow;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeNpsDeclaration;
import in.gov.jci.hrms.entity.EmployeeStatus;
import in.gov.jci.hrms.entity.RegularPayFixation;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.repository.EmployeeNpsDeclarationRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.RegularPayFixationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Enforces the "one NPS percentage declaration per employee per financial year" rule at the service
 * layer rather than relying solely on the uq_employee_nps_declarations_employee_fy database
 * constraint, so a repeat declaration surfaces as a clear BusinessRuleViolationException (400 with
 * a business message) instead of a raw DataIntegrityViolationException (which GlobalExceptionHandler
 * has no specific mapping for and would otherwise fall through to a generic 500).
 */
@Service
@Transactional(readOnly = true)
public class NpsDeclarationServiceImpl implements NpsDeclarationService {

    private final EmployeeNpsDeclarationRepository npsDeclarationRepository;
    private final EmployeeRepository employeeRepository;
    private final RegularPayFixationRepository regularPayFixationRepository;
    private final PayrollComputationService payrollComputationService;

    public NpsDeclarationServiceImpl(EmployeeNpsDeclarationRepository npsDeclarationRepository,
                                      EmployeeRepository employeeRepository,
                                      RegularPayFixationRepository regularPayFixationRepository,
                                      PayrollComputationService payrollComputationService) {
        this.npsDeclarationRepository = npsDeclarationRepository;
        this.employeeRepository = employeeRepository;
        this.regularPayFixationRepository = regularPayFixationRepository;
        this.payrollComputationService = payrollComputationService;
    }

    @Override
    @Transactional
    public NpsDeclarationResponse recordDeclaration(NpsDeclarationRequest request) {
        Employee employee = employeeRepository.findById(request.employeeId())
                .orElseThrow(() -> new EmployeeNotFoundException(request.employeeId()));

        String financialYear = currentFinancialYear();
        npsDeclarationRepository.findByEmployee_IdAndFinancialYear(employee.getId(), financialYear)
                .ifPresent(existing -> {
                    throw new BusinessRuleViolationException(
                            "NPS deduction percentage can only be changed once per financial year");
                });

        EmployeeNpsDeclaration declaration = new EmployeeNpsDeclaration(employee, financialYear,
                request.npsPercentage(), LocalDate.now(), request.remarks());
        return NpsDeclarationResponse.from(npsDeclarationRepository.save(declaration));
    }

    @Override
    public List<NpsDeclarationResponse> listAll() {
        return npsDeclarationRepository.findAll().stream()
                .map(NpsDeclarationResponse::from)
                .toList();
    }

    @Override
    public List<NpsDeclarationResponse> listByEmployee(Long employeeId) {
        return npsDeclarationRepository.findByEmployee_IdOrderByFinancialYearDesc(employeeId).stream()
                .map(NpsDeclarationResponse::from)
                .toList();
    }

    @Override
    public NpsPreviewResponse preview(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));

        BigDecimal[] basicAndDa = currentBasicAndDa(employee);
        String financialYear = currentFinancialYear();
        List<NpsDeclarationResponse> history = listByEmployee(employeeId);
        NpsDeclarationResponse currentFyDeclaration = history.stream()
                .filter(d -> d.financialYear().equals(financialYear))
                .findFirst()
                .orElse(null);

        return new NpsPreviewResponse(employee.getId(), employee.getEmployeeCode(), employee.getFullName(),
                basicAndDa[0], basicAndDa[1], financialYear, currentFyDeclaration != null, currentFyDeclaration, history);
    }

    @Override
    public NpsAdminSummaryResponse adminSummary(String financialYear) {
        List<EmployeeNpsDeclaration> declarations = npsDeclarationRepository.findByFinancialYear(financialYear);
        List<NpsSubmittedRow> submitted = declarations.stream().map(this::toSubmittedRow).toList();

        Set<Long> declaredEmployeeIds = declarations.stream().map(d -> d.getEmployee().getId()).collect(Collectors.toSet());
        List<NpsPendingRow> pending = employeeRepository.findByNpsEligibleTrueAndStatus(EmployeeStatus.ACTIVE).stream()
                .filter(employee -> !declaredEmployeeIds.contains(employee.getId()))
                .map(this::toPendingRow)
                .toList();

        return new NpsAdminSummaryResponse(financialYear, submitted, pending);
    }

    private NpsSubmittedRow toSubmittedRow(EmployeeNpsDeclaration declaration) {
        Employee employee = declaration.getEmployee();
        BigDecimal monthlyDeduction = null;
        try {
            BigDecimal[] basicAndDa = currentBasicAndDa(employee);
            BigDecimal basicPlusDa = basicAndDa[0].add(basicAndDa[1]);
            monthlyDeduction = basicPlusDa.multiply(declaration.getNpsPercentage())
                    .divide(new BigDecimal("100"), 0, java.math.RoundingMode.HALF_UP);
        } catch (BusinessRuleViolationException noCurrentFixation) {
            // Employee has no active pay fixation right now (e.g. separated since declaring) - leave the deduction estimate blank rather than failing the whole dashboard.
        }
        return new NpsSubmittedRow(employee.getId(), employee.getEmployeeCode(), employee.getFullName(), officeOrDpc(employee),
                declaration.getNpsPercentage(), monthlyDeduction, declaration.getCreatedAt(), declaration.getRemarks());
    }

    private NpsPendingRow toPendingRow(Employee employee) {
        return new NpsPendingRow(employee.getId(), employee.getEmployeeCode(), employee.getFullName(), officeOrDpc(employee));
    }

    private String officeOrDpc(Employee employee) {
        if (employee.getRegionalOffice() != null) {
            return employee.getRegionalOffice().getName();
        }
        if (employee.getDepartmentalPurchaseCentre() != null) {
            return employee.getDepartmentalPurchaseCentre().getName();
        }
        return null;
    }

    /** [basicPay, dearnessAllowance] from the employee's current pay fixation - throws BusinessRuleViolationException if there is none. */
    private BigDecimal[] currentBasicAndDa(Employee employee) {
        RegularPayFixation fixation = regularPayFixationRepository.findByEmployeeIdAndCurrentTrue(employee.getId())
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "Employee " + employee.getId() + " has no current pay fixation; cannot compute Basic/DA"));
        BigDecimal basicPay = fixation.getBasicPay();
        BigDecimal daPercentage = payrollComputationService.resolveDaPercentage(fixation.getGradeScale().getScaleType(), LocalDate.now());
        BigDecimal dearnessAllowance = payrollComputationService.computeDearnessAllowance(basicPay, daPercentage);
        return new BigDecimal[]{basicPay, dearnessAllowance};
    }

    /** April-March financial year, e.g. a date in Apr 2026-Mar 2027 (inclusive) resolves to "2026-2027". */
    static String currentFinancialYear() {
        LocalDate today = LocalDate.now();
        int startYear = today.getMonthValue() >= 4 ? today.getYear() : today.getYear() - 1;
        return startYear + "-" + (startYear + 1);
    }
}
