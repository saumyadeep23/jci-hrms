# RBAC Migration Report

## Legacy role inventory (confirmed via `CURRENT_AUTHORIZATION_MODEL.md`, re-verified unchanged this phase)

Exactly 7 legacy JWT-claim-based role strings exist and remain **fully functional and untouched**: `SUPER_ADMIN`, `HR_ADMIN`, `FINANCE_ADMIN`, `CPF_ADMIN`, `COOP_ADMIN`, `BILL_SUPERVISOR`, `EMPLOYEE`. `JwtRoleConverter` is unmodified.

## What was migrated this phase (endpoint-by-endpoint, not bulk)

| Legacy check | Controller | New check added | Old check removed? |
|---|---|---|---|
| `hasAnyRole('EMPLOYEE','HR_ADMIN','SUPER_ADMIN')` on submit | `AttendanceRegularizationController` | `@attendanceAggSec.canEvaluateFor(...)` (SEC-005) | Yes — replaced (ownership-aware) |
| `hasAnyRole('EMPLOYEE','HR_ADMIN','SUPER_ADMIN')` on apply | `LeaveEncashmentController` | Same pattern (SEC-006) | Yes — replaced |
| Class-level `hasAnyRole('EMPLOYEE','HR_ADMIN','SUPER_ADMIN')` on create | `LeaveApplicationController` | Method-level override (SEC-007) | Yes — replaced on `create()` only; other methods (`update`/`submit`/`approve`/`reject`/`cancel`/`list`) untouched |
| `isAuthenticated()` on punch create | `MobilePunchController` | `@attendanceAggSec.canEvaluateFor(...)` (SEC-002, prior phase) | Yes — replaced |
| N/A (new functionality) | `AdminOnboardingController`, `AdminUserRoleController` | `@rbac.hasPermission(...)` (new DB-backed model) | N/A |

## What was explicitly NOT migrated (deliberate — see reasoning)

**The remaining ~92 legacy-gated controllers (~255 of ~260 `@PreAuthorize` occurrences) are unchanged.** This was a deliberate scope decision, not an oversight:

1. **Risk**: a global find-replace of `SUPER_ADMIN`/`HR_ADMIN`/etc. across 96 controllers, without individually re-verifying each endpoint's actual business-responsibility classification (per RBAC_SECURITY_REQUIREMENTS.md instruction 14/15: "Inventory every SUPER_ADMIN use first, classify each occurrence... then migrate each to the correct permission/role boundary"), risks silently breaking or silently over-broadening authorization on financially/legally sensitive endpoints this task did not individually audit line-by-line this pass.
2. **Explicit instruction**: "Do not perform a dangerous global text replacement" and "Do not blindly mark findings remediated."
3. **Test-suite stability**: the existing 1506+ backend tests are built almost entirely against the legacy `@WithMockUser(roles=...)` convention. Migrating a controller wholesale to `@rbac.hasPermission` requires that controller's entire existing test suite to be rewritten to seed real `ApplicationUser`/`UserRoleAssignment` fixtures — a large, controller-by-controller effort, not something safe to rush.

### Legacy → new role conceptual mapping (for future migration passes, NOT applied to code this phase)

| Legacy | New (typical) | Caveat |
|---|---|---|
| `SUPER_ADMIN` | `SYSTEM_ADMIN` for technical/admin actions **only** | **Never** map `SUPER_ADMIN` to a financial ADMIN role wholesale — every current `SUPER_ADMIN` occurrence on a financial endpoint (CPF sanction/disburse, JCIECCS approve/reverse, Payroll finalize) needs its own explicit `FIN_ADMIN_*`/`JCIECCS_ADMIN`/`HR_ADMIN_BILL` grant, decided per-endpoint. **REQUIRES_BUSINESS_CONFIRMATION** for every such endpoint — not attempted this phase. |
| `FINANCE_ADMIN` | `FIN_ADMIN_CPF` / `FIN_ADMIN_DISB` (endpoint-dependent — CPF endpoints vs. payroll-disbursement endpoints currently share this one legacy role) | Splitting requires knowing, per endpoint, which of the two the original author intended — not always obvious from the code alone. |
| `CPF_ADMIN` | `FIN_ADMIN_CPF` | Reasonably direct 1:1 for CPF-specific endpoints. |
| `COOP_ADMIN` | `JCIECCS_ADMIN` | Reasonably direct 1:1. |
| `BILL_SUPERVISOR` | `HR_ADMIN_BILL` | Narrow legacy usage — needs confirmation of exact endpoints before mapping. |
| `EMPLOYEE` | `USER` | Direct — this is the baseline self-service role in both models. |
| `HR_ADMIN` | `HR_ADMIN` (name reused directly in the new model too) | Already a 1:1 name match; scope/permission granularity still needs per-endpoint review. |

## Migration of existing users

**No production/real user data exists to migrate** — `application_users` is a brand-new table (V94) with zero rows before this phase, and this task created none for any real employee except through the (untested-against-real-employees) bootstrap path for employee 8 (which itself only runs against whatever `employee_id=8` the target database actually has — not exercised against production data in this task; see `MAKER_CHECKER_IMPLEMENTATION.md`/bootstrap tests, all Mockito-only, no real DB touched). There is no legacy user table to migrate from (the whole point of SEC-001's finding was that JWT claims were the *only* prior authorization signal, with no DB-backed user concept at all) — so "migrating existing users" is not applicable; this phase is additive-only onto an empty table.

## SUPER_ADMIN business-function migration — explicitly flagged

Per instruction 71: **do not silently grant financial roles** when migrating `SUPER_ADMIN`. Every current `SUPER_ADMIN`-gated financial endpoint (CPF/Payroll/JCIECCS sanction/disburse/finalize/reverse — see `CURRENT_AUTHORIZATION_MODEL.md`'s endpoint matrix for the full list) requires an explicit, individually-confirmed decision about which real officer(s) should receive `FIN_ADMIN_CPF`/`FIN_ADMIN_DISB`/`JCIECCS_ADMIN`/`HR_ADMIN_BILL` — this is **REQUIRES_BUSINESS_CONFIRMATION**, not resolved by this phase, and must not be inferred from the code alone.

## SEC-010 closure pass (2026-09-16) — targeted SUPER_ADMIN inventory and financial-bypass removal

A targeted (not repository-wide) inventory found `SUPER_ADMIN` referenced 246 times across 87 controller files. Rather than granting a new `FIN_ADMIN_*`/`JCIECCS_ADMIN` permission set (which would still need the REQUIRES_BUSINESS_CONFIRMATION decision above about who receives it), this pass took the narrower, zero-new-permission action of **removing `SUPER_ADMIN` from the `hasAnyRole(...)` list on exactly the checker-only financial-approval endpoints already covered by an existing, sufficient functional role** (`CPF_ADMIN`/`FINANCE_ADMIN`/`COOP_ADMIN`) — closing the god-role bypass without inventing new roles or needing a business decision, since no legitimate caller needs `SUPER_ADMIN` specifically to perform these actions today (no real users/IdP exist yet per `CLAUDE.md`).

**Endpoints narrowed (SUPER_ADMIN removed, existing functional role kept, same method-level-override convention already used by `CpfDisputeAdminController`/`CpfInterestRunController`'s class-vs-method split):**

| Controller | Endpoint(s) | Category |
|---|---|---|
| `CpfLoanController` | `sanction`, `disburse`, `reject`, `settle-cash` | CPF |
| `CpfApplicationController` | `sanction`, `disburse`, `reject` | CPF |
| `CpfDisputeAdminController` | `assign`, `start-review`, `request-clarification`, `resolve`, `reject` | CPF |
| `CpfInterestRunController` | `calculate`, `post`, `reverse` | CPF |
| `CpfWithdrawalMasterController` | its one write endpoint | CPF |
| `CpfWithdrawalRuleController` | all 5 write endpoints | CPF |
| `CpfStatutoryInterestRateController` | its POST (create) endpoint | CPF |
| `PayrollRunController` | `finalize` (`create`/`compute` deliberately left alone — maker side) | PAYROLL_BILL (finalization) |
| `JciEccsReconciliationController` | `resolve` (`run`/reads left alone) | JCIECCS |
| `JciEccsSettlementController` | `clear` (`calculate`/reads left alone) | JCIECCS |

Disbursement authorization has no separate controller in this codebase — CPF disbursement is `CpfLoanController.disburse`/`CpfApplicationController.disburse`, both covered above.

**CPF settle-cash gap (separately flagged, now closed):** `CpfLoanSettlementService.processCashSettlement` had no maker!=checker enforcement at all (a real gap — settlement mutates outstanding balance, credits the member's EE ledger, and can close the loan, exactly like sanction/disburse). It now rejects when `receivedByOfficerId` equals the loan's `applicantEmployeeId`, mirroring SEC-003's `requireDifferentFromApplicant` exactly. See `MAKER_CHECKER_IMPLEMENTATION.md`.

**Verified via new tests:** `CpfLoanControllerSecurityTest` (SUPER_ADMIN-only → 403 on sanction/disburse/reject/settle-cash; CPF_ADMIN → not forbidden), `PayrollRunControllerTest` (SUPER_ADMIN-only → 403 on finalize; still allowed on create), `CpfLoanTwoPhaseAndSettlementTest` (settle-cash same-applicant → `AccessDeniedException`, no balance mutation; different-actor → succeeds).

**Deliberately NOT touched, and why (no financial-approval risk identified):**
- `JciEccsLoanController`/`JciEccsPayrollBatchController` (class-level) — these are the JCIECCS loan/repayment **maker**-side endpoints (create, cash-repayment posting, restructure); maker actions have no checker-separation requirement by design (see `MAKER_CHECKER_IMPLEMENTATION.md`: "recovery creation... is the maker step by definition").
- `JciEccsRecoveryController` — read-only (`list`/`get`); `reverseRecovery` (the actual SEC-004-protected checker action) has **no REST endpoint at all** in this codebase yet, so there is currently no HTTP path for a `SUPER_ADMIN`/anyone to call it.
- `JciEccsMigrationController`, `JciEccsAuditTrailController`, `JciEccsIntegrityCheckController` — one-time historical dataload / audit / diagnostic tooling, not a live financial-approval action.
- `JciEccsMemberController.changeStatus` (membership suspension/cessation under Bye-laws 15/16) and `TerminalSettlementController.approve` (HR terminal/separation settlement) — governance/HR actions, not unambiguously "CPF/Payroll/Disbursement/JCIECCS financial approval" per the named critical categories; flagged below as REQUIRES_BUSINESS_CONFIRMATION rather than changed on assumption.
- `PayrollMasterController`'s three `SUPER_ADMIN`-inclusive endpoints (`updateSalaryHead`, `updateStatutoryHead`, `reviseStatutoryParameter`) — master-data/rate configuration, not a specific transaction's financial approval; same REQUIRES_BUSINESS_CONFIRMATION treatment.

**Remaining ~64 non-financial controllers** (HR/personnel, establishment masters, leave/attendance, reports, technical admin — e.g. `EmployeeController` and its sub-resource controllers, `DepartmentController`/`DesignationController`/`RegionalOfficeController`, `AlmsReportController`/`ReportingController`, `AuditLogController`/`DocumentUploadController`) were inventoried by name but not individually re-audited this pass — none were found to gate a CPF/Payroll/Disbursement/JCIECCS financial-approval action, so per the critical requirement's own scope (financial checker-only operations) they carry no confirmed god-role financial-bypass risk. A full per-endpoint classification against `RBAC_PERMISSION_MATRIX.md` for these remains future work, tracked as before under "What was explicitly NOT migrated."

**Business authorization direction received (2026-09-16 checkpoint) — documented here, code change NOT made this commit** (no production-functionality change and no invented role mapping in this commit; implementing these three is the next concrete SEC-010 step):
- `JciEccsMemberController.changeStatus` — SYSTEM_ADMIN/legacy `SUPER_ADMIN` must not independently suspend/cease a JCIECCS membership. Direction: keep this under JCIECCS functional administration — remove `SUPER_ADMIN` from `hasAnyRole('COOP_ADMIN', 'SUPER_ADMIN')`, leaving `COOP_ADMIN` only.
- `TerminalSettlementController.approve` — SYSTEM_ADMIN/legacy `SUPER_ADMIN` must not independently approve a terminal financial settlement. Direction: remove `SUPER_ADMIN` from `hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')`, leaving `HR_ADMIN`. The exact functional-checker-role mapping (whether `HR_ADMIN` itself is the correct, final checker distinct from whoever initiates the settlement) is explicitly left open for workflow review, not resolved by inventing a new role here.
- `PayrollMasterController.updateSalaryHead`/`updateStatutoryHead`/`reviseStatutoryParameter` — SYSTEM_ADMIN must not be treated as an alternate functional approver for payroll/statutory financial master changes merely because it is the technical administrator. Direction: remove `SUPER_ADMIN` from each, leaving the existing `HR_ADMIN`/`FINANCE_ADMIN` roles already listed.

None of these three had their code changed in this commit — each still lists `SUPER_ADMIN` in its `@PreAuthorize` as of this checkpoint. Per instruction, no new role mapping is invented for any of them beyond the roles already present in each endpoint's existing `hasAnyRole(...)` list.
