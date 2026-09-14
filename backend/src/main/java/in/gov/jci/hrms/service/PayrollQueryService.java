package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.PayrollHeadLineResponse;
import in.gov.jci.hrms.entity.PayrollMonthlyHeadItem;
import in.gov.jci.hrms.entity.PayrollMonthlyStatutoryItem;
import in.gov.jci.hrms.entity.SalaryHead;
import in.gov.jci.hrms.entity.SalaryHeadEffectType;
import in.gov.jci.hrms.entity.StatutoryHead;
import in.gov.jci.hrms.repository.PayrollMonthlyHeadItemRepository;
import in.gov.jci.hrms.repository.PayrollMonthlyStatutoryItemRepository;
import in.gov.jci.hrms.repository.SalaryHeadRepository;
import in.gov.jci.hrms.repository.StatutoryHeadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Read-side reconciliation matrix: every EARNING/DEDUCTION SalaryHead and every StatutoryHead,
 * left-joined against whatever was actually posted for one payroll_monthly_records row, so a head
 * with nothing posted comes back as amount 0.00 rather than being silently omitted (only non-zero
 * heads are persisted at all - see PayrollMonthlyHeadItem's own javadoc). NO_EFFECT salary heads
 * (e.g. DAYS_PRESENT) are excluded - they never carry a rupee amount and have no place on a
 * reconciliation sheet.
 */
@Service
@Transactional(readOnly = true)
public class PayrollQueryService {

    private final SalaryHeadRepository salaryHeadRepository;
    private final StatutoryHeadRepository statutoryHeadRepository;
    private final PayrollMonthlyHeadItemRepository headItemRepository;
    private final PayrollMonthlyStatutoryItemRepository statutoryItemRepository;

    public PayrollQueryService(SalaryHeadRepository salaryHeadRepository, StatutoryHeadRepository statutoryHeadRepository,
                                PayrollMonthlyHeadItemRepository headItemRepository,
                                PayrollMonthlyStatutoryItemRepository statutoryItemRepository) {
        this.salaryHeadRepository = salaryHeadRepository;
        this.statutoryHeadRepository = statutoryHeadRepository;
        this.headItemRepository = headItemRepository;
        this.statutoryItemRepository = statutoryItemRepository;
    }

    public List<PayrollHeadLineResponse> fullHeadLines(Long tranId) {
        Map<Integer, BigDecimal> postedHeads = headItemRepository.findByRecord_TranId(tranId).stream()
                .collect(Collectors.toMap(PayrollMonthlyHeadItem::getHeadCount, PayrollMonthlyHeadItem::getAmount, (a, b) -> a));
        Map<Integer, BigDecimal> postedStatutory = statutoryItemRepository.findByRecord_TranId(tranId).stream()
                .collect(Collectors.toMap(PayrollMonthlyStatutoryItem::getStatHeadCount, PayrollMonthlyStatutoryItem::getAmount, (a, b) -> a));

        List<PayrollHeadLineResponse> lines = salaryHeadRepository.findAllByOrderByHeadCountAsc().stream()
                .filter(head -> head.getEffectType() != SalaryHeadEffectType.NO_EFFECT)
                .map(head -> toLine(head, postedHeads.getOrDefault(head.getHeadCount(), BigDecimal.ZERO)))
                .collect(Collectors.toCollection(java.util.ArrayList::new));

        statutoryHeadRepository.findAllByOrderByStatHeadCountAsc().stream()
                .map(head -> toLine(head, postedStatutory.getOrDefault(head.getStatHeadCount(), BigDecimal.ZERO)))
                .forEach(lines::add);

        return lines;
    }

    private PayrollHeadLineResponse toLine(SalaryHead head, BigDecimal amount) {
        return new PayrollHeadLineResponse(head.getHeadCount(), head.getShortName(), head.getDescription(),
                head.getEffectType().name(), amount.setScale(2, java.math.RoundingMode.HALF_UP));
    }

    private PayrollHeadLineResponse toLine(StatutoryHead head, BigDecimal amount) {
        return new PayrollHeadLineResponse(head.getStatHeadCount(), head.getStatHeadShortName(), head.getStatHeadDescr(),
                "STATUTORY", amount.setScale(2, java.math.RoundingMode.HALF_UP));
    }
}
