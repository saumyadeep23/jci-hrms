package in.gov.jci.hrms.dto;

import java.util.List;

/** GET .../{id}/dependencies - PIMS_SPEC.md Section 1.B's "Dependency Guard" pre-check. */
public record DependencyCheckResponse(boolean hasActiveDependencies, List<DependencyUsage> usages) {
}
