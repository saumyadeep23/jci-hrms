package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeStatus;
import in.gov.jci.hrms.entity.ExitClearanceDepartment;
import in.gov.jci.hrms.entity.ExitClearanceItem;
import in.gov.jci.hrms.entity.ExitClearanceItemStatus;
import in.gov.jci.hrms.entity.ExitClearanceRequest;
import in.gov.jci.hrms.entity.ExitClearanceStatus;
import in.gov.jci.hrms.entity.SeparationType;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.ExitClearanceItemRepository;
import in.gov.jci.hrms.repository.ExitClearanceRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Exit Formalities: multi-department clearance checklist for a separation
 * event, ending in a release order that hands off to
 * EmployeeReleaseService. See exit_clearance_requests/exit_clearance_items
 * (V58 migration).
 */
@Service
@Transactional(readOnly = true)
public class ExitClearanceService {

    private final ExitClearanceRequestRepository clearanceRequestRepository;
    private final ExitClearanceItemRepository clearanceItemRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeReleaseService employeeReleaseService;

    public ExitClearanceService(ExitClearanceRequestRepository clearanceRequestRepository,
                                 ExitClearanceItemRepository clearanceItemRepository,
                                 EmployeeRepository employeeRepository,
                                 EmployeeReleaseService employeeReleaseService) {
        this.clearanceRequestRepository = clearanceRequestRepository;
        this.clearanceItemRepository = clearanceItemRepository;
        this.employeeRepository = employeeRepository;
        this.employeeReleaseService = employeeReleaseService;
    }

    /** Creates the request and provisions all 7 ExitClearanceDepartment items PENDING. */
    @Transactional
    public ExitClearanceRequest initiateExit(Long employeeId, SeparationType separationType, LocalDate targetReleaseDate, String remarks) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new MasterDataNotFoundException("Employee", employeeId));

        if (clearanceRequestRepository.existsByEmployeeIdAndStatusNot(employeeId, ExitClearanceStatus.CANCELLED)) {
            throw new BusinessRuleViolationException("Employee " + employeeId + " already has an open exit clearance request");
        }

        ExitClearanceRequest request = new ExitClearanceRequest(employee, separationType, targetReleaseDate, remarks);
        request = clearanceRequestRepository.save(request);

        for (ExitClearanceDepartment department : ExitClearanceDepartment.values()) {
            clearanceItemRepository.save(new ExitClearanceItem(request, department));
        }

        return request;
    }

    public List<ExitClearanceItem> checklist(Long clearanceRequestId) {
        return clearanceItemRepository.findByClearanceRequestId(clearanceRequestId);
    }

    public ExitClearanceRequest getRequest(Long clearanceRequestId) {
        return clearanceRequestRepository.findById(clearanceRequestId)
                .orElseThrow(() -> new MasterDataNotFoundException("ExitClearanceRequest", clearanceRequestId));
    }

    /** Most recently initiated request for an employee, if any - lets a caller (e.g. SuperannuationTab's Exit Formalities button) find or start a request without tracking the id itself. */
    public Optional<ExitClearanceRequest> findLatestForEmployee(Long employeeId) {
        return clearanceRequestRepository.findByEmployeeId(employeeId).stream()
                .max(java.util.Comparator.comparing(ExitClearanceRequest::getInitiatedDate).thenComparing(ExitClearanceRequest::getId));
    }

    /** One department's sign-off. Flips the parent request to CLEARANCE_IN_PROGRESS on the first non-PENDING item, and to CLEARANCES_COMPLETED once all 7 are CLEARED. */
    @Transactional
    public ExitClearanceItem updateDepartmentClearance(Long itemId, ExitClearanceItemStatus status, BigDecimal dues, String remarks, Long userId) {
        ExitClearanceItem item = clearanceItemRepository.findById(itemId)
                .orElseThrow(() -> new MasterDataNotFoundException("ExitClearanceItem", itemId));

        ExitClearanceRequest request = item.getClearanceRequest();
        if (request.getStatus() == ExitClearanceStatus.RELEASE_ORDER_ISSUED || request.getStatus() == ExitClearanceStatus.CANCELLED) {
            throw new BusinessRuleViolationException("Clearance request " + request.getId() + " is already finalized");
        }

        item.setStatus(status);
        item.setDuesRecoveryAmount(dues != null ? dues : BigDecimal.ZERO);
        item.setRemarks(remarks);
        item.setClearedByUserId(userId);
        item.setClearedAt(java.time.Instant.now());

        long clearedCount = clearanceItemRepository.countByClearanceRequestIdAndStatus(request.getId(), ExitClearanceItemStatus.CLEARED);
        long totalCount = clearanceItemRepository.countByClearanceRequestId(request.getId());

        if (clearedCount == totalCount) {
            request.setStatus(ExitClearanceStatus.CLEARANCES_COMPLETED);
        } else if (request.getStatus() == ExitClearanceStatus.INITIATED) {
            request.setStatus(ExitClearanceStatus.CLEARANCE_IN_PROGRESS);
        }

        return item;
    }

    /**
     * Transitions the request to RELEASE_ORDER_ISSUED and releases the
     * employee (post + pay fixation close-out, status flip) via
     * EmployeeReleaseService - see that class for why this is shared with
     * SuperannuationScheduledTask.
     */
    @Transactional
    public ExitClearanceRequest finalizeReleaseOrder(Long clearanceRequestId, String orderRef, LocalDate releaseDate) {
        ExitClearanceRequest request = getRequest(clearanceRequestId);
        if (request.getStatus() != ExitClearanceStatus.CLEARANCES_COMPLETED) {
            throw new BusinessRuleViolationException(
                    "Clearance request " + clearanceRequestId + " cannot be finalized until all department clearances are CLEARED (current status: " + request.getStatus() + ")");
        }

        request.setStatus(ExitClearanceStatus.RELEASE_ORDER_ISSUED);
        request.setReleaseOrderRefNo(orderRef);
        request.setReleaseOrderDate(releaseDate);

        employeeReleaseService.release(request.getEmployee(), releaseDate, targetStatus(request.getSeparationType()),
                "Exit clearance #" + clearanceRequestId + " release order " + orderRef);

        return request;
    }

    /** SeparationType has no VRS-equivalent EmployeeStatus - a voluntary retirement is still a retirement for status purposes. */
    private static EmployeeStatus targetStatus(SeparationType separationType) {
        return switch (separationType) {
            case SUPERANNUATION, VRS -> EmployeeStatus.RETIRED;
            case RESIGNATION -> EmployeeStatus.RESIGNED;
            case DECEASED -> EmployeeStatus.DECEASED;
            case TERMINATED -> EmployeeStatus.TERMINATED;
        };
    }
}
