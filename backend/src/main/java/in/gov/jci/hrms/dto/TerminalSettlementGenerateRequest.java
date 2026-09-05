package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.SeparationType;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/** cpfAccruedInterest is an optional manual override (CPF Trust doesn't compute interest anywhere in this codebase yet) - null means 0.00, see TerminalSettlementService. */
public record TerminalSettlementGenerateRequest(
        @NotNull SeparationType separationType,
        @NotNull LocalDate separationDate,
        Long clearanceRequestId,
        BigDecimal cpfAccruedInterest
) {
}
