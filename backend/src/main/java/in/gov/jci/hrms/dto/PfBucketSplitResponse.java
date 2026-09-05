package in.gov.jci.hrms.dto;

import java.math.BigDecimal;

public record PfBucketSplitResponse(
        BigDecimal employeeEpf,
        BigDecimal employerEpf,
        BigDecimal employerEps
) {
}
