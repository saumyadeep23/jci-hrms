# Data Flow Diagrams — PIMS & ALMS

Grounded in the actual backend (`backend/src/main/java/in/gov/jci/hrms`) and migrations
(`backend/src/main/resources/db/migration`) as of migration V60. Where the source naming
differs from common ALMS terminology, the real name is used and the assumed name is noted.

> **Naming corrections vs. common assumptions**
> - There is no `attendance_punches` table — the real table is `mobile_punches` (V7), written by
>   `MobilePunchService` from the ESS mobile app's geofenced IN/OUT punches.
> - There is no `alms_attendance_summaries` table — `daily_attendance` (V7, extended V14/V36) is
>   the one-row-per-employee-per-day summary; V36's own header comment explicitly rejects adding
>   a duplicate table.
> - "Roster shifts" = `shift_master` (V43) + `employee_shift_schedule` (V44), not a single
>   `roster_shifts` table.
> - "Holidays master" = `holidays` (V14), not `holidays_master`.

---

## Level 0 — System Context Diagram

```mermaid
flowchart TD
    EMP[["Employee\n(ESS mobile app / web portal)"]]
    HOD[["Nodal Officer / HOD\n(approver)"]]
    HRA[["HR Admin"]]
    FIN[["Finance / CPF Admin"]]
    DEVICE[["Biometric / GPS Punch Device\n(mobile app geofence capture)"]]
    BANK[["Bank / Disbursement\n(salary + terminal settlement payout)"]]

    HRMS(("JCI HRMS\nPIMS + ALMS + Payroll"))

    EMP -- "punches (IN/OUT, lat/long)" --> HRMS
    DEVICE -- "geofenced punch events" --> HRMS
    EMP -- "leave / encashment / regularization applications" --> HRMS
    HOD -- "approve / reject clearances, leave, regularization" --> HRMS
    HRA -- "onboarding, master data, exit clearance, service book" --> HRMS
    FIN -- "CPF ledger, terminal settlement approval" --> HRMS
    HRMS -- "payslip, LOP, muster data" --> FIN
    HRMS -- "bank disbursement CSV\n(PayrollReportingService)" --> BANK
    HRMS -- "profile, leave balance, payslip view" --> EMP
```

**Verified touchpoint**: `PayrollReportingService` generates a bank disbursement CSV that
excludes on-hold (`is_hold`) payslips — confirming the Bank Remittance external entity is real,
not assumed. There is no confirmed inbound feed *from* a bank (no reconciliation/NEFT-return
ingestion found) — the Bank arrow is one-way (HRMS → Bank) as implemented today.

---

## Level 1 — Module Decomposition

```mermaid
flowchart TB
    subgraph PIMS["PIMS — Personnel Information Management"]
        direction TB
        P1["EmployeeService / EmployeeOnboardingService"]
        P2["PostIncumbencyService / MovementOrderService"]
        P3["RegularPayFixation ledger\n(pay fixation on promotion/increment)"]
        P4["ExitClearanceService"]
        P5["TerminalSettlementService"]
        P6["ExitInitiationScheduler (00:30 daily)\nSuperannuationScheduledTask (00:05 daily)"]
        DS1[("employees\nemployee_employment_categories\npost_master / post_incumbency\nregular_pay_fixations\ngrade_scale_master\nemployee_service_book\nemployee_nominees")]
        DS2[("exit_clearance_requests\nexit_clearance_items\nterminal_settlements\nterminal_settlement_beneficiaries")]
    end

    subgraph ALMS["ALMS — Attendance & Leave Management"]
        direction TB
        A1["MobilePunchService"]
        A2["AttendanceAggregationService\n(per-day pipeline engine)"]
        A3["AttendanceLeaveDeductionService\n(CL→EL→LWP cascade)"]
        A4["LeaveApplicationService"]
        A5["ElAccrualService (01-Jan / 01-Jul)"]
        A6["LeaveEncashmentApplication workflow\n(HR + Finance dual approval)"]
        DS3[("mobile_punches\ndaily_attendance\nshift_master / employee_shift_schedule\nholidays")]
        DS4[("leave_types / leave_balances\nleave_entitlement_balance\nleave_applications\nleave_ledger_entries\nleave_encashment_application")]
    end

    subgraph PAYROLL["Payroll (consumer, not part of PIMS/ALMS)"]
        PR1["PayrollComputationService.computeLopDays()"]
    end

    A1 --> A2
    A2 --> DS3
    A2 -- "LATE_SHORT_HOURS / UNAUTHORIZED_LATE" --> A3
    A3 --> DS4
    A4 --> DS4
    A5 --> DS4
    A6 --> DS4
    A6 -. "service_book_entry_id (FK)" .-> DS1

    DS3 -- "daily_attendance.status\n(ABSENT / HALF_DAY count)" --> PR1
    P3 -- "current basic pay / grade\n(RegularPayFixationRepository)" --> PR1

    P1 --> DS1
    P2 --> DS1
    P3 --> DS1
    P4 --> DS2
    P5 --> DS2
    P6 --> P4
    P5 -- "TERMINAL_ENCASHMENT ledger debit" --> DS4
```

**Interface points confirmed in code**:
- ALMS → Payroll: `PayrollComputationService.computeLopDays()` reads `daily_attendance.status`
  (coarse `AttendanceStatus`) directly — counts `ABSENT` as 1.0 day, `HALF_DAY` as 0.5. This is
  the *only* verified ALMS→Payroll bridge; `attendance_payroll_cutoff` (freeze window table) has
  no writer anywhere in the codebase and is dead schema.
- PIMS → Payroll: `PayrollComputationService.resolveCurrentGradeScale()` reads
  `regular_pay_fixations` (via `RegularPayFixationRepository.findByEmployeeIdAndCurrentTrue`).
- PIMS → ALMS: `TerminalSettlementService.approve()` writes a `TERMINAL_ENCASHMENT`
  `leave_ledger_entries` row when it encashes EL/HPL at separation — the one confirmed write from
  a PIMS service into an ALMS ledger table.

---

## Level 2.1 — Leave Application: Submission → Approval → Ledger Debit/Reversal

Traced from `LeaveApplicationService` (`submit`/`approve`/`reject`/`cancel`) and
`SupervisorResolutionService`.

**Correction vs. common assumption**: approval is **single-level**, not multi-tier. There is no
"recommend" intermediate role/state — one resolved approver (HOD functional-role assignment, or a
post-hierarchy walk to the nearest post with an active Substantive incumbent) approves directly.
There is also **no reversal after `APPROVED`** — `leave_balances.usedDays` is never decremented
back once approved; only `PENDING_APPROVAL`→`REJECTED`/`CANCELLED` releases the reservation.

```mermaid
flowchart TD
    E["Employee submits LeaveApplication\n(status=DRAFT)"] --> S["LeaveApplicationService.submit()"]
    S --> RB["resolveBalance(): check leave_balances\n.availableDays >= requested"]
    RB -- "insufficient" --> ERR["InsufficientLeaveBalanceException"]
    RB -- "sufficient" --> RES["leave_balances.reservedDays += amount"]
    RES --> SR["SupervisorResolutionService.resolveSupervisor()\n1) active HOD functional-role assignment (dept)\n2) else walk operational_reporting_post_id\n   to nearest active Substantive incumbent"]
    SR --> PA["status = PENDING_APPROVAL\napprover_employee_id / approver_post_id set"]

    PA --> APR{"Approver decision"}
    APR -- "approve()" --> AP1["leave_balances.reservedDays -= amount\nleave_balances.usedDays += amount"]
    AP1 --> APPROVED["status = APPROVED\n(terminal — no reversal path in code)"]

    APR -- "reject()" --> RJ1["releaseReservation():\nleave_balances.reservedDays -= amount"]
    RJ1 --> REJECTED["status = REJECTED"]

    APR -- "cancel() [only from DRAFT/PENDING_APPROVAL]" --> CN1["if PENDING_APPROVAL:\nreleaseReservation()"]
    CN1 --> CANCELLED["status = CANCELLED"]

    NOTE1["NOTE: this flow mutates leave_balances\n(reserved_days/used_days) directly.\nIt does NOT write leave_ledger_entries —\nthat append-only table is written only by\nAttendanceLeaveDeductionService, ElAccrualService,\nand TerminalSettlementService (separate sources)."]
```

---

## Level 2.2 — Daily Superannuation Transition → Exit Clearance → Terminal Settlement Sanction

Traced from `SuperannuationScheduledTask`, `ExitInitiationScheduler`, `ExitClearanceService`,
`EmployeeReleaseService`, `TerminalSettlementService`.

```mermaid
flowchart TD
    T90["ExitInitiationScheduler.runDailySweep()\ncron 0 30 0 * * ? (00:30 daily)"] --> LOOK["Find employee_superannuation_details\nwhere superannuation_date in [today, today+90d]"]
    LOOK --> CHK{"ACTIVE and no open\n(non-CANCELLED) exit_clearance_requests?"}
    CHK -- "no" --> SKIP1["skip"]
    CHK -- "yes" --> INIT["ExitClearanceService.initiateExit()\nstatus=INITIATED\n7x ExitClearanceItem rows created PENDING\n(ESTABLISHMENT, VIGILANCE, ESTATE, IT,\nFINANCE, STORES, CPF_TRUST)"]

    INIT --> DEPT["Each department clears via\nupdateDepartmentClearance()"]
    DEPT --> PROG["First non-PENDING item:\nstatus -> CLEARANCE_IN_PROGRESS"]
    PROG --> ALL7{"All 7 items CLEARED?"}
    ALL7 -- "no" --> DEPT
    ALL7 -- "yes" --> COMP["status -> CLEARANCES_COMPLETED"]

    COMP --> FIN["ExitClearanceService.finalizeReleaseOrder()"]
    FIN --> ISSUED["status -> RELEASE_ORDER_ISSUED\nEmployeeReleaseService.release()\n-> employees.status flips\n(RETIRED/RESIGNED/DECEASED/TERMINATED\nper SeparationType)"]

    ISSUED --> GEN["TerminalSettlementService.generate()\nstatus = DRAFT\n(Gratuity, CPF, EL/HPL encashment computed)"]
    GEN --> APPROVE["TerminalSettlementService.approve()"]
    APPROVE --> DEBIT["Debits EL/HPL balances\nwrites leave_ledger_entries\n(source=TERMINAL_ENCASHMENT)"]
    DEBIT --> APPROVED2["terminal_settlements.status = APPROVED"]

    MIDNIGHT["SuperannuationScheduledTask.runDailySweep()\ncron 0 5 0 * * ? (00:05 daily)\n— independent hard safety net"] --> OVERDUE{"ACTIVE employee whose\nsuperannuation_date < today?"}
    OVERDUE -- "yes" --> FORCE["EmployeeReleaseService.release()\n-> RETIRED\n(runs regardless of clearance workflow state)"]

    NOTE2["NOTE: 'AUDITED' and 'DISBURSED' exist in the\nterminal_settlements CHECK constraint but no Java\ncode ever sets them — only DRAFT and APPROVED\nare reachable. No disburse() endpoint exists."]
```

---

## Level 2.3 — Attendance Punch Ingestion → Monthly Evaluation → LOP Computation

Traced from `MobilePunchService`, `AttendanceAggregationService.evaluatePipelineOrNull()`
(the per-day pipeline), `AttendanceLeaveDeductionService`, and
`PayrollComputationService.computeLopDays()`.

```mermaid
flowchart TD
    PUNCH["Employee punches IN/OUT via ESS mobile app\n(lat/long, geofence check)"] --> MP["mobile_punches row written\n(review_status: VALID / FLAGGED_FOR_REVIEW)"]
    MP --> SYNC["MobilePunchService.syncDailyAttendance()\ncalls AttendanceAggregationService.evaluateDay()\n(REQUIRES_NEW tx — sync failure never blocks the punch)"]

    SYNC --> PIPE["evaluatePipelineOrNull() per employee-day\n(ordered precedence)"]
    PIPE --> ST1{"1. Approved LeaveApplication\ncovers this date?"}
    ST1 -- "yes, FULL_DAY" --> ONLEAVE["detail_status = ON_LEAVE"]
    ST1 -- "yes, half-day" --> HALFEVAL["evaluate punch window\n-> HALF_DAY_PRESENT / HALF_DAY_SHORT"]
    ST1 -- "no" --> ST2{"2. Approved TourRequest\ncovers this date?"}
    ST2 -- "yes" --> ONTOUR["detail_status = ON_TOUR"]
    ST2 -- "no, no punches" --> ST3{"3. Holiday or weekly-off\n(shift-resolved)?"}
    ST3 -- "yes" --> CAL["detail_status = HOLIDAY / WEEKOFF"]
    ST3 -- "no" --> ST4["4. Evaluate punches vs.\nshift start/end/grace (Circular JCI/HO/Pers/2024-25/53)"]

    ST4 --> COMPLY{"On-time, full duration?"}
    COMPLY -- "yes" --> PRESENT["PRESENT"]
    COMPLY -- "grace window, full day met" --> GRACE["GRACE_APPLIED"]
    COMPLY -- "grace window, short" --> LSH["LATE_SHORT_HOURS"]
    COMPLY -- "concession window\n(<=2 this month)" --> RRQ["REQUIRES_REGULARIZATION\n(needs HoD approval, not yet a violation)"]
    COMPLY -- "concession window\n(3rd+ this month)" --> UAL["UNAUTHORIZED_LATE"]
    COMPLY -- "severely out of window" --> ABS["HALF_DAY_ABSENT / ABSENT"]

    PRESENT --> DA["daily_attendance row saved\n(coarse status via toCoarseStatus() mapping)"]
    GRACE --> DA
    ONLEAVE --> DA
    ONTOUR --> DA
    CAL --> DA
    HALFEVAL --> DA
    ABS --> DA
    RRQ --> DA

    LSH --> CASCADE["AttendanceLeaveDeductionService\n.debitForUnauthorizedAttendance()\n(idempotent via related_daily_attendance_id)"]
    UAL --> CASCADE
    CASCADE --> CL1{"CL leave_balances\nhas 0.5 available?"}
    CL1 -- "yes" --> CLDEBIT["Debit 0.5 CL\n+ leave_ledger_entries (source=AUTO_LATE_DEDUCTION)"]
    CL1 -- "no" --> EL1{"EL has 0.5 available?"}
    EL1 -- "yes" --> ELDEBIT["Debit 0.5 EL\n+ leave_ledger_entries"]
    EL1 -- "no" --> LWP["Mark LWP/Dies-Non\n(ledger-only — no LeaveBalance row for LWP)"]

    DA --> LOP["PayrollComputationService.computeLopDays()\ncounts daily_attendance.status\nABSENT=1.0 day, HALF_DAY=0.5 day\nwithin the payroll cycle window"]

    NOTE3["NOTE: there is deliberately NO reversal path for\nthe CL/EL/LWP auto-debit — if a punch is corrected\nlater, the earlier debit stands and must be fixed\nmanually (documented gap in AttendanceLeaveDeductionService).\nA day with NO attendance row at all is NOT counted as LOP."]
```
