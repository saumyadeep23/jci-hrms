package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.JciEccsMembershipStatus;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * PUT /api/jcieccs/members/{id}/status. remarks is validated as required only when {@code status} is
 * SUSPENDED or CLOSED (Bye-laws 15/16) - a plain @NotBlank here would also wrongly require it on
 * reactivation back to ACTIVE, so that check lives in JciEccsMemberService instead.
 */
public record JciEccsMemberStatusChangeRequest(
        @NotNull JciEccsMembershipStatus status,
        @NotNull LocalDate effectiveDate,
        String remarks
) {
}
