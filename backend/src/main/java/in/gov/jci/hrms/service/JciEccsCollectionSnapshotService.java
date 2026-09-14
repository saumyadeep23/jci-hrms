package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.JciEccsCollectionBatchResponse;
import in.gov.jci.hrms.dto.JciEccsCollectionDetailResponse;
import in.gov.jci.hrms.entity.HrmsPayrollCycle;
import in.gov.jci.hrms.entity.JciEccsCollectionBatch;
import in.gov.jci.hrms.entity.JciEccsCollectionDetail;
import in.gov.jci.hrms.entity.JciEccsLoan;
import in.gov.jci.hrms.entity.JciEccsLoanProductCode;
import in.gov.jci.hrms.entity.JciEccsLoanSchedule;
import in.gov.jci.hrms.entity.JciEccsLoanStatus;
import in.gov.jci.hrms.entity.JciEccsMember;
import in.gov.jci.hrms.entity.JciEccsMembershipStatus;
import in.gov.jci.hrms.entity.PayrollBatch;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.JciEccsCollectionBatchRepository;
import in.gov.jci.hrms.repository.JciEccsCollectionDetailRepository;
import in.gov.jci.hrms.repository.JciEccsLoanRepository;
import in.gov.jci.hrms.repository.JciEccsLoanScheduleRepository;
import in.gov.jci.hrms.repository.JciEccsMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Generates and LOCKS the per-payroll-run collection snapshot (spec sections 1.2-1.4): one
 * {@link JciEccsCollectionBatch} plus one {@link JciEccsCollectionDetail} per contributing member, for
 * every ACTIVE JCIECCS member with a nonzero thrift amount and/or a due installment this cycle on an
 * ACTIVE Term/Emergency loan. Idempotent on payrollRunId - a repeat call returns the already-locked
 * snapshot verbatim, never recalculating (spec section 6) - the whole point of "locked and immutable" is
 * that Payroll always sees the same figures no matter how many times it asks.
 */
@Service
@Transactional(readOnly = true)
public class JciEccsCollectionSnapshotService {

    private final JciEccsCollectionBatchRepository batchRepository;
    private final JciEccsCollectionDetailRepository detailRepository;
    private final JciEccsMemberRepository memberRepository;
    private final JciEccsLoanRepository loanRepository;
    private final JciEccsLoanScheduleRepository scheduleRepository;
    private final CycleResolverService cycleResolverService;
    private final JciEccsLifecycleEventService lifecycleEventService;

    public JciEccsCollectionSnapshotService(JciEccsCollectionBatchRepository batchRepository,
                                             JciEccsCollectionDetailRepository detailRepository, JciEccsMemberRepository memberRepository,
                                             JciEccsLoanRepository loanRepository, JciEccsLoanScheduleRepository scheduleRepository,
                                             CycleResolverService cycleResolverService, JciEccsLifecycleEventService lifecycleEventService) {
        this.batchRepository = batchRepository;
        this.detailRepository = detailRepository;
        this.memberRepository = memberRepository;
        this.loanRepository = loanRepository;
        this.scheduleRepository = scheduleRepository;
        this.cycleResolverService = cycleResolverService;
        this.lifecycleEventService = lifecycleEventService;
    }

    @Transactional
    public JciEccsCollectionBatchResponse generateSnapshot(String payrollRunId, PayrollBatch payrollBatch, Long performedByEmployeeId) {
        Optional<JciEccsCollectionBatch> existing = batchRepository.findByPayrollRunId(payrollRunId);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        HrmsPayrollCycle cycle = cycleResolverService.resolveForYearMonth(payrollBatch.getSalYear(), payrollBatch.getSalMonth());

        JciEccsCollectionBatch batch = new JciEccsCollectionBatch(payrollRunId, cycle, performedByEmployeeId, BigDecimal.ZERO);
        batch = batchRepository.saveAndFlush(batch);

        BigDecimal totalExpected = BigDecimal.ZERO;
        for (JciEccsMember member : memberRepository.findByMembershipStatus(JciEccsMembershipStatus.ACTIVE)) {
            LoanDue termDue = dueFor(member, JciEccsLoanProductCode.TERM, cycle);
            LoanDue emergencyDue = dueFor(member, JciEccsLoanProductCode.EMERGENCY, cycle);
            BigDecimal thriftAmount = member.getThriftMonthlyAmount();

            boolean nothingDue = thriftAmount.signum() == 0 && termDue.isEmpty() && emergencyDue.isEmpty();
            if (nothingDue) {
                continue;
            }

            JciEccsCollectionDetail detail = new JciEccsCollectionDetail(batch, member.getEmployeeId(), member, thriftAmount,
                    termDue.loan(), termDue.principal(), termDue.interest(), emergencyDue.loan(), emergencyDue.principal(), emergencyDue.interest());
            detailRepository.save(detail);

            totalExpected = totalExpected.add(thriftAmount)
                    .add(BigDecimal.valueOf(termDue.principal())).add(termDue.interest())
                    .add(BigDecimal.valueOf(emergencyDue.principal())).add(emergencyDue.interest());
        }

        batch.setTotalExpectedAmount(totalExpected);
        batch = batchRepository.saveAndFlush(batch);

        lifecycleEventService.record("JCIECCS_COLLECTION_BATCH", batch.getId(), "COLLECTION_BATCH_LOCKED", null,
                toResponse(batch), payrollRunId, performedByEmployeeId, "Snapshot locked for cycle " + cycle.getCycleCode());

        return toResponse(batch);
    }

    public JciEccsCollectionBatchResponse getByPayrollRunId(String payrollRunId) {
        JciEccsCollectionBatch batch = batchRepository.findByPayrollRunId(payrollRunId)
                .orElseThrow(() -> new MasterDataNotFoundException("JCIECCS Collection Batch", payrollRunId));
        return toResponse(batch);
    }

    private LoanDue dueFor(JciEccsMember member, JciEccsLoanProductCode productCode, HrmsPayrollCycle cycle) {
        return loanRepository.findFirstByMember_IdAndLoanProduct_ProductCodeAndStatusOrderByCreatedAtDesc(
                        member.getId(), productCode, JciEccsLoanStatus.ACTIVE)
                .flatMap(loan -> scheduleRepository.findByLoan_IdAndCycle_Id(loan.getId(), cycle.getId())
                        .map(schedule -> new LoanDue(loan, schedule)))
                .orElse(LoanDue.EMPTY);
    }

    private JciEccsCollectionBatchResponse toResponse(JciEccsCollectionBatch batch) {
        List<JciEccsCollectionDetailResponse> details = detailRepository.findByBatch_IdOrderByEmployeeIdAsc(batch.getId())
                .stream().map(JciEccsCollectionDetailResponse::from).toList();
        return JciEccsCollectionBatchResponse.from(batch, details);
    }

    private record LoanDue(JciEccsLoan loan, int principal, BigDecimal interest) {
        static final LoanDue EMPTY = new LoanDue(null, 0, BigDecimal.ZERO);

        LoanDue(JciEccsLoan loan, JciEccsLoanSchedule schedule) {
            this(loan, schedule.getPrincipalDue(), schedule.getInterestDue());
        }

        boolean isEmpty() {
            return loan == null;
        }
    }
}
