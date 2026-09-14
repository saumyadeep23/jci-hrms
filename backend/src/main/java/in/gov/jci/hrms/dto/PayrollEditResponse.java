package in.gov.jci.hrms.dto;

import java.math.BigDecimal;
import java.util.List;

/** Response for both the edit-preview (no persistence) and edit (persisted) endpoints - changedHeads holds only the heads whose amount actually moved (BASIC itself, plus any of DA/HRA/CPF/JCPF/P.Tax the cascade touched). */
public record PayrollEditResponse(
        Long tranId,
        List<PayrollHeadLineResponse> changedHeads,
        BigDecimal grossAmount,
        BigDecimal totalDeductions,
        BigDecimal netAmount) {
}
