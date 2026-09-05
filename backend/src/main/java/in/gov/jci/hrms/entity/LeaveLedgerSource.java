package in.gov.jci.hrms.entity;

public enum LeaveLedgerSource {
    AUTO_LATE_DEDUCTION,
    COMMUTED_LEAVE_HPL_DEBIT,
    BASELINE_TAKEON,
    EL_SEMI_ANNUAL_ACCRUAL,
    EL_EOL_LAPSE_DEDUCTION,
    EL_ENCASHMENT_DEBIT,
    ATTENDANCE_PENALTY_REFUND,
    /** JoiningReportService: unavailed Joining Time credited to EL on an administrative transfer with benefit admissible, subject to the 300-day ceiling. */
    TRANSFER_JT_CONVERSION,
    /** TerminalSettlementService.approve(): DoPT Rule 39 EL/HPL encashment debited on terminal settlement approval - distinct from EL_ENCASHMENT_DEBIT (the ordinary discretionary LeaveEncashmentApplication workflow), which never applies to a separating employee's HPL and goes through its own reserve-then-finalize flow. */
    TERMINAL_ENCASHMENT
}
