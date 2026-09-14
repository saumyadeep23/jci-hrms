package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.PayrollBatchCreateRequest;
import in.gov.jci.hrms.dto.PayrollBatchResponse;
import in.gov.jci.hrms.entity.PayrollBatch;
import in.gov.jci.hrms.entity.PayrollBatchType;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.PayrollBatchRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Batch create/list - the two lifecycle endpoints PayrollBatchController never had (a payroll_batches
 * row previously had to already exist, by some other means, before /calculate could be called). Kept
 * as its own service rather than folded into PayrollBatchComputationService, which owns computation,
 * not batch bookkeeping.
 */
@Service
@Transactional(readOnly = true)
public class PayrollBatchService {

    private static final String ENTITY_NAME = "Payroll Batch";

    private final PayrollBatchRepository payrollBatchRepository;

    public PayrollBatchService(PayrollBatchRepository payrollBatchRepository) {
        this.payrollBatchRepository = payrollBatchRepository;
    }

    @Transactional
    public PayrollBatchResponse create(PayrollBatchCreateRequest request) {
        int salYear = request.cycleYear();
        int salMonth = request.cycleMonth();
        if (payrollBatchRepository.findBySalMonthAndSalYear(salMonth, salYear).isPresent()) {
            throw new MasterDataConflictException(ENTITY_NAME + " already exists for " + salYear + "-" + salMonth);
        }

        String batchNo = "PB-" + salYear + "-" + String.format("%02d", salMonth);
        LocalDate financialYearAnchor = YearMonth.of(salYear, salMonth).atEndOfMonth();
        String financialYear = IncomingFundTransferService.financialYearFor(financialYearAnchor);

        PayrollBatch batch = new PayrollBatch(batchNo, salMonth, salYear, financialYear);
        batch.setBatchType(request.batchType() != null ? request.batchType() : PayrollBatchType.REGULAR);
        batch.setPayDate(request.payDate());
        return PayrollBatchResponse.from(payrollBatchRepository.saveAndFlush(batch));
    }

    public PayrollBatchResponse getById(Long id) {
        return PayrollBatchResponse.from(payrollBatchRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException(ENTITY_NAME, id)));
    }

    public Page<PayrollBatchResponse> list(Pageable pageable) {
        Pageable ordered = pageable.getSort().isSorted() ? pageable
                : org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "salYear").and(Sort.by(Sort.Direction.DESC, "salMonth")));
        return payrollBatchRepository.findAll(ordered).map(PayrollBatchResponse::from);
    }
}
