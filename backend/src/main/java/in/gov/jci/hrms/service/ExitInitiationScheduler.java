package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeStatus;
import in.gov.jci.hrms.entity.EmployeeSuperannuationDetails;
import in.gov.jci.hrms.entity.ExitClearanceStatus;
import in.gov.jci.hrms.entity.SeparationType;
import in.gov.jci.hrms.repository.EmployeeSuperannuationDetailsRepository;
import in.gov.jci.hrms.repository.ExitClearanceRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Daily 90-day-ahead sweep: any still-ACTIVE employee superannuating within
 * the next 90 days who has no open (non-CANCELLED) exit clearance request
 * yet gets one auto-created as a draft, so HR always has advance notice
 * instead of discovering a retirement on the day it happens.
 */
@Service
public class ExitInitiationScheduler {

    private static final int LOOKAHEAD_DAYS = 90;

    private final EmployeeSuperannuationDetailsRepository superannuationDetailsRepository;
    private final ExitClearanceRequestRepository clearanceRequestRepository;
    private final ExitClearanceService exitClearanceService;
    private final Clock clock;

    @Autowired
    public ExitInitiationScheduler(EmployeeSuperannuationDetailsRepository superannuationDetailsRepository,
                                    ExitClearanceRequestRepository clearanceRequestRepository,
                                    ExitClearanceService exitClearanceService) {
        this(superannuationDetailsRepository, clearanceRequestRepository, exitClearanceService, Clock.systemDefaultZone());
    }

    ExitInitiationScheduler(EmployeeSuperannuationDetailsRepository superannuationDetailsRepository,
                             ExitClearanceRequestRepository clearanceRequestRepository,
                             ExitClearanceService exitClearanceService, Clock clock) {
        this.superannuationDetailsRepository = superannuationDetailsRepository;
        this.clearanceRequestRepository = clearanceRequestRepository;
        this.exitClearanceService = exitClearanceService;
        this.clock = clock;
    }

    @Scheduled(cron = "0 30 0 * * ?")
    public void runDailySweep() {
        runDailySweepFor(LocalDate.now(clock));
    }

    public void runDailySweepFor(LocalDate today) {
        List<EmployeeSuperannuationDetails> upcoming =
                superannuationDetailsRepository.findBySuperannuationDateBetween(today, today.plusDays(LOOKAHEAD_DAYS));

        for (EmployeeSuperannuationDetails sup : upcoming) {
            initiateIfNeeded(sup);
        }
    }

    @Transactional
    void initiateIfNeeded(EmployeeSuperannuationDetails sup) {
        Employee employee = sup.getEmployee();
        if (employee.getStatus() != EmployeeStatus.ACTIVE) {
            return;
        }
        if (clearanceRequestRepository.existsByEmployeeIdAndStatusNot(employee.getId(), ExitClearanceStatus.CANCELLED)) {
            return;
        }
        exitClearanceService.initiateExit(employee.getId(), SeparationType.SUPERANNUATION, sup.getSuperannuationDate(),
                "Auto-drafted by ExitInitiationScheduler - superannuation date " + sup.getSuperannuationDate());
    }
}
