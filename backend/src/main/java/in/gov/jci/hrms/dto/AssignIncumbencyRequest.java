package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.AssignmentType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * POST /api/v1/posts/:id/incumbency - PIMS_SPEC.md Section 2's Dual/
 * Additional Charge assignment. postId comes from the path, not this body.
 * orderDate has no dedicated column on post_incumbency (only a single
 * free-text order_reference) - PostMasterController folds it into
 * orderReference as "<orderReference> (dated <orderDate>)" rather than
 * silently dropping it.
 */
public record AssignIncumbencyRequest(
        @NotNull Long employeeId,
        @NotNull AssignmentType assignmentType,
        @NotNull LocalDate startDate,
        @Size(max = 100) String orderReference,
        LocalDate orderDate
) {
}
