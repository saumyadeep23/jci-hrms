package in.gov.jci.hrms.service;

import in.gov.jci.hrms.config.CpfTrustPolicyProperties;
import in.gov.jci.hrms.dto.CpfEpsEligibilityResponse;
import in.gov.jci.hrms.dto.CpfEpsEligibilityStatus;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeStatus;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.EmployeeSuperannuationDetailsRepository;
import in.gov.jci.hrms.repository.ExitClearanceRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Set;

/**
 * Backs "Check EPS Pension Eligibility" (GET /api/v1/payroll/trust/members/{employeeId}/eps-eligibility).
 *
 * IMPORTANT - placeholder rule, not confirmed JCI policy: this codebase has no authoritative EPS pension
 * eligibility rule anywhere (Employee.epsEligible/epsHigherPensionEligible are manually-set enrolment flags,
 * not a computed eligibility). The threshold used below (CpfTrustPolicyProperties.epsMinimumPensionableServiceYears,
 * default 10) mirrors the well-known EPS-95 Para 12 minimum-pensionable-service rule for monthly-pension
 * eligibility, but is deliberately kept as configuration rather than hardcoded business logic, per this
 * task's own instruction not to invent authoritative legal/business rules. Confirm against JCI's actual CPF
 * Trust / EPS scheme rules before relying on this for a real determination.
 *
 * "Eligible service" and "pensionable service" are reported as the same span here - there is no tracking in
 * this codebase of breaks in service or non-pensionable periods (e.g. unpaid leave) that would make the two
 * diverge, so distinguishing them would mean inventing data this schema doesn't have.
 */
@Service
@Transactional(readOnly = true)
public class CpfEpsEligibilityService {

    private static final Set<EmployeeStatus> SEPARATED_STATUSES =
            EnumSet.of(EmployeeStatus.RETIRED, EmployeeStatus.RESIGNED, EmployeeStatus.DECEASED, EmployeeStatus.TERMINATED);

    private final EmployeeRepository employeeRepository;
    private final ExitClearanceRequestRepository exitClearanceRequestRepository;
    private final EmployeeSuperannuationDetailsRepository superannuationDetailsRepository;
    private final CpfTrustPolicyProperties policyProperties;

    public CpfEpsEligibilityService(EmployeeRepository employeeRepository,
                                     ExitClearanceRequestRepository exitClearanceRequestRepository,
                                     EmployeeSuperannuationDetailsRepository superannuationDetailsRepository,
                                     CpfTrustPolicyProperties policyProperties) {
        this.employeeRepository = employeeRepository;
        this.exitClearanceRequestRepository = exitClearanceRequestRepository;
        this.superannuationDetailsRepository = superannuationDetailsRepository;
        this.policyProperties = policyProperties;
    }

    public CpfEpsEligibilityResponse checkEligibility(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId).orElseThrow(() -> new EmployeeNotFoundException(employeeId));

        LocalDate separationDate = resolveSeparationDate(employee);
        LocalDate serviceEndDate = separationDate != null ? separationDate : LocalDate.now();
        LocalDate serviceStartDate = employee.getDateOfJoining();

        long eligibleServiceDays = serviceStartDate != null ? ChronoUnit.DAYS.between(serviceStartDate, serviceEndDate) : 0;
        Period eligiblePeriod = serviceStartDate != null ? Period.between(serviceStartDate, serviceEndDate) : Period.ZERO;
        String serviceLabel = formatPeriod(eligiblePeriod);

        CpfEpsEligibilityStatus eligibility;
        String basis;
        int minimumYears = policyProperties.getEpsMinimumPensionableServiceYears();

        if (!employee.isEpsEligible()) {
            eligibility = CpfEpsEligibilityStatus.NOT_ELIGIBLE;
            basis = "Not enrolled under the Employees' Pension Scheme (EPS) on this employee's record.";
        } else if (serviceStartDate == null) {
            eligibility = CpfEpsEligibilityStatus.REVIEW_REQUIRED;
            basis = "Date of joining is not on record - eligible service cannot be computed.";
        } else if (eligiblePeriod.getYears() >= minimumYears) {
            eligibility = CpfEpsEligibilityStatus.ELIGIBLE;
            basis = "Completed the configured minimum pensionable service of " + minimumYears + " years"
                    + " (EPS-95 Para 12 threshold - confirm against JCI's own CPF Trust/EPS scheme rules).";
        } else if (separationDate == null) {
            eligibility = CpfEpsEligibilityStatus.REVIEW_REQUIRED;
            basis = "Still in service with " + serviceLabel + " completed, short of the configured " + minimumYears
                    + "-year minimum - re-check on separation.";
        } else {
            eligibility = CpfEpsEligibilityStatus.NOT_ELIGIBLE;
            basis = "Separated with only " + serviceLabel + " of pensionable service, short of the configured " + minimumYears
                    + "-year minimum - a withdrawal benefit applies instead of monthly pension.";
        }

        return new CpfEpsEligibilityResponse(
                employee.getId(), employee.getFullName(), employee.getCpfAcNo(), employee.getUanNo(),
                employee.getDateOfBirth(), employee.getDateOfJoining(), separationDate,
                employee.isEpsEligible(), serviceLabel, eligibleServiceDays, serviceLabel,
                eligibility, basis);
    }

    /** Mirrors CpfTrustMemberDirectoryService's own separation-date resolution: COALESCE(latest exit-clearance release date, superannuation date), gated by the employee actually having separated. */
    private LocalDate resolveSeparationDate(Employee employee) {
        if (!SEPARATED_STATUSES.contains(employee.getStatus())) {
            return null;
        }
        LocalDate releaseOrderDate = exitClearanceRequestRepository.findByEmployeeId(employee.getId()).stream()
                .max(Comparator.comparing(r -> r.getInitiatedDate()))
                .map(r -> r.getReleaseOrderDate())
                .orElse(null);
        if (releaseOrderDate != null) {
            return releaseOrderDate;
        }
        return superannuationDetailsRepository.findByEmployeeId(employee.getId())
                .map(s -> s.getSuperannuationDate())
                .orElse(null);
    }

    private static String formatPeriod(Period period) {
        if (period.isZero() || period.isNegative()) {
            return "0 years, 0 months";
        }
        return period.getYears() + " year" + (period.getYears() == 1 ? "" : "s")
                + ", " + period.getMonths() + " month" + (period.getMonths() == 1 ? "" : "s");
    }
}
