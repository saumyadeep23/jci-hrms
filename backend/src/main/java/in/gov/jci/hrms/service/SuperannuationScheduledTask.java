package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeStatus;
import in.gov.jci.hrms.entity.EmployeeSuperannuationDetails;
import in.gov.jci.hrms.repository.EmployeeSuperannuationDetailsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Daily hard safety net: any still-ACTIVE employee whose
 * employee_superannuation_details.superannuation_date has already passed
 * is transitioned to RETIRED regardless of whether an Exit Formalities
 * clearance workflow ever ran for them - see EmployeeReleaseService for
 * why this shares its close-out logic with
 * ExitClearanceService.finalizeReleaseOrder() (the clearance-gated path).
 * Runs unconditionally so a REGULAR employee can never keep drawing an
 * increment or an active payroll salary past their statutory
 * superannuation date purely because HR never ran the exit workflow.
 */
@Service
public class SuperannuationScheduledTask {

    private final EmployeeSuperannuationDetailsRepository superannuationDetailsRepository;
    private final EmployeeReleaseService employeeReleaseService;
    private final Clock clock;

    @Autowired
    public SuperannuationScheduledTask(EmployeeSuperannuationDetailsRepository superannuationDetailsRepository,
                                        EmployeeReleaseService employeeReleaseService) {
        this(superannuationDetailsRepository, employeeReleaseService, Clock.systemDefaultZone());
    }

    /** Package-visible so a test (or an admin re-run endpoint) can drive this for an arbitrary "today" without waiting for the cron trigger - mirrors ElAccrualService's Clock pattern. */
    SuperannuationScheduledTask(EmployeeSuperannuationDetailsRepository superannuationDetailsRepository,
                                 EmployeeReleaseService employeeReleaseService, Clock clock) {
        this.superannuationDetailsRepository = superannuationDetailsRepository;
        this.employeeReleaseService = employeeReleaseService;
        this.clock = clock;
    }

    @Scheduled(cron = "0 5 0 * * ?")
    public void runDailySweep() {
        runDailySweepFor(LocalDate.now(clock));
    }

    public void runDailySweepFor(LocalDate today) {
        List<EmployeeSuperannuationDetails> overdue = superannuationDetailsRepository.findBySuperannuationDateLessThan(today);
        for (EmployeeSuperannuationDetails sup : overdue) {
            releaseIfStillActive(sup);
        }
    }

    @Transactional
    void releaseIfStillActive(EmployeeSuperannuationDetails sup) {
        Employee employee = sup.getEmployee();
        if (employee.getStatus() != EmployeeStatus.ACTIVE) {
            return;
        }
        employeeReleaseService.release(employee, sup.getSuperannuationDate(), EmployeeStatus.RETIRED,
                "Employee " + employee.getEmployeeCode() + " automatically transitioned to RETIRED (superannuation date "
                        + sup.getSuperannuationDate() + " reached)");
    }
}
