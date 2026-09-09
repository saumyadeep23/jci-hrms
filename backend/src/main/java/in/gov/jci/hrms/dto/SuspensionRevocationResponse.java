package in.gov.jci.hrms.dto;

import java.math.BigDecimal;

/**
 * revokeAndRegularize()'s computed back-pay arrear - grossBackPayArrear is (full pay the employee would
 * have drawn across the suspension window) minus (subsistence allowance actually paid, read from real
 * Head 66 payroll history). This is a computed figure surfaced for HR/Finance to sanction, not yet an
 * actual disbursed arrear - EmployeeSuspensionRecord.isArrearsSettled()/getArrearsPayrollRun() track
 * whether a later payroll run has actually paid it; that disbursal pipeline itself is out of scope here
 * (there is no dedicated per-month arrear breakdown table for suspensions, unlike IDA arrears).
 */
public record SuspensionRevocationResponse(
        Long suspensionId,
        BigDecimal grossBackPayArrear,
        BigDecimal cpfDeductionOnArrear,
        BigDecimal netArrearPayable
) {
}
