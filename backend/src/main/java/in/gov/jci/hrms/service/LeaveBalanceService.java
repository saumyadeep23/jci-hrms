package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.LeaveBalanceResponse;
import in.gov.jci.hrms.repository.LeaveBalanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Read-only - balances are only ever mutated by LeaveApplicationService/AttendanceLeaveDeductionService/legacy migration, never through this service. */
@Service
@Transactional(readOnly = true)
public class LeaveBalanceService {

    private final LeaveBalanceRepository leaveBalanceRepository;

    public LeaveBalanceService(LeaveBalanceRepository leaveBalanceRepository) {
        this.leaveBalanceRepository = leaveBalanceRepository;
    }

    public List<LeaveBalanceResponse> listForEmployee(Long employeeId, int year) {
        return leaveBalanceRepository.findByEmployeeIdAndYear(employeeId, year).stream()
                .map(LeaveBalanceResponse::from)
                .toList();
    }
}
