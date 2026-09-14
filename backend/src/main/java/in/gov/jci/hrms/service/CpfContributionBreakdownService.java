package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.CpfLedgerEntryType;
import in.gov.jci.hrms.entity.CpfTrustMemberLedgerEntry;
import in.gov.jci.hrms.entity.PayrollMonthlyRecord;
import in.gov.jci.hrms.repository.PayrollMonthlyRecordRepository;
import in.gov.jci.hrms.repository.PayrollMonthlyStatutoryItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The single authoritative source for a CPF Trust member's EPS (Employees' Pension Scheme) contribution and
 * the FY contribution summary the Passbook V2 UI displays - Parts 4.3/4.4/16 of the module spec. Never
 * derives or recomputes EPS itself; it only reads the figure {@code PayrollBatchComputationService} already
 * computed and stored.
 *
 * <h2>Where EPS actually lives</h2>
 * {@code cpf_trust_member_ledger_entries} (the CPF Trust ledger this passbook is built on) has exactly
 * three running-balance buckets - EE, ER, VPF - and never carried a fourth "EPS" column, because EPS is not
 * part of the CPF Trust's own corpus: it is the Employees' Pension Scheme contribution administered by
 * EPFO, not the Trust. {@code PayrollBatchComputationService.resolveEmployerContributions()}'s own javadoc
 * establishes the authoritative split (verified by inspection before writing this class, not assumed): of
 * the employer's total EPF contribution (payroll_statutory_heads stat_head_count=1), whatever is
 * EPS-eligible is carved out into stat_head_count=4 ("Pension Fund" - this is EPS), and the REMAINDER (head
 * 1 minus head 4) is what lands in stat_head_count=3 ("JCPF") - which is exactly the figure
 * {@link CpfLedgerSyncService} already credits into the ledger's own ER bucket. In other words, the
 * ledger's existing ER credit is ALREADY net of EPS by construction - EPS is a genuinely separate,
 * already-computed, already-stored figure this class only needs to read alongside it, never subtract or
 * derive: {@code employerCpf(head1) = ledgerErCredit(head3, JCPF) + epsContribution(head4, Pension)}.
 *
 * <h2>Per-transaction EPS</h2>
 * A CPF ledger row's own {@code salMonth}/{@code salYear} (populated only for PAYROLL_MONTHLY/DA_ARREAR
 * rows) is the join key back to the specific {@link PayrollMonthlyRecord} that produced it, whose
 * {@code tranId} in turn keys {@code payroll_monthly_statutory_items}. This class always batches that
 * lookup (fetch every record for the relevant financial year's two calendar years in one query, then every
 * statutory item for all of their tranIds in one more) rather than querying per ledger row, to avoid an
 * N+1 across a passbook's worth of transactions (Part 25).
 */
@Service
@Transactional(readOnly = true)
public class CpfContributionBreakdownService {

    /** payroll_statutory_heads.stat_head_count = 4, "Pension Fund" - see this class's own javadoc. */
    private static final int STAT_HEAD_PENSION_EPS = 4;

    private final PayrollMonthlyRecordRepository payrollMonthlyRecordRepository;
    private final PayrollMonthlyStatutoryItemRepository statutoryItemRepository;

    public CpfContributionBreakdownService(PayrollMonthlyRecordRepository payrollMonthlyRecordRepository,
                                            PayrollMonthlyStatutoryItemRepository statutoryItemRepository) {
        this.payrollMonthlyRecordRepository = payrollMonthlyRecordRepository;
        this.statutoryItemRepository = statutoryItemRepository;
    }

    /** employeeContribution/employerContribution/vpfContribution/totalContribution/epsContribution - Part 4.3's "Current FY Contribution Summary". employee/employer/vpf are summed directly from the FY's own already-posted ledger credits (PAYROLL_MONTHLY/DA_ARREAR); eps is looked up separately per this class's own javadoc. totalContribution intentionally adds all four - EPS is additive, never subtracted from or folded into employerContribution, since the ledger's employer figure is already net of it. */
    public record ContributionSummary(
            BigDecimal employeeContribution,
            BigDecimal employerContribution,
            BigDecimal epsContribution,
            BigDecimal vpfContribution,
            BigDecimal totalContribution
    ) {
    }

    public ContributionSummary summaryFor(Long employeeId, String finYear, List<CpfTrustMemberLedgerEntry> finYearLedgerEntries) {
        BigDecimal employeeContribution = BigDecimal.ZERO;
        BigDecimal employerContribution = BigDecimal.ZERO;
        BigDecimal vpfContribution = BigDecimal.ZERO;
        for (CpfTrustMemberLedgerEntry entry : finYearLedgerEntries) {
            if (entry.getEntryType() == CpfLedgerEntryType.PAYROLL_MONTHLY || entry.getEntryType() == CpfLedgerEntryType.DA_ARREAR) {
                employeeContribution = employeeContribution.add(entry.getEeShareCredit());
                employerContribution = employerContribution.add(entry.getErShareCredit());
                vpfContribution = vpfContribution.add(entry.getVpfCredit());
            }
        }
        BigDecimal epsContribution = epsForFinYear(employeeId, finYear);
        BigDecimal total = employeeContribution.add(employerContribution).add(epsContribution).add(vpfContribution);
        return new ContributionSummary(employeeContribution, employerContribution, epsContribution, vpfContribution, total);
    }

    /** Total EPS contribution for one employee across a whole financial year (April-March). */
    public BigDecimal epsForFinYear(Long employeeId, String finYear) {
        int startYear = Integer.parseInt(finYear.substring(0, 4));
        int endYear = startYear + 1;
        List<PayrollMonthlyRecord> records = payrollMonthlyRecordRepository.findByEmployee_IdAndYearIn(employeeId, List.of(startYear, endYear));
        List<Long> tranIds = records.stream()
                .filter(r -> isWithinFinYear(r.getYear(), r.getMonth(), startYear, endYear))
                .map(PayrollMonthlyRecord::getTranId)
                .toList();
        if (tranIds.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return statutoryItemRepository.findByRecord_TranIdIn(tranIds).stream()
                .filter(item -> item.getStatHeadCount() == STAT_HEAD_PENSION_EPS)
                .map(item -> item.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Batch EPS-per-ledger-transaction map for the passbook transaction list/detail view (Part 25: avoid
     * N+1). Only PAYROLL_MONTHLY/DA_ARREAR rows with a populated salMonth/salYear can ever have an EPS
     * figure; every other entry type (interest, loan, withdrawal, transfer) is simply absent from the
     * returned map rather than mapped to a fabricated zero, so callers can distinguish "not applicable"
     * from "genuinely zero contribution that month".
     */
    public Map<Long, BigDecimal> epsByLedgerEntry(Long employeeId, List<CpfTrustMemberLedgerEntry> entries) {
        Map<Long, BigDecimal> result = new HashMap<>();
        List<CpfTrustMemberLedgerEntry> payrollEntries = entries.stream()
                .filter(e -> e.getSalMonth() != null && e.getSalYear() != null)
                .toList();
        if (payrollEntries.isEmpty()) {
            return result;
        }
        List<Integer> years = payrollEntries.stream().map(CpfTrustMemberLedgerEntry::getSalYear).distinct().toList();
        List<PayrollMonthlyRecord> records = payrollMonthlyRecordRepository.findByEmployee_IdAndYearIn(employeeId, years);

        Map<YearMonth, Long> tranIdByYearMonth = new HashMap<>();
        for (PayrollMonthlyRecord record : records) {
            tranIdByYearMonth.put(YearMonth.of(record.getYear(), record.getMonth()), record.getTranId());
        }
        List<Long> tranIds = tranIdByYearMonth.values().stream().toList();
        if (tranIds.isEmpty()) {
            return result;
        }
        Map<Long, BigDecimal> epsByTranId = new HashMap<>();
        for (var item : statutoryItemRepository.findByRecord_TranIdIn(tranIds)) {
            if (item.getStatHeadCount() == STAT_HEAD_PENSION_EPS) {
                epsByTranId.merge(item.getRecord().getTranId(), item.getAmount(), BigDecimal::add);
            }
        }

        for (CpfTrustMemberLedgerEntry entry : payrollEntries) {
            Long tranId = tranIdByYearMonth.get(YearMonth.of(entry.getSalYear(), entry.getSalMonth()));
            if (tranId != null) {
                result.put(entry.getId(), epsByTranId.getOrDefault(tranId, BigDecimal.ZERO));
            }
        }
        return result;
    }

    private static boolean isWithinFinYear(int year, int month, int startYear, int endYear) {
        return (year == startYear && month >= 4) || (year == endYear && month <= 3);
    }
}
