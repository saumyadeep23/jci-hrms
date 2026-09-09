# Software Requirements Specification — PIMS & ALMS Modules

**Document type:** IEEE 830-style SRS
**System:** JCI HRMS (Human Resource Management System for JCI, an Indian government-adjacent PSU)
**Modules covered:** PIMS (Personnel Information Management System) and ALMS (Attendance & Leave Management System)
**Grounding:** Every requirement below is traced to real tables (`backend/src/main/resources/db/migration/`), JPA entities/services/controllers (`backend/src/main/java/in/gov/jci/hrms/`), and frontend views (`frontend/src/`) as they exist in the codebase at the time of writing. Where the codebase's own comments flag something as a placeholder, simplification, or gap, that is called out rather than smoothed over.

---

## 1. Introduction

### 1.1 Purpose
This SRS documents the functional and non-functional requirements actually implemented (or partially implemented) for PIMS and ALMS, to serve as a baseline before further feature work. It is a reverse-engineered specification, not a pre-implementation design document — it exists so PIMS/ALMS extensions are built on an accurate understanding of current behavior rather than assumptions.

### 1.2 Scope
JCI HRMS is an early-stage system (Spring Boot 3.3.2/Java 21 backend, React/TypeScript frontend, PostgreSQL 16) deployed to AWS `ap-south-1` (Mumbai) for data-residency compliance. Per the project's own `CLAUDE.md`: the system may eventually handle Aadhaar-linked data, and STQC audit / CERT-In empanelled VAPT are expected before production launch, with MeitY/GI Cloud empanelment and IT Security Committee sign-off required before any environment beyond `dev`. PIMS covers the employee lifecycle from onboarding through separation and terminal settlement; ALMS covers attendance capture, leave rules, and leave-linked payroll deductions (LOP).

### 1.3 Definitions
- **PIMS**: Personnel Information Management System (employee master, cadre/post, pay fixation, superannuation, exit, settlement).
- **ALMS**: Attendance & Leave Management System (shifts, punches, daily attendance, leave application/ledger/encashment).
- **EL / HPL / CL / RH**: Earned Leave / Half Pay Leave / Casual Leave / Restricted Holiday — leave type codes seeded in `leave_types` (V15).
- **LOP**: Loss of Pay, computed from `daily_attendance` status for payroll purposes.
- **DoPT Rule 39**: cited directly in `TerminalSettlementService`'s EL/HPL encashment-at-retirement formula.

---

## 2. System Overview & User Personas

Roles are Spring Security authorities enforced via `@PreAuthorize(hasAnyRole(...))` on controllers. The **actual** role set found across every controller in the codebase is:

| Role (as coded) | Where used | Notes |
|---|---|---|
| `EMPLOYEE` | Self-service: leave application, attendance regularization request, ESS leave/encashment pages | The base authenticated-user persona |
| `HR_ADMIN` | Nearly all PIMS/ALMS admin endpoints (onboarding, post incumbency, exit clearance, leave/attendance masters, approvals) | The primary administrative persona |
| `SUPER_ADMIN` | Paired with `HR_ADMIN` on almost every admin endpoint | Superset administrative persona, not functionally distinguished from `HR_ADMIN` in most controllers |
| `FINANCE_ADMIN` | `LeaveEncashmentController` (finance-side dual approval), some settlement-adjacent flows | Maps to the requested "Finance/CPF Admin" persona's finance half |
| `CPF_ADMIN` | CPF ledger/loan-adjacent controllers | Maps to the "Finance/CPF Admin" persona's CPF half — **`FINANCE_ADMIN` and `CPF_ADMIN` are two distinct roles in code, not one combined "Finance/CPF Admin"** |
| `COOP_ADMIN` | Cooperative/society loan-adjacent controllers | Not requested by name but exists in the real role set |

**Correction to the requested persona list:** a distinct **"Nodal Officer"** role/authority does **not exist** anywhere in the codebase (`grep -r "Nodal"` returns nothing). The closest real analogues are: (a) `exit_clearance_items.department_code` (one of `ESTABLISHMENT, VIGILANCE, ESTATE, IT, FINANCE, STORES, CPF_TRUST`) with a generic `cleared_by_user_id` — clearance is per-department, not per a named "Nodal Officer" role; and (b) `leave_applications.approver_employee_id`/`approver_post_id` — a generic per-application approver reference, not a distinct role either. Both are modeled as **references to whichever `HR_ADMIN`/`EMPLOYEE` user acts**, not as a first-class persona. Feature work assuming a dedicated Nodal Officer authority would need new role plumbing.

---

## 3. Functional Requirements

### 3.1 PIMS

**FR-PIMS-001 — Employee Master 360 Profile**
The system provides a consolidated read view of an employee's core, family, address, banking, qualification, employment-category, and compensation data via the `vw_jci_employee_master_360` SQL view (V32, revised V57/V60), exposed through `PimsReportController`. As of V60, the view's grade/scale join is `grade_scale_master.scale_code = employee_employment_categories.scale_code` (the `pay_scale_id` FK leg was dropped).

**FR-PIMS-002 — Employee Onboarding Wizard**
An 8-step onboarding flow (`frontend/src/pages/hr/onboarding/OnboardingWizardPage.tsx`, Steps 1–8: Personal, Address, Banking, Qualifications, Past Service, Employment, Family, Review) persists progressive drafts (`onboarding_drafts`, V25) before finalizing into `employees` and related tables via `EmployeeOnboardingService`.

**FR-PIMS-003 — Post Master & Cadre/Designation Structure**
`post_master` (V6) models a post as a (department, designation, RO/DPC) tuple with self-referential operational/administrative reporting-post links. `PostMasterController` exposes CRUD; `designations.category_type = 'Director'` distinguishes Board-level posts (consumed by the superannuation trigger, FR-PIMS-008).

**FR-PIMS-004 — Post Incumbency Assignment**
`post_incumbency` (V6) tracks who holds a post and how (`SUBSTANTIVE`, `ADDITIONAL_CHARGE`, `ACTING`, `LOOK_AFTER`), with a partial unique index (`uq_post_incumbency_post_substantive_active`) enforcing at most one active Substantive incumbent per post — backstopped in the DB, primarily enforced in `PostIncumbencyService`. `PostIncumbencyController` exposes create/list/end (`POST /api/post-incumbencies`, `.../{id}/end`).

**FR-PIMS-005 — Grade Scale Master & Pay Fixation (post-V60 architecture)**
`grade_scale_master` (V48: `scale_code`, `cadre` [BOARD/EXECUTIVE/STAFF], `hierarchy_level`, `board_level`, min/max basic, `scale_type` IDA/CDA) is the current grade/scale authority, linked from `employee_employment_categories.scale_code`. `regular_pay_fixations` (V50) is the historized, promotion/increment-aware ledger of a REGULAR employee's basic pay against a grade scale, with an `is_current` flag resolved via `RegularPayFixationRepository.findByEmployeeIdAndCurrentTrue()`. **V60 explicitly decoupled and dropped** the legacy `pay_scale_master`-linked columns (`employees.pay_scale_id`, `employee_employment_categories.pay_scale_id`) after discovering the latter's FK had silently drifted to point at `grade_scale_master` instead of `pay_scale_master` — `pay_scale_master`/`PayScaleController`/`PayScaleService` remain in the schema/codebase but are `@Deprecated` and read-only.

**FR-PIMS-006 — Increment Processing**
`IncrementProcessingService` computes a 3% IDA increment (`GET /api/v1/increments/due-list`, `POST .../process-batch`) against an employee's current basic pay (COALESCE of `regular_pay_fixations.basic_pay` then `employee_employment_categories.regular_basic_pay`), capped at the grade's maximum basic, and withholds/flags employees with an active (non-`CLOSED`/`EXONERATED`) disciplinary case.

**FR-PIMS-007 — Movement Orders & Digital Service Book**
`MovementOrderService` records transfers/promotions and keeps `employee_employment_categories.regular_basic_pay` in sync; `employee_service_book` (V12, repointed to `grade_scale_id` by V60) is the general append-style career-event ledger, exposed via `EmployeeServiceBookService`/`ServiceBookTab.tsx`.

**FR-PIMS-008 — Superannuation Date Auto-Calculation**
A Postgres trigger (`trg_calc_jci_superannuation` → `fn_calculate_jci_superannuation_date()`, V31) fires on `employees.date_of_birth` insert/update and computes `employee_superannuation_details.superannuation_date`: regular staff at 58, Board Directors (`designations.category_type = 'Director'`) at 60 **or** 5 years from appointment (whichever is earlier), both rounded to month-end (or prior month-end if born on the 1st). A companion stored procedure (`sp_apply_director_ministry_extension`) supports extending a Director's tenure up to (never past) age 60.

**FR-PIMS-009 — Daily Superannuation Sweep & Auto-Release**
`SuperannuationScheduledTask` runs daily at `00:05` (`cron = "0 5 0 * * ?"`) and unconditionally transitions any still-`ACTIVE` employee whose `superannuation_date` has passed to `RETIRED` via `EmployeeReleaseService`, independent of whether an Exit Formalities workflow ever ran — a documented hard safety net.

**FR-PIMS-010 — Automated Exit Dossier Initiation (T-90)**
`ExitInitiationScheduler` runs daily at `00:30` (`cron = "0 30 0 * * ?"`) and auto-creates a draft `exit_clearance_requests` row for any `ACTIVE` employee superannuating within the next 90 days (`LOOKAHEAD_DAYS = 90`) who doesn't already have one open.

**FR-PIMS-011 — Multi-Department Exit Clearance**
`exit_clearance_requests` (V58) models a separation (`separation_type`: `SUPERANNUATION`/`RESIGNATION`/`VRS`/`DECEASED`/`TERMINATED`) with status `INITIATED → CLEARANCE_IN_PROGRESS → CLEARANCES_COMPLETED → RELEASE_ORDER_ISSUED` (or `CANCELLED`). `exit_clearance_items` (one row per department: `ESTABLISHMENT`, `VIGILANCE`, `ESTATE`, `IT`, `FINANCE`, `STORES`, `CPF_TRUST`) tracks per-department clearance (`PENDING`/`CLEARED`/`REJECTED_WITH_DUES`) with a recoverable-dues amount. `ExitClearanceController` exposes creation, checklist retrieval, per-item update, and finalize (`POST /api/v1/exit-clearances`, `.../{id}/checklist`, `PUT .../items/{itemId}`, `POST .../{id}/finalize`).

**FR-PIMS-012 — Terminal Settlement Computation**
`TerminalSettlementService` computes, per employee at separation: statutory gratuity, **DoPT Rule 39** EL/HPL leave encashment (against `el_balance_at_retirement`/`hpl_balance_at_retirement`), and CPF payout (employee + employer + VPF balance + accrued interest), rolling up to `gross_terminal_dues` minus `total_recoveries_deductions` (fed by `exit_clearance_items.dues_recovery_amount`) = `net_terminal_payable`. Settlement status flows `DRAFT → AUDITED → APPROVED → DISBURSED`. `TerminalSettlementController` exposes preview, generate, and approve (`GET /api/v1/settlements/preview/{employeeId}`, `POST .../generate/{employeeId}`, `POST .../{id}/approve`).

**FR-PIMS-013 — Beneficiary Disbursement for Deceased Cases**
`terminal_settlement_beneficiaries` (V58/V59) splits a settlement's payout by `beneficiary_type` (`SELF`/`NOMINEE`/`LEGAL_HEIR`) and `share_percentage`. For `DECEASED` separations, beneficiaries auto-populate from the employee's registered `employee_nominees` (name/relationship/share%) — since nominees carry no bank details, `bank_account_no`/`bank_ifsc` are nullable at insert time (V59 relaxed this) but `TerminalSettlementService.approve()` enforces they're non-blank before allowing `APPROVED` status.

**FR-PIMS-014 — Separated Employee Directory**
`SeparatedEmployeeDirectoryService` lists non-`ACTIVE` employees (`SeparatedStaffTab.tsx`) with status-keyword filtering (e.g., `RETIRED`, `RESIGNED`).

**FR-PIMS-015 — Idempotent Sequential Code Generation**
`CpfAcNoGeneratorService` and (by the same established pattern) `EmployeeCodeGeneratorService` generate sequential identifiers (CPF A/C No, Employee Code) using a **session-scoped PostgreSQL advisory lock** (`employeeRepository.acquireCpfAcNoGenerationLock()`) taken in the same transaction, explicitly in preference to a Java-level `synchronized` method (which would not protect against concurrent requests across multiple app instances).

### 3.2 ALMS

**FR-ALMS-001 — Leave Type Master & Cadre Eligibility**
`leave_types` (V7) seeded (V15/V16) with `CL`, `EL`, `HPL`, `RH`, `COMMUTED`, `MATERNITY`, `PATERNITY`, `CCL`, and `LWP` (V16) — quota/accumulation/career-limit figures are explicitly flagged in the V15 migration comment as **CCS(Leave) Rules 1972-style placeholders, not authoritative HR-approved figures**, pending HR review via `LeaveTypeController`/`LeaveTypeMasterPage.tsx`. `leave_type_cadre_eligibility` (V37) further restricts which employment categories may draw which leave type.

**FR-ALMS-002 — Leave Application Submission & Approval**
`leave_applications` (V7, extended V14/V36) status is `DRAFT → PENDING_APPROVAL → APPROVED / REJECTED` or `CANCELLED` — a **single approval step** referencing one `approver_employee_id`/`approver_post_id`, not a multi-tier recommend-then-approve chain (no `RECOMMENDED` status exists in the schema or `LeaveApplicationService`). `LeaveApplicationController` exposes `POST /api/leave-applications`, `.../preview`, `.../{id}/submit`, `.../{id}/approve`, `.../{id}/reject`, `.../{id}/cancel`. A GiST exclusion constraint (`excl_leave_applications_full_day_overlap`) prevents two overlapping full-day applications for the same employee at the DB level; a second exclusion constraint handles same-session half-day overlaps.

**FR-ALMS-003 — Combined CL + RH Application**
`CombinedLeaveApplicationService`/`CombinedLeaveController` (`group_application_id` on `leave_applications`, V36) allow a single submission spanning CL and an RH date drawn from the `holidays` calendar (`rh_entry_id`).

**FR-ALMS-004 — Leave Ledger (Append-Only Balance Audit Trail)**
`leave_ledger_entries` (V14, extended V59) is an append-only, never-edited debit/credit trail keyed by `source` — currently constrained to `AUTO_LATE_DEDUCTION`, `COMMUTED_LEAVE_HPL_DEBIT`, `BASELINE_TAKEON`, `EL_SEMI_ANNUAL_ACCRUAL`, `EL_EOL_LAPSE_DEDUCTION`, `EL_ENCASHMENT_DEBIT`, `ATTENDANCE_PENALTY_REFUND`, `TRANSFER_JT_CONVERSION`, and `TERMINAL_ENCASHMENT` (the last added by V59 specifically so a terminal settlement's EL/HPL debit is distinguishable from the ordinary discretionary encashment flow). `LeaveLedgerEntryService`/`Controller` exposes read access.

**FR-ALMS-005 — Leave Entitlement Sub-Ledger (EL Encashable/Enjoyable Split)**
`leave_entitlement_balance` (V36) is a richer, per-year balance table than the original `leave_balances` (V7) — both exist and are **not** merged; the newer table tracks an EL-specific encashable/enjoyable day split (`encashable_*`/`enjoyable_*` columns, with a CHECK enforcing `credited_days = encashable_credited + enjoyable_credited`). `LeaveEntitlementBalanceController` exposes read access.

**FR-ALMS-006 — Semi-Annual EL Accrual**
`ElAccrualService` runs on `01-Jan` and `01-Jul` (`cron = "0 0 0 1 1,7 *"`), crediting 15 EL days per half-year to REGULAR employees, with a documented **approximation**: this schema has no dedicated Extraordinary Leave (EOL) tracking, so the EOL 1/10th deduction is approximated as 1/10th of the prior half-year's LWP ledger debits — explicitly flagged in the service's own javadoc as not an exact CCS(Leave) Rules implementation.

**FR-ALMS-007 — Leave Baseline Take-On**
`leave_baseline_initialization` (V36) records verified opening balances (as of go-live/joining) against the physical service book, including the EL encashable/enjoyable split, `is_locked` by default. `LeaveBaselineTakeOnService`/`Controller`/`LeaveBaselineTakeOnPage.tsx` expose this migration-only workflow.

**FR-ALMS-008 — Leave Encashment (In-Service / Superannuation / Separation)**
`leave_encashment_application` (V36) supports `IN_SERVICE_EL` (minimum 15 EL days, no HPL), `SUPERANNUATION`, and `SEPARATION` encashment types, requiring **dual approval** (`hr_approval_status` AND `finance_approval_status` both `APPROVED`) before `is_payroll_eligible` can be set true (DB-enforced via `ck_leave_encashment_dual_approval`). `LeaveEncashmentController`/`LeaveEncashmentPage.tsx` (ESS) expose this.

**FR-ALMS-009 — Shift Master & Scheduling**
`shift_master` (V43: `GEN`/`SHIFT_A`/`SHIFT_B`/`SHIFT_C`, with `crosses_midnight` support) and `employee_shift_schedule`/DPC-level shift defaults (V44) are distinct from the single global `office_timing_config` (V36) used for regular-staff grace-window evaluation. `ShiftMasterService`/`ShiftResolutionService`/`Controller` and `ShiftsMasterTab.tsx`/`DutyRosterPage.tsx` expose this. **Note:** there is no table literally named `roster_shifts` — the real names are `shift_master` and `employee_shift_schedule`.

**FR-ALMS-010 — Biometric/Mobile Punch Ingestion**
`mobile_punches` (V7) captures geotagged IN/OUT punches with `is_within_geofence` and `review_status` (`VALID`/`FLAGGED_FOR_REVIEW`); device registration is tracked separately (V38, `RegisteredDevicesCard.tsx`/`DeviceMasterPage.tsx`).

**FR-ALMS-011 — Daily Attendance Aggregation**
`daily_attendance` (V7, extended V14/V36) is the one-row-per-employee-per-day summary (`status`: `PRESENT`/`ABSENT`/`HALF_DAY`/`ON_LEAVE`/`HOLIDAY`/`WEEKLY_OFF`, plus a fine-grained `detail_status` added in V14 covering grace/late/short-hour states), aggregated by `AttendanceAggregationService` against `office_timing_config`'s grace/concession windows. **Note:** there is no table literally named `alms_attendance_summaries` — `daily_attendance` is this codebase's attendance-summary table (the V36 migration's own header comment explicitly reconciles this naming).

**FR-ALMS-012 — Attendance Regularization Workflow**
`attendance_regularization_applications` (V36) lets an employee submit a corrected in/out time against a reason code (`FORGOT_PUNCH`, `DEVICE_FAILURE`, `FIELD_DUTY`, `GEOFENCE_ISSUE`, `SYSTEM_ERROR`, `OTHER`) for HR approval, via `AttendanceRegularizationService`/`Controller` and `RegularizationModal.tsx`/`RegularizationApprovalQueuePage.tsx`.

**FR-ALMS-013 — Attendance-Linked Leave Deduction**
`AttendanceLeaveDeductionService` writes `leave_ledger_entries` with source `AUTO_LATE_DEDUCTION` or `COMMUTED_LEAVE_HPL_DEBIT` when attendance patterns trigger an automatic leave debit.

**FR-ALMS-014 — Holiday Calendar Master**
`holidays` (V14, extended multi-state V39) distinguishes `GAZETTED` vs `RESTRICTED` (RH) holidays; `HolidayMasterService`/`Controller` and `HolidayCalendarCard.tsx` expose this. RH leave applications reference a specific `holidays` row via `rh_entry_id`.

**FR-ALMS-015 — Attendance Payroll Cutoff Freeze (schema present, not wired)**
`attendance_payroll_cutoff` (V36) models a monthly attendance-freeze window (`period_start`/`period_end`, `is_frozen`). **As implemented, no service writes to this table** — `PayrollComputationService.deriveCycleDates()` derives its cutoff window on the fly instead. This is schema-only scaffolding, not an active control, and should be treated as a gap rather than an enforced freeze if relied upon.

**FR-ALMS-016 — LOP Computation for Payroll**
`PayrollComputationService.computeLopDays()` counts `daily_attendance` rows with status `ABSENT` (1.0 day) or `HALF_DAY` (0.5 day) within a payroll cycle window and prorates basic pay accordingly. A day with **no** `daily_attendance` row at all is *not* counted as LOP — documented as deliberate, since no daily batch process yet populates one row per employee per working day.

---

## 4. Non-Functional Requirements

**NFR-1 — Audit Logging.** Every auditable entity implements the `Auditable` marker interface (`in.gov.jci.hrms.audit.Auditable`), captured by `AuditableEntityListener` (a JPA entity listener) writing before/after JSON snapshots to the append-only `audit_logs` table (V5: `entity_name`, `entity_id`, `action` [`CREATE`/`UPDATE`/`DELETE`], `acting_username`, `client_ip`, `before_state`/`after_state` JSONB) with no `deleted_at` — audit rows are intentionally not soft-deletable. Exposed for review via `AuditLogController`.

**NFR-2 — Data Consistency.** Soft-delete (`deleted_at`) is the dominant deletion pattern across master-data tables (`post_master`, `leave_types`, `holidays`, `employee_service_book`, etc.), paired with partial unique indexes scoped to `WHERE deleted_at IS NULL` so a soft-deleted row's business key can be reused. Status transitions are DB-enforced via `CHECK` constraints on every workflow table (`exit_clearance_requests.status`, `leave_applications.status`, `terminal_settlements.status`, etc.), not left to application-layer discipline alone.

**NFR-3 — Concurrency & Idempotency.** Sequential-identifier generation (CPF A/C No, Employee Code) uses PostgreSQL session-scoped advisory locks taken inside the writing transaction (`CpfAcNoGeneratorService`), explicitly chosen over a JVM-level `synchronized` method because the latter offers no protection across multiple application instances. Overlapping leave applications for the same employee are prevented via PostgreSQL `EXCLUDE USING gist` constraints (`excl_leave_applications_full_day_overlap`, `excl_leave_applications_half_day_session_overlap`) rather than only an application-level check — a DB-level guarantee immune to race conditions between concurrent requests. At most one active Substantive post incumbent is enforced the same way (`uq_post_incumbency_post_substantive_active`).

**NFR-4 — Performance.** `EmployeeRepository`'s `findById`/`findAll`/`findAll(Specification, Pageable)` methods use `@EntityGraph` to eagerly fetch `department`, `designation`, `regionalOffice`, and `departmentalPurchaseCentre` in one query, avoiding N+1 lazy-load queries per row when rendering the Employee Directory or Master 360 profile.

**NFR-5 — Data Residency & Compliance.** All infrastructure is pinned to AWS `ap-south-1` (Mumbai) per `CLAUDE.md`, a stated data-residency requirement rather than a cost choice. The system anticipates Aadhaar-linked data and lists STQC audit, CERT-In empanelled VAPT, MeitY/GI Cloud empanelment, and IT Security Committee sign-off as pre-production/pre-scale-up requirements — these are organizational/process requirements the codebase does not yet implement controls for, and should not be assumed satisfied by current code alone.
