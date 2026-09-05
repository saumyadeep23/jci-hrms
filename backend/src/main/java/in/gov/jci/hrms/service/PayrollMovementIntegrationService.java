package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.PayrollMovementInputResponse;
import in.gov.jci.hrms.entity.CityClass;
import in.gov.jci.hrms.entity.EmployeeMovementRecord;
import in.gov.jci.hrms.entity.PayrollMovementInput;
import in.gov.jci.hrms.entity.SessionType;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.repository.PayrollMovementInputRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Tab 4 - Payroll &amp; LPC Clearance. Generates the payroll_movement_inputs row(s)
 * PayrollComputationService's monthly run needs to split a mid-cycle transfer's pay between the
 * releasing and receiving office, apply the destination office's HRA city-class tier, and pass
 * through payable transit-JT vs. deductible transit-LWP days - see PayrollMovementInput's javadoc
 * for why this is a separate bridge table rather than a change to PayrollComputationService itself.
 */
@Service
@Transactional(readOnly = true)
public class PayrollMovementIntegrationService {

    private final PayrollMovementInputRepository payrollMovementInputRepository;

    public PayrollMovementIntegrationService(PayrollMovementInputRepository payrollMovementInputRepository) {
        this.payrollMovementInputRepository = payrollMovementInputRepository;
    }

    /**
     * Called from JoiningReportService.approve(). Release and joining almost always fall in the
     * same payroll month (JT is at most 15 days); when they don't, this writes one row for the
     * release month (releasing days only) and one for the join month (receiving days only) instead
     * of forcing an artificial single-month split.
     */
    @Transactional
    public List<PayrollMovementInput> generateInputs(EmployeeMovementRecord movement) {
        if (movement.getReleaseDate() == null || movement.getJoiningDate() == null) {
            throw new BusinessRuleViolationException(
                    "Movement " + movement.getId() + " has no release/joining date - cannot generate payroll inputs");
        }

        YearMonth releaseMonth = YearMonth.from(movement.getReleaseDate());
        YearMonth joinMonth = YearMonth.from(movement.getJoiningDate());

        CityClass hraTier = movement.getToOffice().getCityClass();
        int transitJtDays = Math.min(movement.getJoiningTimeAvailedDays(), movement.getAdmissibleJtDays());
        int transitLwpDays = movement.getExcessTransitLwpDays();

        if (releaseMonth.equals(joinMonth)) {
            PayrollMovementInput input = upsert(movement, releaseMonth);
            input.setReleasingOffice(movement.getFromOffice());
            input.setReleasingOfficeDays(releasingOfficeDays(movement.getReleaseDate(), movement.getReleaseSession()));
            input.setReceivingOffice(movement.getToOffice());
            input.setReceivingOfficeDays(receivingOfficeDays(movement.getJoiningDate(), movement.getJoiningSession()));
            applyPayAndTransit(input, movement, hraTier, transitJtDays, transitLwpDays);
            return List.of(payrollMovementInputRepository.saveAndFlush(input));
        }

        PayrollMovementInput releaseInput = upsert(movement, releaseMonth);
        releaseInput.setReleasingOffice(movement.getFromOffice());
        releaseInput.setReleasingOfficeDays(releasingOfficeDays(movement.getReleaseDate(), movement.getReleaseSession()));
        releaseInput.setTransitJtDays(transitJtDays);
        releaseInput.setTransitLwpDays(transitLwpDays);

        PayrollMovementInput joinInput = upsert(movement, joinMonth);
        joinInput.setReceivingOffice(movement.getToOffice());
        joinInput.setReceivingOfficeDays(receivingOfficeDays(movement.getJoiningDate(), movement.getJoiningSession()));
        applyPayAndTransit(joinInput, movement, hraTier, 0, 0);

        return List.of(payrollMovementInputRepository.saveAndFlush(releaseInput), payrollMovementInputRepository.saveAndFlush(joinInput));
    }

    private void applyPayAndTransit(PayrollMovementInput input, EmployeeMovementRecord movement, CityClass hraTier,
                                     int transitJtDays, int transitLwpDays) {
        input.setRevisedHraTier(hraTier);
        input.setRevisedBasicPay(movement.getPromotionalBasicPay());
        input.setTransitJtDays(transitJtDays);
        input.setTransitLwpDays(transitLwpDays);
    }

    /** Relieved in the afternoon -&gt; worked (and is payable for) that whole day at the old office; relieved in the forenoon -&gt; the old office's last payable day is the one before. */
    private int releasingOfficeDays(LocalDate releaseDate, SessionType releaseSession) {
        int dayOfMonth = releaseDate.getDayOfMonth();
        return releaseSession == SessionType.AFTERNOON ? dayOfMonth : Math.max(0, dayOfMonth - 1);
    }

    /** Symmetric with releasingOfficeDays: joined in the forenoon -&gt; the new office pays from that day; joined in the afternoon -&gt; from the following day. */
    private int receivingOfficeDays(LocalDate joiningDate, SessionType joiningSession) {
        int totalDaysInMonth = YearMonth.from(joiningDate).lengthOfMonth();
        int firstPayableDay = joiningSession == SessionType.FORENOON ? joiningDate.getDayOfMonth() : joiningDate.getDayOfMonth() + 1;
        return Math.max(0, totalDaysInMonth - firstPayableDay + 1);
    }

    private PayrollMovementInput upsert(EmployeeMovementRecord movement, YearMonth month) {
        return payrollMovementInputRepository
                .findByMovementIdAndPayMonthAndPayYear(movement.getId(), month.getMonthValue(), month.getYear())
                .orElseGet(() -> new PayrollMovementInput(movement, movement.getEmployee(), month.getMonthValue(), month.getYear()));
    }

    public List<PayrollMovementInputResponse> forMonth(int year, int month) {
        return payrollMovementInputRepository.findByPayYearAndPayMonthOrderByCreatedAtAsc(year, month).stream()
                .map(PayrollMovementInputResponse::from)
                .toList();
    }

    public List<PayrollMovementInputResponse> forMovement(Long movementId) {
        return payrollMovementInputRepository.findByMovementIdOrderByPayYearAscPayMonthAsc(movementId).stream()
                .map(PayrollMovementInputResponse::from)
                .toList();
    }
}
