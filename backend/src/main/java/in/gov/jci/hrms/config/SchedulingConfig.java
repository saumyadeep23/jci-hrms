package in.gov.jci.hrms.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables @Scheduled - ElAccrualService's semi-annual EL accrual job (PIMS
 * ALMS Phase 2, Section 2) is the first scheduled job in this codebase;
 * everything before it (AttendanceLeaveDeductionService etc.) ran inline
 * from a controller-triggered call, not on a timer.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
