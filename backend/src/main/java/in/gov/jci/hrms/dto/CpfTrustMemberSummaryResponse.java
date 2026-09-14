package in.gov.jci.hrms.dto;

import java.math.BigDecimal;

/**
 * GET /api/v1/payroll/trust/members/summary - the CPF Trust Members' List KPI strip. Always computed
 * against the full membership (ignores whatever search/filter the table itself currently has applied) so
 * the cards read as fixed at-a-glance totals that filter the table when clicked, rather than a number that
 * changes depending on what's already been typed into the search box.
 */
public record CpfTrustMemberSummaryResponse(
        long totalMembers,
        long activeAccounts,
        long separatedMembers,
        long pendingSettlement,
        long overdueSettlement,
        long uanMissing,
        BigDecimal totalCpfBalance
) {
}
