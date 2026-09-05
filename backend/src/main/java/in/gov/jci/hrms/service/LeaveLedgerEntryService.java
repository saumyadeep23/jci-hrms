package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.LeaveLedgerEntryResponse;
import in.gov.jci.hrms.repository.LeaveLedgerEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Read-only - entries are only ever written by AttendanceLeaveDeductionService, never through this service. */
@Service
@Transactional(readOnly = true)
public class LeaveLedgerEntryService {

    private final LeaveLedgerEntryRepository leaveLedgerEntryRepository;

    public LeaveLedgerEntryService(LeaveLedgerEntryRepository leaveLedgerEntryRepository) {
        this.leaveLedgerEntryRepository = leaveLedgerEntryRepository;
    }

    public List<LeaveLedgerEntryResponse> listForEmployee(Long employeeId) {
        return leaveLedgerEntryRepository.findByEmployeeId(employeeId).stream()
                .map(LeaveLedgerEntryResponse::from)
                .toList();
    }
}
