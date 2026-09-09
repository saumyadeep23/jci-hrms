# Employee Movement Lifecycle — Promotion, Transfer, Release, Transit & Joining

Grounded in the actual backend (`backend/src/main/java/in/gov/jci/hrms`) and migrations
(`backend/src/main/resources/db/migration`) as of migration V70. The primary classes are
`MovementOrderService`, `JoiningReportService`, `PostIncumbencyService`, `PayrollMovementIntegrationService`,
and (as the downstream payroll consumer) `PayrollBatchComputationService`.

> **Naming corrections vs. common assumptions**
> A number of names commonly assumed for this workflow do not exist in the real schema/code. Real
> names are used throughout this document; here is the map from assumed → real:
>
> | Assumed | Real |
> |---|---|
> | `employee_movement_records.status`: `DRAFT → ISSUED → RELIEVED → JOINED` | `movement_status`: **`ORDERED → RELIEVED → JOINED`** (`MovementStatus`, 3 values — no DRAFT/ISSUED) |
> | `joining_status`: `SUBMITTED → PENDING_VERIFICATION → ACCEPTED` | `JoiningStatus`: **`NOT_SUBMITTED → PENDING_VERIFICATION → ACCEPTED/REJECTED`**, plus a side-branch `CLARIFICATION_REQUESTED` |
> | `post_incumbency.assignment_type = 'REGULAR'` | `AssignmentType`: **`SUBSTANTIVE, ADDITIONAL_CHARGE, ACTING, LOOK_AFTER`** — no `REGULAR` value exists (DB `CHECK` constraint rejects it) |
> | `post_incumbency.relieved_session` column | **No such column.** Release-session lives on `employee_movement_records.release_session`, not on `post_incumbency` |
> | `uq_idx_single_regular_active_occupant` | Real name: **`uq_post_incumbency_post_substantive_active`** — a partial unique index on `post_incumbency(post_id) WHERE assignment_type='SUBSTANTIVE' AND is_active=true` (per-**post**, not per-occupant) |
> | `uq_idx_single_regular_active_employee` | **No such constraint exists.** Per-employee "at most one active incumbency" is *not* DB-enforced — see `PostIncumbencyRepository.findActiveByEmployeeId()`'s own javadoc caveat |
> | `employees.loc_code` synced on joining | **No `loc_code` column on `employees`.** `loc_code` lives on `payroll_monthly_records` only, populated per batch from the employee's resolved office code. What *does* get synced onto `employees` on joining approval is `department_id`, `designation_id`, `ro_id` (regional office), `dpc_id` |
> | `PayrollBatchComputationService` head numbers matching a generic payroll brief | The **real** `payroll_salary_heads` catalog (V66/V69 seed) is used: DA is head **7 (CDA)** or **8 (IDA)**, not head 2; Professional Tax is head **49**, not head 42 |
> | `regular_pay_fixations` closed via entity mutation | Closed via a **bulk `@Modifying` JPQL `UPDATE`** (`closeCurrentFixation`) — see the Appendix for why entity mutation was actively bug-prone here |

---

## Part 1 — Operational / User Walkthrough

```
┌──────────────────┐   ┌────────────────────┐   ┌───────────────────┐   ┌────────────────────┐   ┌──────────────────────┐
│  STEP 1           │   │  STEP 2             │   │  STEP 3            │   │  STEP 4             │   │  STEP 5               │
│  Order Issuance    │   │  Relieving           │   │  Transit & JT       │   │  Joining Report      │   │  Verification &        │
│  (HR Admin /       │──▶│  Execution           │──▶│  (Employee in       │──▶│  Submission           │──▶│  Approval              │
│  Competent         │   │  (Releasing Station) │   │  transit)           │   │  (Employee, ESS)      │   │  (Receiving Station)   │
│  Authority)         │   │                      │   │                     │   │                       │   │                        │
└──────────────────┘   └────────────────────┘   └───────────────────┘   └────────────────────┘   └──────────────────────┘
  MovementOrderService     JoiningReportService     JoiningTimeCalculator     JoiningReportService      JoiningReportService
  .create()                .release()               Service (pure math,      .submitJoiningReport()    .decide()
                                                      no state change)
```

### Step 1 — Movement Order Issuance (HR Admin / Competent Authority)

**Endpoint:** `POST /api/v1/pims/movements/orders` → `MovementOrderService.create(MovementOrderCreateRequest)`

The request captures `orderType` (`TRANSFER | PROMOTION | TRANSFER_CUM_PROMOTION`), `orderRefNo`,
`orderDate`/`effectiveDate`, `transferNature` (`ADMINISTRATIVE | OWN_REQUEST | MUTUAL`), the
from/to office-or-DPC pair, from/to department/designation, and — for a promotion —
`promotionalBasicPay`/`toScaleCode`. One `MovementOrder` (the office-order record) plus its first
`EmployeeMovementRecord` (one employee's leg of that order) are created in a single transaction.

Two things happen automatically at creation, both **before** any release/joining step:

1. **Station distance & admissible Joining Time.** `computeStationDistanceKm()` uses
   `GeofenceService.distanceMeters()` (Haversine great-circle) between the from/to office
   coordinates (0 if either office has no lat/long configured), and
   `JoiningTimeCalculatorService.computeAdmissibleJtDays(transferNature, distanceKm)` slabs that
   into admissible JT days: `OWN_REQUEST` → 0; 0 km → 0; &lt;20 km → 1; ≤1000 km → 10; ≤2000 km →
   12; beyond → 15.
2. **DPE promotion pay fixation**, only for `PROMOTION`/`TRANSFER_CUM_PROMOTION` orders that
   carry a `toScaleCode`, and only for an employee whose current `EmployeeEmploymentCategory` is
   `REGULAR` (anyone else is silently skipped — nothing to fix). See **Stage A** below for the
   exact mechanics.

### Step 2 — Relieving Execution (Releasing Station / Controlling Officer)

**Endpoint:** `PATCH /api/v1/pims/movements/records/{id}/release` → `JoiningReportService.release(id, MovementReleaseRequest)`

Requires `movementStatus == ORDERED`. Captures `releaseOrderRef`, `releaseDate`, and
`releaseSession` (`FORENOON | AFTERNOON` — **user-entered** here, unlike the joining session,
because this is a clerical record of a physical relieving order, not a server-witnessed event).
Sets `movementStatus = RELIEVED`, records a `TRANSFER_RELEASE` Service Book event, and closes the
employee's outgoing post incumbency. See **Stage B**.

### Step 3 — Transit & Joining Time (Employee Lifecycle)

Pure elapsed time — no service call, no state transition happens here on its own. The relevant
math (`JoiningTimeCalculatorService`) is evaluated later, once the employee actually submits a
joining report:

```
availedDays        = max(0, joiningDate − releaseDate)
unavailedJtDays     = max(0, admissibleJtDays − availedDays)     → convertible to EL credit
excessTransitLwpDays = max(0, availedDays − admissibleJtDays)    → unpaid transit overage
```

During this window the employee holds **no active post incumbency at all** (Stage B closed the
old one; Stage D has not yet opened the new one) — see the "no active incumbency" invariant in
Part 2.

### Step 4 — Joining Report Submission (Transferred Employee, ESS)

**Endpoint:** `POST /api/v1/pims/movements/records/{id}/joining-report` → `JoiningReportService.submitJoiningReport(id, JoiningReportRequest, callerEmployeeId, submissionIp)`

Requires `movementStatus == RELIEVED` and `joiningStatus == NOT_SUBMITTED`. The request carries
only `joiningReportNo`, optional `latitude`/`longitude`/`accuracyMeters` ("soft GPS" — optional,
never a hard gate: a desktop ESS session with no GPS chip must still be able to submit), and
`remarks`. **Deliberately absent from the request**: joining date, session, and timestamp — those
are captured server-side from `DbClockService.now()`, evaluated in `Asia/Kolkata`, with the FN/AN
cutoff at **13:00 IST** — a client cannot backdate a submission or force a particular session.
`is_geo_verified` is set (not gated) by comparing the submitted coordinates against the
destination office's own geofence radius. Sets `movementStatus = JOINED` and
`joiningStatus = PENDING_VERIFICATION`.

If a reviewing officer sends the report back (`requestClarification`), `joiningStatus` moves to
`CLARIFICATION_REQUESTED`; `resubmitJoiningReport()` (`PUT .../joining-report/resubmit`) re-runs
the exact same DB-clock/soft-GPS capture and returns it to `PENDING_VERIFICATION`.

### Step 5 — Verification & Approval (Receiving Station / Controlling Officer)

**Endpoint:** `POST /api/v1/pims/movements/records/{id}/joining-report/decision` → `JoiningReportService.decide(id, JoiningDecisionRequest, approverEmployeeId)`

Requires `joiningStatus == PENDING_VERIFICATION`. On `approve = true`: `joiningStatus = ACCEPTED`,
then `approveJoiningReport()` runs the full approval cascade (Service Book events, EL credit,
payroll movement inputs, LPC issuance) and `syncPostIncumbencyAndMasterData()` opens the new
incumbency and syncs the employee master record — see **Stage D**. On `approve = false`:
`joiningStatus = REJECTED`, and none of that cascade runs.

---

## Part 2 — System Architecture & Data Flow

### 2.1 State Machines

`movementStatus` and `joiningStatus` are **deliberately two separate state machines** on the same
row — a report can sit at `PENDING_VERIFICATION` while the employee has already physically
`JOINED`. Nothing here merges them.

```
movementStatus (EmployeeMovementRecord.movementStatus):

   ┌─────────┐  release()   ┌──────────┐  submitJoiningReport()  ┌────────┐
   │ ORDERED │ ───────────▶ │ RELIEVED │ ───────────────────────▶ │ JOINED │
   └─────────┘              └──────────┘                          └────────┘
   (set at Step 1)           (Step 2)                              (Step 4 —
                                                                     NOT Step 5;
                                                                     "JOINED" means
                                                                     the report was
                                                                     submitted, not
                                                                     yet approved)


joiningStatus (EmployeeMovementRecord.joiningStatus):

                    submitJoiningReport()
   ┌───────────────┐ ───────────────────▶ ┌──────────────────────┐
   │ NOT_SUBMITTED │                       │ PENDING_VERIFICATION │
   └───────────────┘                       └──────────────────────┘
                                              │        ▲       │
                          requestClarification│        │       │ decide(approve=true)
                                              ▼        │       ▼
                                    ┌───────────────────────┐  ┌──────────┐
                                    │ CLARIFICATION_REQUESTED│  │ ACCEPTED │
                                    └───────────────────────┘  └──────────┘
                                              │
                          resubmitJoiningReport()      decide(approve=false)
                                              │                  │
                                              ▼                  ▼
                                    (back to PENDING_VERIFICATION)   ┌──────────┐
                                                                     │ REJECTED │
                                                                     └──────────┘
```

### 2.2 End-to-End Sequence Flow

```
HR Admin        MovementOrder    RegularPayFixation   Releasing      JoiningReport    PostIncumbency   Employee     Receiving      EmployeeServiceBook   PayrollMovement    PostIncumbency
                Service          Repository           Officer        Service (rel.)   Repository       (ESS)        Officer        Service               IntegrationService Service
   │                │                    │                │               │                │              │             │                 │                     │                  │
   │ POST orders    │                    │                │               │                │              │             │                 │                     │                  │
   │───────────────▶│                    │                │               │                │              │             │                 │                     │                  │
   │                │ if PROMOTION:      │                │               │                │              │             │                 │                     │                  │
   │                │ closeCurrentFixation()               │               │                │              │             │                 │                     │                  │
   │                │───────────────────▶│                │               │                │              │             │                 │                     │                  │
   │                │ save new fixation  │                │               │                │              │             │                 │                     │                  │
   │                │───────────────────▶│                │               │                │              │             │                 │                     │                  │
   │  201 record (ORDERED)               │                │               │                │              │             │                 │                     │                  │
   │◀───────────────│                    │                │               │                │              │             │                 │                     │                  │
   │                │                    │                │               │                │              │             │                 │                     │                  │
   │                │                    │  PATCH release │               │                │              │             │                 │                     │                  │
   │                │                    │                │──────────────▶│                │              │             │                 │                     │                  │
   │                │                    │                │                │ movementStatus=RELIEVED       │             │                 │                     │                  │
   │                │                    │                │                │───────────────────────────────────────────▶│ TRANSFER_RELEASE│                     │                  │
   │                │                    │                │                │ closeActiveIncumbency(employeeId, releaseDate)                 │                     │                  │
   │                │                    │                │                │───────────────▶│              │             │                 │                     │                  │
   │                │                    │                │                │  employees row  UNTOUCHED (critical invariant)                 │                     │                  │
   │                │                    │                │                │                │              │             │                 │                     │                  │
   │                │                    │                │                │        (Joining Time elapses — no active incumbency)            │                     │                  │
   │                │                    │                │                │                │              │             │                 │                     │                  │
   │                │                    │                │                │  POST joining-report            │             │                 │                     │                  │
   │                │                    │                │                │◀─────────────────────────────│             │                 │                     │                  │
   │                │                    │                │                │ DbClockService.now() → session, joiningDate │                 │                     │                  │
   │                │                    │                │                │ GeofenceService soft-verify   │             │                 │                     │                  │
   │                │                    │                │                │ movementStatus=JOINED, joiningStatus=PENDING_VERIFICATION      │                     │                  │
   │                │                    │                │                │                │              │             │                 │                     │                  │
   │                │                    │                │                │  POST joining-report/decision (approve)     │                 │                     │                  │
   │                │                    │                │                │◀─────────────────────────────────────────────────────────────│                 │                     │                  │
   │                │                    │                │                │ joiningStatus=ACCEPTED         │             │                 │                     │                  │
   │                │                    │                │                │ recordJoiningServiceBookEvent()───────────────────────────────────────────────────▶│ TRANSFER_JOINING│                  │
   │                │                    │                │                │ applyPromotion() (if not TRANSFER)─────────────────────────────────────────────────▶│ PROMOTION,       │                  │
   │                │                    │                │                │                │              │             │                 │  PAY_FIXATION       │                  │
   │                │                    │                │                │ creditUnavailedJoiningTime() (EL credit, 300-day ceiling)                            │                  │
   │                │                    │                │                │────────────────────────────────────────────────────────────────────────────────────▶│TRANSFER_BENEFIT_ │                  │
   │                │                    │                │                │                │              │             │                 │  EL_CREDIT          │                  │
   │                │                    │                │                │ payrollMovementIntegrationService.generateInputs()─────────────────────────────────────────────────────▶│                  │
   │                │                    │                │                │ payrollSyncStatus=LPC_ISSUED  │              │             │                 │                     │                  │
   │                │                    │                │                │ syncPostIncumbencyAndMasterData()──────────────────────────────────────────────────────────────────────────────────▶│
   │                │                    │                │                │                │              │             │                 │                     │  closeActiveIncumbency()          │
   │                │                    │                │                │                │              │             │                 │                     │  PostIncumbencyService.create()   │
   │                │                    │                │                │                │              │             │                 │                     │  employee.dept/desg/ro/dpc synced │
```

### 2.3 Database Mutations by Stage

#### Stage A — Order Issuance & Promotion Pay Fixation

| Table | Operation | Key columns / values | Invariant |
|---|---|---|---|
| `movement_orders` | INSERT | `order_type`, `order_ref_no`, `order_date`, `effective_date` | `order_ref_no` unique |
| `employee_movement_records` | INSERT | `movement_status = 'ORDERED'`, `joining_status = 'NOT_SUBMITTED'`, `admissible_jt_days` (JT slab), `station_distance_km`, `promotional_basic_pay` | — |
| `regular_pay_fixations` (promotion only, REGULAR employees only) | **bulk `UPDATE`** via `closeCurrentFixation(employeeId, effectiveDate.minusDays(1))`, then **`INSERT`** new row | Closes: `is_current = false`, `effective_to = effectiveDate − 1`. New row: `basic_pay = max(current + round10(current×3%), targetScale.min)` clamped to `targetScale.max`, `fixation_reason = 'PROMOTION'` | `idx_uq_current_regular_fixation` — unique on `(employee_id) WHERE is_current = true` |
| `employee_employment_categories` | UPDATE | `regular_basic_pay`, `grade_scale_id` kept in sync with the new fixation | Additive alongside `regular_pay_fixations` so payroll's pre-existing reader is unaffected |

> **Why a bulk `UPDATE`, not entity mutation:** the original code loaded the current fixation,
> mutated it (`setCurrent(false)`), and relied on the enclosing transaction's flush to persist it
> alongside `save()` of the brand-new row. Against a live DB this hit two real Hibernate bugs:
> single-flush statement ordering runs INSERTs before UPDATEs regardless of code order (so the new
> `is_current=true` row's INSERT could violate `idx_uq_current_regular_fixation` before the old
> row's UPDATE ever ran), and forcing an earlier flush by re-saving the already-managed entity
> nulled its lazy `gradeScale`/`scale_code` association on merge. `closeCurrentFixation()` is a
> `@Modifying(clearAutomatically = true, flushAutomatically = true)` JPQL `UPDATE` — it never loads
> the row as an entity, so neither failure mode applies. See the Appendix.

#### Stage B — Relieving

| Table | Operation | Key columns / values | Invariant |
|---|---|---|---|
| `employee_movement_records` | UPDATE | `release_order_ref`, `release_date`, `release_session`, `released_at_dbtimestamp`, `movement_status = 'RELIEVED'` | Only from `movement_status = 'ORDERED'` |
| `employee_service_book` | INSERT | `event_type = 'TRANSFER_RELEASE'`, snapshot of the **from**-side department/designation/office, `remarks` | — |
| `post_incumbency` | **bulk `UPDATE`** via `closeActiveIncumbency(employeeId, releaseDate)` | `is_active = false`, `end_date = releaseDate` for **all** of this employee's active incumbencies | — |
| `employees` | **untouched** | — | **CRITICAL INVARIANT** — the employee is in transit and does not yet hold the new post; `department_id`/`designation_id`/`ro_id`/`dpc_id` are only synced on joining approval (Stage D), never here |

#### Stage C — Transit

No table mutation happens purely from the passage of time. State: the employee has **zero active
`post_incumbency` rows** (Stage B closed the old one; Stage D has not yet run). `JoiningTimeCalculatorService`
is pure math with no repository dependency — `admissibleJtDays` was already persisted at Stage A,
and `unavailedJtDays`/`excessTransitLwpDays` are computed (not yet persisted) the moment a joining
report is actually submitted (Step 4), by diffing `joiningDate` against the stored `releaseDate`.

#### Stage D — Joining Approval

| Table | Operation | Key columns / values | Invariant |
|---|---|---|---|
| `employee_movement_records` | UPDATE | `joining_status = 'ACCEPTED'`, `approved_by_officer_id`, `approved_at`, `probation_end_date` (promotions), `el_credited_days`/`is_el_credited`/`leave_ledger_txn_id` (if EL credit fired), `lpc_number`, `payroll_sync_status = 'LPC_ISSUED'`, `effective_pay_fixation_date` | Only from `joining_status = 'PENDING_VERIFICATION'` |
| `employee_service_book` | INSERT (1–3 rows) | `TRANSFER_JOINING` always; `PROMOTION` (+ `PAY_FIXATION` if `promotionalBasicPay` set) when `order_type != TRANSFER`; `TRANSFER_BENEFIT_EL_CREDIT` if unavailed JT converted | — |
| `leave_entitlement_balance` / `leave_ledger_entries` | UPDATE / INSERT | EL `current_balance`/`available_balance` credited (50:50 encashable/enjoyable split), capped so balance never exceeds the **300-day EL ceiling**; only for `ADMINISTRATIVE` transfers with `is_transfer_benefit_admissible = true` | `creditAllowed = min(unavailedJtDays, max(0, 300 − currentBalance))` |
| `payroll_movement_inputs` | INSERT (1 or 2 rows) | `releasing_office_id`/`releasing_office_days`, `transit_jt_days`/`transit_lwp_days`, `receiving_office_id`/`receiving_office_days`, `revised_hra_tier`, `revised_basic_pay` | 2 rows only when release month ≠ join month (JT ≤ 15 days, so this is rare) |
| `post_incumbency` | **bulk `UPDATE`** (defensive re-close) then **`INSERT`** new row | New row: `assignment_type = 'SUBSTANTIVE'`, `is_active = true`, `start_date = joiningDate` | `uq_post_incumbency_post_substantive_active` — **and**, since a recent fix, `PostIncumbencyService.create()` now refuses to auto-close *another employee's* prior incumbent on the target post unless a `RELIEVED`/`JOINED` `employee_movement_records` row proves they actually left it — see Appendix |
| `employees` | UPDATE | `department_id`, `designation_id`, `ro_id`, `dpc_id` — synced from the resolved `PostMaster` | Only fires when the (department, designation, regionalOffice) triple resolves to **exactly one** sanctioned `post_master` row — 0 or >1 matches skip the sync entirely rather than guessing |

**Why the post-resolution can be ambiguous:** `employee_movement_records` carries no `post_id` at
all — Movement Orders and the Post Incumbency ledger are otherwise independent subsystems. The
destination post is matched via `PostMasterRepository.findByDepartment_IdAndDesignation_IdAndRegionalOffice_Id()`,
and the sync is skipped (logged, not failed) whenever that doesn't resolve to a single post — this
must never block the Service-Book/EL-credit/payroll-input cascade that already completed above it.

### 2.4 Reporting Hierarchy Architecture

`SupervisorResolutionService.resolveSupervisor(employeeId)` is what actually resolves an
approver for leave/attendance-regularization requests (`LeaveApplicationService`,
`AttendanceRegularizationService`) — a different resolver, `DoaResolverService`, handles a
separate DOA-circular routing concern and is not part of this walk.

```
resolveSupervisor(employeeId)
   │
   ▼
1. Functional-role shortcut: does the applicant's DEPARTMENT have an active
   HOD EmployeeFunctionalRoleAssignment (role_code = 'HOD') held by someone
   other than the applicant? → route straight there, post = null.
   │
   ▼ (no HOD assignment)
2. currentPost(employeeId): the applicant's own active post_incumbency
   (SUBSTANTIVE preferred, else any active row) → PostMaster seat.
   │
   ▼
3. Walk post_master.operational_reporting_post_id upward (max 20 hops,
   cycle-safe via a visited-set):

       [Applicant's Post] ──operational_reporting_post_id──▶ [Post A]
                                                                  │ active SUBSTANTIVE
                                                                  │ incumbent here?
                                                       No ──▶ [Post B] ──▶ ...
                                                       Yes ─▶ STOP → that incumbent
                                                              is the resolved supervisor

   A vacant post along the way is skipped (kept walking), not treated as
   a dead end.
   │
   ▼ (top of hierarchy or cycle reached, still nothing)
4. staticFallback(employeeId) → Optional.empty() — deliberate stub; Employee
   has no manager/reporting-line field to fall back to, so this is documented
   as unimplemented rather than guessed at.
```

`administrative_reporting_post_id` and `accepting_authority_post_id` are additional self-referencing
FKs on `post_master`, present in the schema for administrative-channel and joining-acceptance
routing respectively, but `SupervisorResolutionService` itself only walks `operational_reporting_post_id`.

### 2.5 Downstream Payroll & Statutory Linkage

`PayrollBatchComputationService` (the current, batch-based engine — distinct from the older,
still-active cycle-based `PayrollComputationService`) is the sole consumer of
`payroll_movement_inputs`. For a month with a movement row, that row's own day-split is
**authoritative for the whole month** — the ordinary `daily_attendance`-based LOP check is skipped
entirely for that employee that month, since a real attendance register cannot span two offices'
registers within one transfer month.

```
buildOfficeWindows(employee, month, year)
   │
   ├─ no payroll_movement_inputs row this month?
   │    └─▶ ONE whole-month OfficeWindow @ employee.regionalOffice (payable, non-transit)
   │
   └─ has a payroll_movement_inputs row:
        Day  1 ─────────────────────────────────────────────────────────── Day 30/31
        │←── releasing_office_days ──→│←─ transit_jt_days ─→│←transit_lwp→│←── receiving_office_days ──→│
        │   (releasing office, paid)  │ (releasing office's │  (no office,│   (receiving office, paid)   │
        │                             │  terms, paid, transit│   unpaid)  │                               │
        │                             │  = true)             │            │                               │
        └── any day-count shortfall vs. days-in-month is covered by one more
            window at employee.regionalOffice, rather than silently dropping pay

buildEarningsSlices(): cross-joins each payable OfficeWindow against the employee's FULL
RegularPayFixation history — a slice is the day-range where BOTH the office AND the pay-fixation
rate are constant. This is what makes a same-month promotion + transfer compute correctly instead
of applying whichever fixation happens to be "current" at computation time to the whole month.
```

**Per-head computation, by real head number** (`payroll_salary_heads` V66/V69 catalog):

| Head | Name | Basis | Rounding granularity |
|---|---|---|---|
| 1 | Basic | `Σ slice.fixation.basicPay × slice.days / daysInMonth` | Per slice |
| 7 (CDA) / 8 (IDA) | DA | `Σ(basicByScaleType) × daRate(scaleType, monthEnd) / 100` | **Once per scale type**, on the scale's *total* Earned Basic for the month — not per slice (see note below) |
| 9 | HRA | `sliceBasic × hraRate(cityClass X/Y/Z, asOfDate) / 100`, skipped entirely if `EmployeeQuarterAllotmentService.isHraSuppressed()` | Per slice |
| 10 | Transport Allowance | `baseRate(gradeScale, cityClass) + round(baseRate × daPercentage / 100)`, flat (not Basic-proportional); **skipped for transit (Joining Time) slices** — no fixed commute while in transit | Per slice |
| 20 | Leave Encashment | `basicPlusDa / 30 × (elDaysClaimed + hplDaysClaimed)` unless the application carries its own `grossAmount` | Whole month |
| 21 | Remote Area Allowance | `sliceBasic × stateMaster.remoteAllowancePercentage / 100`, only if `StateMaster.isRemoteArea` for the slice's office's state | Per slice |
| 27 | CPF (employee) | `basicPlusDa × CPF_EMP_RATE / 100` (statutory parameter, default 12%) | Whole month |
| 40 | TDS (Section 192) | `PayrollTdsEngine.computeMonthlyTds(...)` — regime-aware, Sec 80CCD(2)/87A, progressive slabs + 4% cess | Whole month |
| 44 | Car-use recovery | Flat monthly deduction from `EmployeeVehicleAllotment`, only if office car provided | Whole month |
| 49 | Professional Tax | Slab lookup by `stateCode` resolved from the employee's **effective month-end station** (see below), against `grossAmount`; a `specialMonth` override can apply (e.g. WB's February slab) | Whole month, but station-dependent |
| 62 | NPS | `basicPlusDa × npsDeclaration.npsPercentage / 100`, only if an `ACTIVE` declaration exists for the financial year | Whole month |
| 63/64/65 | Accommodation (license fee / water / electric) | From the active `EmployeeQuarterAllotment` occupancy for the month's span | Whole month |

**Why P-Tax/Remote-Area resolve off the *effective month-end station*, not `employee.getRegionalOffice()`:**
the *last* `EarningsSlice` chronologically (the receiving office after a mid-month transfer) is
used — P-Tax is a where-you-are-posted-**now** tax, not a where-you-earned-each-rupee one. This
matters concretely for Assam vs. West Bengal: a mid-month Kolkata (WB) → Guwahati (Assam) transfer
resolves that month's Professional Tax against **Assam's** slab table, not West Bengal's, even
though most of the month's Basic was actually earned at the WB office.

**Why DA gets exactly one rounding per scale type, not one per slice:** a promotion/transfer
splits Earned Basic itself into several independently-rounded rupee-and-paise pieces (each
day-range genuinely earns its own amount), but DA is a single percentage applied to whatever the
month's Basic added up to — so it is rounded once on the summed total, never accumulated as a sum
of per-slice roundings (which would silently drift from the "percentage of the month's actual
Basic" definition by a paisa or two).

---

## Appendix — Invariants, Constraints & Recent Fixes

- **CRITICAL INVARIANT:** `JoiningReportService.release()` never writes to `employees`. The
  employee is mid-transit and does not yet hold the new post; the employee master record is only
  touched by `syncPostIncumbencyAndMasterData()` on joining **approval**.
- **Two Hibernate flush-ordering bugs, both fixed via bulk `@Modifying` `UPDATE`s** rather than
  entity mutation + later flush: `RegularPayFixationRepository.closeCurrentFixation()` (promotion
  pay fixation, Stage A) and `PostIncumbencyRepository.closeById()`/`closeActiveIncumbency()`
  (post incumbency, Stages B and D). In both cases the original code loaded the row, mutated it,
  and relied on the enclosing `@Transactional` method's end-of-method flush — Hibernate orders
  INSERTs before UPDATEs within one flush regardless of code order, so a new "current"/"active"
  row's INSERT could violate the relevant partial unique index before the old row's UPDATE ever
  ran.
- **Outgoing-incumbent verification guard:** `PostIncumbencyService.create()` no longer
  auto-closes a post's prior `SUBSTANTIVE` incumbent purely because a new one is being created for
  someone else. It now requires `EmployeeMovementRecordRepository.existsReleasedMovementFromPost()`
  to confirm that outgoing employee has an actual `RELIEVED`/`JOINED` movement record moving them
  away from *that exact post* (department/designation/office match); otherwise it throws
  `BusinessRuleViolationException` and leaves the post occupied for manual HR reconciliation,
  rather than silently evicting a still-serving employee's incumbency.
- **`employee.getRegionalOffice()` sync gap, now closed:** `PayrollBatchComputationService`'s own
  class javadoc still documents (as of this writing) that "employee.getRegionalOffice() is never
  updated by the movement lifecycle" as a known limitation worked around via
  `payroll_movement_inputs`. That gap has since been closed by
  `JoiningReportService.syncPostIncumbencyAndMasterData()` (Stage D) — a following month with no
  `payroll_movement_inputs` row now correctly falls back to the employee's *updated* office rather
  than a stale one, for any movement that resolved to exactly one sanctioned post. The comment in
  `PayrollBatchComputationService` should be updated to reflect this.
