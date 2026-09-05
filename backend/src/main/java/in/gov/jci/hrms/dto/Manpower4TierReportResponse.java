package in.gov.jci.hrms.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * GET /api/v1/reports/pims/manpower-4tier. totalOutsourcedVendorBilling is
 * the one cross-tier aggregate reported directly (sum of
 * employee_employment_categories.billing_rate_monthly for OUTSOURCED) since
 * that's the only cost figure genuinely comparable across vendors/agencies -
 * the other tiers' costs use incompatible units (see ManpowerTierRow).
 */
public record Manpower4TierReportResponse(List<ManpowerTierRow> tiers, long totalHeadcount, BigDecimal totalOutsourcedVendorBilling) {
}
