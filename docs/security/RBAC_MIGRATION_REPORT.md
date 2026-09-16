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
- `JciEccsMemberController.changeStatus` (membership suspension/cessation under Bye-laws 15/16) and `TerminalSettlementController.approve` (HR terminal/separation settlement) — governance/HR actions, not unambiguously "CPF/Payroll/Disbursement/JCIECCS financial approval" per the named critical categories at the time of this pass; SUPER_ADMIN has since been removed from all three (and from `PayrollMasterController`'s rate-edit endpoints below) in the 2026-09-16 non-financial migration pass — see that section further down for what changed and what remains REQUIRES_BUSINESS_CONFIRMATION.
- `PayrollMasterController`'s three `SUPER_ADMIN`-inclusive endpoints (`updateSalaryHead`, `updateStatutoryHead`, `reviseStatutoryParameter`) — master-data/rate configuration, not a specific transaction's financial approval at the time of this pass; see the 2026-09-16 pass below.

**Remaining ~64 non-financial controllers** (HR/personnel, establishment masters, leave/attendance, reports, technical admin — e.g. `EmployeeController` and its sub-resource controllers, `DepartmentController`/`DesignationController`/`RegionalOfficeController`, `AlmsReportController`/`ReportingController`, `AuditLogController`/`DocumentUploadController`) were inventoried by name but not individually re-audited this pass — none were found to gate a CPF/Payroll/Disbursement/JCIECCS financial-approval action, so per the critical requirement's own scope (financial checker-only operations) they carry no confirmed god-role financial-bypass risk. A full per-endpoint classification against `RBAC_PERMISSION_MATRIX.md` for these remains future work, tracked as before under "What was explicitly NOT migrated."

## Non-financial legacy RBAC migration pass (2026-09-16) — closes the "Legacy non-financial RBAC migration remains open" item

Baseline for this pass: `5d67639`. Scope: the ~64 non-financial controllers left untouched by the SEC-010 closure pass above, plus the three items that pass explicitly deferred (`JciEccsMemberController.changeStatus`, `TerminalSettlementController.approve`, `PayrollMasterController`'s 3 rate-edit endpoints), plus two genuinely financial controllers (`LoanController`, `PfLedgerController`) discovered missed by the original SEC-003/004 closure.

**Migrated:** 65 files (64 controllers + `AttendanceAggregationSecurity`, the shared SEC-002/005/006/007 ownership-check helper). `SUPER_ADMIN` occurrences in `src/main/java/in/gov/jci/hrms/controller`: **232 before → 28 after** (the 26 remaining CPF/JCIECCS/Loan/PfLedger/PayrollRun occurrences are class-level or maker/read endpoints on financial controllers, unchanged from the prior SEC-010 closure pass and re-verified here as not a checker bypass; the 2 remaining are `StateMasterController`/`DistrictMasterController`'s sole `hasRole('SUPER_ADMIN')`, deliberately left — see below).

**Mechanism:** for the 61 purely non-financial controllers, `'SUPER_ADMIN'` was removed from each `hasAnyRole(...)`/`hasRole(...)` list, keeping whatever functional role(s) were already listed alongside it (overwhelmingly `HR_ADMIN` — 134 of the 232 original occurrences were the single pattern `hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')`). No new permission model migration was attempted for these: `RBAC_PERMISSION_MATRIX.md` confirms the reserved `HR_ADMIN_PERS`/`HR_MAKER_PERS`, `HR_ADMIN_EST`/`HR_MAKER_EST`, and `IT_ADMIN_STORE`/`IT_MAKER_STORE` role pairs carry **zero seeded permissions and gate no live endpoint** — seeding permissions for them now, with no existing endpoint or business specification to derive them from, would be inventing business rules, which this task's instructions explicitly prohibit. Removing the `SUPER_ADMIN` god-role bypass while keeping the existing legacy functional role is therefore the correct, minimal, non-invented fix for this bucket.

**The three previously-deferred items — now implemented:**
- `JciEccsMemberController.changeStatus` — `SUPER_ADMIN` removed. Now `hasRole('COOP_ADMIN') or @rbac.hasPermission(authentication, 'JCIECCS_APPROVE')` — the legacy `COOP_ADMIN` role is retained (no real `UserRoleAssignment` data exists yet to grant the new `JCIECCS_ADMIN` role to anyone — `application_users` is empty outside the SYSTEM_ADMIN bootstrap), and the already-seeded `JCIECCS_APPROVE` permission is wired as the forward-looking path, per RBAC_SECURITY_REQUIREMENTS's preference for the canonical DB-backed model where the matrix already provides it. Verified: `JciEccsMemberControllerSecurityTest` (SUPER_ADMIN-only + no permission → 403; COOP_ADMIN → not forbidden; USER + `JCIECCS_APPROVE` granted → not forbidden; USER without either → 403).
- `TerminalSettlementController.approve` — **IMPLEMENTED 2026-09-16** (see "Final RBAC business-authority closure" below): now requires `DISBURSEMENT_AUTHORIZE` (`FIN_ADMIN_DISB`), not any role name. `HR_ADMIN` no longer works here (it is denied, same as `SUPER_ADMIN`).
- `PayrollMasterController.updateSalaryHead`/`updateStatutoryHead`/`reviseStatutoryParameter` — **IMPLEMENTED 2026-09-16**: now require `PAYROLL_MASTER_APPROVE` (`HR_ADMIN_BILL`), not any role name.

**Supplemental financial finding (not part of the original SEC-003/004 scope, closed the same way):** `LoanController` (`/api/loans`, a general employee-advance module distinct from CPF/JCIECCS loans) and `PfLedgerController` (`/api/cpf-ledger`) both had `disburse`/`foreclose`/`settle` — checker-shaped financial actions — reachable by `SUPER_ADMIN` alone via their class-level grant. `SUPER_ADMIN` narrowed off those three specific endpoints only (class-level maker/read access unchanged). No maker != checker enforcement exists in `LoanService`/`PfLedgerService` for this module (unlike `CpfLoanApplicationService.requireDifferentFromApplicant`) — adding that is a business-workflow change out of this authorization-only task's scope and was not attempted. Verified: `LoanControllerTest.disburse_withOnlySuperAdminRole_returns403`/`foreclose_withOnlySuperAdminRole_returns403`, `PfLedgerControllerTest.settle_withOnlySuperAdminRole_returns403`.

**SEC-002/005/006/007 shared evaluator:** `AttendanceAggregationSecurity.canActOnBehalfOfOthers` (backing `MobilePunchController`, `AttendanceRegularizationController`, `LeaveEncashmentController`, `LeaveApplicationController`) had a hardcoded `ROLE_HR_ADMIN || ROLE_SUPER_ADMIN` check — acting on another employee's attendance/leave is an HR business action, not technical administration, so `SUPER_ADMIN` was removed, leaving `HR_ADMIN` only. This is the one non-controller-annotation SUPER_ADMIN grant found this pass. Verified: `AttendanceAggregationSecurityTest` (11 tests, including the two updated to assert SUPER_ADMIN is now denied).

**Retained, explained, sole-role occurrences (NON_FINANCIAL_COMPATIBILITY, deliberately untouched):**
- `StateMasterController`/`DistrictMasterController` — **IMPLEMENTED 2026-09-16**: now require `ESTABLISHMENT_VIEW`/`ESTABLISHMENT_MAINTAIN` (`HR_ADMIN_EST`), not `SUPER_ADMIN` or any other role. (Pre-existing, separately tracked inconsistency, still open: SEC-011 already flags that `MasterStateController`, a different controller over similar data, uses `isAuthenticated()` instead — not resolved by this pass either.)

**Test regressions found and fixed (pre-existing tests that asserted the old SUPER_ADMIN grant as correct):** `DaRateHistoryControllerTest.create_asSuperAdmin_returns201` → renamed/inverted to assert 403; `HolidayMasterControllerTest.update_asSuperAdmin_returns200` → same; `LeaveTypeMasterControllerTest.update_asSuperAdmin_returns200` → same. All three now correctly assert the closed bypass.

**SEC-005/006/007:** already COMPLIANT since the original RBAC phase (before `54ff421`) via `@attendanceAggSec.canEvaluateFor(...)`/service-layer re-checks on `AttendanceRegularizationController`/`LeaveEncashmentController`/`LeaveApplicationController`. This pass only removed `SUPER_ADMIN`'s implicit "act on behalf of others" grant from the shared helper those three (and SEC-002's `MobilePunchController`) depend on — the ownership/BOLA mechanism itself was not changed and remains proven by the existing `AttendanceRegularizationServiceTest`/`LeaveEncashmentServiceTest`/`LeaveApplicationServiceTest`/`LeaveApplicationControllerTest` suites, all still passing.

**Status as of the 2026-09-16 non-financial migration pass:** SEC-010 remains recorded as "COMPLIANT — financial authorization boundary. Legacy non-financial RBAC migration remains open" (see `VAPT_READINESS_REGISTER.md`) — the 3 items above were REQUIRES_BUSINESS_CONFIRMATION at that time. **All three are now implemented** — see "Final RBAC business-authority closure" below.

## Final RBAC business-authority closure (2026-09-16, second pass same day)

Baseline: `b9d027e`. Confirmed business direction was given for the three items above; this pass implements them. Migration `V97__payroll_master_and_establishment_permissions.sql` (additive only, V1-V96 untouched) adds the permissions needed - `HR_ADMIN_BILL`/`HR_MAKER_BILL`/`HR_ADMIN_EST` already existed as roles from V94 with zero or unrelated grants.

**Terminal settlement.** Traced the actual lifecycle before changing anything (`TerminalSettlementService.approve()`): the status enum is `DRAFT -> AUDITED -> APPROVED -> DISBURSED`, but `AUDITED` and `DISBURSED` are never reached by any code path - `generate()` creates `DRAFT`, `approve()` is the only other transition, going straight to `APPROVED`. `approve()` validates the beneficiary bank/share allocation and irreversibly debits the encashed EL/HPL leave ledger - it is the final financial-authorization checkpoint actually implemented today, not merely HR data verification (2A applies). It now requires `DISBURSEMENT_AUTHORIZE` (`FIN_ADMIN_DISB`'s existing permission, reused as-is, no new permission needed) instead of any role. `preview`/`generate`/`getById`/`updateBeneficiaries` (the HR preparation stage) are unchanged - still `HR_ADMIN`. **Maker-checker**: `TerminalSettlement` has no preparer/generatedBy actor column (only `employee`, the settlement's *subject*, and timestamps) - enforcing preparer != approver would need a schema change. **FOLLOW_UP_REQUIRED — TERMINAL_SETTLEMENT_MAKER_CHECKER**, not invented here. Status: **IMPLEMENTED_WITH_FOLLOW_UP** (the authority boundary is closed; maker-checker is a genuine technical follow-up, not an unresolved business decision).

**Payroll master.** Traced `PayrollMasterServiceImpl.updateSalaryHead`/`.updateStatutoryHead` and `PayrollStatutoryParameterServiceImpl.revise`: all three mutate live master data in one immediate step - no pending/draft status, no prepare-then-approve persistence. Per the confirmed ownership (maker `HR_MAKER_BILL`, checker/admin `HR_ADMIN_BILL`) and the explicit instruction not to manufacture a workflow that doesn't exist, all three endpoints are gated by the new `PAYROLL_MASTER_APPROVE` permission only (`HR_ADMIN_BILL`); `HR_MAKER_BILL` holds `PAYROLL_MASTER_VIEW`+`PAYROLL_MASTER_EDIT` but is correctly denied these checker-only endpoints, since no safe maker-only operation exists to grant it instead. **FOLLOW_UP_REQUIRED — PAYROLL_MASTER_MAKER_CHECKER**: `PAYROLL_MASTER_EDIT` is seeded (matching the expected VIEW/EDIT/APPROVE mapping and this codebase's own existing convention of seeding reserved-but-unwired permissions, e.g. V94's original CPF_*/JCIECCS_* grants before their endpoints existed) but deliberately unwired to any endpoint until a real prepare/approve status design is built. Status: **IMPLEMENTED_WITH_FOLLOW_UP**.

**State/District master.** Traced `StateMasterService`/`DistrictMasterService`: `create`/`update`/`updateStatus`/`delete` are all immediate single-step mutations, no maker/checker persistence of any kind. Per the confirmed ownership (`HR_ADMIN_EST`, no maker/checker manufactured), the class-level `hasRole('SUPER_ADMIN')` on both controllers was split into per-method `@rbac.hasPermission(...)` checks: `ESTABLISHMENT_VIEW` for reads, `ESTABLISHMENT_MAINTAIN` for create/update/status/delete, both granted to `HR_ADMIN_EST` only (`HR_MAKER_EST` receives nothing, per the explicit instruction not to invent a maker step this data has no room for). Status: **IMPLEMENTED** (no follow-up - this data genuinely has no maker/checker distinction to model).

**Legacy role compatibility deliberately NOT retained for these three closures**, unlike the earlier `JciEccsMemberController.changeStatus`/`COOP_ADMIN` precedent: the confirmed business direction named exact new roles (`FIN_ADMIN_DISB`, `HR_ADMIN_BILL`, `HR_ADMIN_EST`) with no legacy analog naturally already present on any of these four controllers, and the test specification that came with the confirmation explicitly requires `HR_ADMIN`-only and `SUPER_ADMIN`-only to both be denied on the closed endpoints - so no role-based fallback was added. A practical consequence: these six endpoints (`approve`, the 3 payroll-master endpoints, and all of State/District master) are inaccessible to any caller until a real `ApplicationUser` is explicitly granted `FIN_ADMIN_DISB`/`HR_ADMIN_BILL`/`HR_ADMIN_EST` via the existing `USER_ROLE_ASSIGN` mechanism - expected, not a regression, since `application_users` has no real assignments yet outside the `SYSTEM_ADMIN` bootstrap (see "Migration of existing users" above).

**Verified via updated/new tests:** `TerminalSettlementControllerSecurityTest` (SUPER_ADMIN/HR_ADMIN/unrelated role → 403; `DISBURSEMENT_AUTHORIZE` permission → reaches the service), `PayrollMasterControllerTest` (SUPER_ADMIN/FINANCE_ADMIN → 403; `PAYROLL_MASTER_EDIT`-only → 403; `PAYROLL_MASTER_APPROVE` → 200), `StateMasterControllerTest`/`DistrictMasterControllerTest` (rewritten: `ESTABLISHMENT_MAINTAIN`/`ESTABLISHMENT_VIEW` → allowed; no permission, and `SYSTEM_ADMIN` role alone without the permission → 403).

**REQUIRES_BUSINESS_CONFIRMATION count from this task: 0.** Two genuine technical follow-ups remain (`TERMINAL_SETTLEMENT_MAKER_CHECKER`, `PAYROLL_MASTER_MAKER_CHECKER`) - these are implementation work for a future phase, not unresolved business decisions.
