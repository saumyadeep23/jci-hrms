package in.gov.jci.hrms.dto;

/** One referencing table found to still have active rows pointing at a master record. */
public record DependencyUsage(String table, String label, long activeCount) {
}
