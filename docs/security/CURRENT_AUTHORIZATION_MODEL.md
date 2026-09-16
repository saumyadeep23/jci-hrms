# JCI HRMS — Current Authorization Model (Pre-RBAC)

**Purpose:** Document the authorization mechanisms that exist in the codebase *today*, as input to the future RBAC design. This is an inventory, not a migration plan — no roles are changed here.

---

## 1. How authorization is expressed today

100% method-security (`@EnableMethodSecurity` in `backend/src/main/java/in/gov/jci/hrms/security/SecurityConfig.java:34`). There is **no** `requestMatchers(...).hasRole(...)`-style URL-pattern role gating in `SecurityConfig` — the only `requestMatchers` calls are the four `permitAll` paths (see `ATTACK_SURFACE_INVENTORY.md` §1.1). Every other authorization decision is a `@PreAuthorize` (or occasionally `@Secured`) SpEL expression on a controller class or method.

Two SpEL patterns are used throughout:

1. **Role-only**: `hasAnyRole('X','Y','Z')` — the majority pattern.
2. **Role-or-object-ownership**: `hasAnyRole('X','Y') or @someSecurityBean.isSelf(authentication, #id)` — a custom `@Component` bean (e.g. `EmployeeSecurity`, `LoanSecurity`, `CpfApplicationSecurity`, `LeaveApplicationSecurity`, `AttendanceAggregationSecurity`, `AparSecurity`, `@jciEccsSec`) loads the target entity and compares its owning-employee id against the caller's own id (see §3).

## 2. Current role model — every distinct role string in use

Grepped exhaustively for `hasRole`, `hasAnyRole`, `hasAuthority`, `hasAnyAuthority`, `@Secured`, `ROLE_` literals across the entire backend. **Exactly 7 distinct role strings exist in the codebase today:**

| Role | Approx. frequency in `@PreAuthorize` | Typical scope observed |
|---|---|---|
| `SUPER_ADMIN` | ~85 of ~260 occurrences (allow-listed in almost every sensitive endpoint) | De facto god-role — technical admin, HR admin, and every financial approval action all include `SUPER_ADMIN` |
| `HR_ADMIN` | Broad | Employee master data, master/reference tables, leave, onboarding drafts |
| `FINANCE_ADMIN` | Broad | Payroll, CPF, JCIECCS financial actions |
| `CPF_ADMIN` | CPF-specific | CPF loan apply/sanction/disburse/reject, passbook admin views |
| `COOP_ADMIN` | JCIECCS-specific | JCIECCS loan/recovery/settlement/reconciliation/member/migration |
| `BILL_SUPERVISOR` | Narrow | Payroll billing-adjacent |
| `EMPLOYEE` | Self-service | Leave application/encashment creation, attendance punch/regularization submission |

No `ROLE_` prefix literal appears anywhere in application code — Spring Security auto-prefixes `ROLE_` for `hasRole`/`hasAnyRole` SpEL, and `JwtRoleConverter.java:20,38` independently prefixes `ROLE_` when building `GrantedAuthority` objects from the JWT's `realm_access.roles`/`resource_access.*.roles` claims. Both are consistent with each other.

**These 7 roles are exactly what the future RBAC phase's expanded role set (`SYSTEM_ADMIN`, `HR_ADMIN_PERS/BILL/EST`, `HR_MAKER_PERS/BILL/EST`, `FIN_ADMIN_CPF/DISB`, `FIN_MAKER_CPF/DISB`, `JCIECCS_ADMIN`/`JCIECCS_MAKER`, `IT_ADMIN_STORE`/`IT_MAKER_STORE`) must replace.** There is no existing partial migration, no dual-role-support code, and no deprecation shim — a clean replacement.

## 3. Object-level authorization ("SELF-scope") — existing pattern, correctly used in most places

A consistent, well-designed idiom already exists: a small `@Component` "security" bean per domain, exposing an `isSelf(Authentication, id)` (and sometimes `isApprover`/`isParticipant`/`isReportingOfficer`) method, referenced from `@PreAuthorize` SpEL. Confirmed correctly wired for:

| Domain | Security bean | Controllers using it |
|---|---|---|
| Employee master + sub-resources | `EmployeeSecurity` (`@employeeSecurity`) | `EmployeeController`, `EmployeeBankAccountController`, `EmployeeAddressController`, `EmployeeFamilyController`, `EmployeeDependentController`, `EmployeeNomineeController`, `EmployeeQualificationController`, `EmployeePastServiceRecordController`, `EmployeeSocialProfileController`, `EmployeeVehicleAllotmentController`, `EmployeeQuarterAllotmentController`, `ReportingController`, `ServiceBookController`, `LegacyMigrationController` |
| Employee loans | `LoanSecurity` (`@loanSec`) | `LoanController` — loads the `EmployeeLoan` and compares `.getEmployee().getId()` |
| CPF self-service | `CpfApplicationSecurity` (`@cpfApplicationSec`) | `CpfApplicationController` |
| Leave applications | `LeaveApplicationSecurity` (`@leaveSec`) | `LeaveApplicationController` — `getById`/`update`/`submit`/`approve`/`reject`/`cancel` (**but not `create()` — see gap below**) |
| Attendance aggregation | `AttendanceAggregationSecurity` | Attendance summary/reporting endpoints |
| APAR | `AparSecurity` (`@aparSec`) | `AparController` — `isParticipant`/`isSelf`/`isReportingOfficer`/`isReviewingOfficer`, the most granular pattern in the codebase |
| JCIECCS members | `JciEccsSecurity` (`@jciEccsSec`) | `JciEccsMemberController.java:40` — `hasAnyRole(...) or @jciEccsSec.isSelf(authentication, #employeeId)` |

Additionally, several **self-service read** endpoints derive the caller's identity exclusively from the JWT — never from a client-supplied `employeeId` — via `SecurityUtils.currentEmployeeId(authentication)` (`backend/src/main/java/in/gov/jci/hrms/security/SecurityUtils.java:14,19-32`), and are correctly gated as a result even where the `@PreAuthorize` annotation itself only says `isAuthenticated()`:

- `EssPayrollController` (salary slips) — `employeeId` never a parameter; `EssPayrollService.loadOwnedDisbursedRecord()` additionally re-verifies `record.getEmployee().getId().equals(employeeId)` server-side.
- `CpfSelfServicePassbookController`/`CpfSelfServiceDisputeController` — same pattern; `CpfTrustPassbookService.getTransactionDetail()` checks ownership again server-side.
- `AttendanceHistoryController.myHistory()` — `employeeId` resolved only from the JWT claim.

**These are genuinely well-built precursors to the future SELF data scope and should be the template the RBAC/onboarding phase generalizes**, rather than inventing a new mechanism.

**Important caveat carried over from the authentication review**: `SecurityUtils.currentEmployeeId()` trusts the JWT's `employee_id` claim exactly as much as the token's signature can be trusted. Under the HS256-fallback finding (`PRE_RBAC_SECURITY_AUDIT.md`, SEC-001), anyone who can forge a token under the known default secret controls **both** their role claims **and** their `employee_id` claim — every `isSelf` check in this table is only as strong as SEC-001's remediation.

## 4. Object-level authorization — confirmed gaps (creation endpoints)

Unlike the read/update/approve endpoints above, four **creation** endpoints accept a client-supplied `employeeId` inside the request body, are gated only by role (not ownership), and neither the controller nor the service verifies the body's `employeeId` against the caller:

| Endpoint | Role gate | Object-ownership check | Impact | Finding ID |
|---|---|---|---|---|
| `POST /api/attendance/punch` (`MobilePunchController.java:41-59`) | `isAuthenticated()` — **no role restriction at all** | None — `MobilePunchService.create()` loads `employeeRepository.findById(request.employeeId())` directly | Any authenticated user can fabricate a time+geolocation attendance punch for **any other employee** | SEC-002 (CRITICAL) |
| `POST /api/v1/attendance/regularization` (`AttendanceRegularizationController.java:40-44`) | `hasAnyRole('EMPLOYEE','HR_ADMIN','SUPER_ADMIN')` | None — `AttendanceRegularizationService.submit()` loads by `request.employeeId()` | Any `EMPLOYEE` can submit a falsified regularization request routed to another employee's real approver | SEC-005 (HIGH) |
| `POST /api/v1/self-service/leave/encashment` (`LeaveEncashmentController.java:37-41`) | `hasAnyRole('EMPLOYEE','HR_ADMIN','SUPER_ADMIN')` | None — `LeaveEncashmentService.apply()` loads by `request.employeeId()` | Any `EMPLOYEE` can file a financial-payout encashment claim against another employee's leave balance | SEC-006 (HIGH) |
| `POST /api/leave-applications` (`LeaveApplicationController.java:33-36`) | `hasAnyRole('EMPLOYEE','HR_ADMIN','SUPER_ADMIN')` | None — `LeaveApplicationService.create()` loads by `request.employeeId()` | Any `EMPLOYEE` can create a DRAFT leave application under another employee's identity (contained — the victim's own `submit()` remains `isSelf`-gated, so the attacker cannot advance it) | SEC-007 (MEDIUM) |

**Root cause and fix pattern (same for all four):** on creation, `employeeId` must either (a) be removed from the client-writable DTO and derived server-side via `SecurityUtils.currentEmployeeId()` — the pattern already correctly used by `EssPayrollController`/`AttendanceHistoryController`/`CpfSelfServicePassbookController` — or (b) be explicitly checked against the caller unless the caller holds a role that legitimately acts on others' behalf (HR/admin creating on an employee's behalf).

## 5. No maker-checker enforcement anywhere (financial domains)

Across CPF, Payroll, and JCIECCS, the *identical* role set is authorized for both the "maker" step (apply/create/initiate) and the "checker" step (sanction/disburse/approve/reverse/reconcile/settle):

- `CpfLoanController.java:38` — one class-level `@PreAuthorize("hasAnyRole('FINANCE_ADMIN','CPF_ADMIN','SUPER_ADMIN')")` covers `apply`, `sanction`, `disburse`, `reject`, `settle-cash`. `CpfLoanApplicationService` never compares `applicantUserId` against `approvingOfficerId`/`disburseOfficerId` (verified in `applyLoan`/`sanctionLoan`/`disburseLoan`/`rejectLoan`, `CpfLoanApplicationService.java:106,140,227,315`).
- `JciEccsRecoveryService.reverseRecovery()` (`:251-293`) accepts and persists `performedByEmployeeId` but never compares it against the original recovery's own `performedByEmployeeId` — a single `COOP_ADMIN`/`FINANCE_ADMIN`/`SUPER_ADMIN` can create a recovery and immediately reverse it themselves.

This is an **architecture gap to close as part of the RBAC design** (the maker/checker distinction doesn't exist as a role concept yet — it cannot be bolted onto the current 7-role model), not a simple annotation fix. See `RBAC_SECURITY_REQUIREMENTS.md` §Maker-Checker.

## 6. No SYSTEM_ADMIN / financial-approver boundary exists

`SUPER_ADMIN` is allow-listed in the overwhelming majority (~85/260) of `@PreAuthorize` expressions across every domain — technical administration, HR administration, and every financial sanction/disbursement/posting/reversal action all currently collapse into one role. There is **no existing code path or partial separation** between "technical/application administration" and "financial approval authority" for the future `SYSTEM_ADMIN` boundary (RBAC spec §15) to build on. This must be designed from scratch.

## 7. No organizational data scope exists

No evidence anywhere in repository/service code of `SELF`/`OFFICE`/`REGION`/`HO`/`ALL_JCI`-style scoping (spec §70). Every non-`isSelf` authorization branch is a flat role check with no office/region filter — today, holding `HR_ADMIN` implies `ALL_JCI`-wide access, not an office/region-bounded view. Expected pre-RBAC; confirms scope enforcement must be built centrally in the backend service layer during RBAC, with no existing partial implementation to reconcile.

## 8. Bootstrap / magic-ID bypass check (spec §18)

Grepped for `employeeId == 8`, `== 8L`, `admin`/`admin` literal pairs, `hardcod*`, `bypass`, `BOOTSTRAP` across all of `backend/src/main/java`. **No authorization bypass found.** Every "hardcoded"/"bypass" hit is either a documented statutory business constant (e.g. EPFO rate in `PayrollComputationService.java:222`) or a defensive comment about a validation/lock backstop. **No `if (employeeId == 8) allowEverything()` or equivalent exists.** There is currently no user-to-employee-role mapping table at all (roles come only from JWT claims) — the real risk window opens once bootstrap employee-ID-8 logic is introduced during onboarding implementation; this must be re-tested at that time (see `ONBOARDING_SECURITY_REQUIREMENTS.md`).

## 9. Minor inconsistency (informational)

`MasterStateController` (`/api/v1/master/states`) is gated `isAuthenticated()` — any authenticated user — while `StateMasterController` (`/api/v1/admin/masters/states`) gates apparently equivalent state-master data at `hasRole('SUPER_ADMIN')`. Low sensitivity data, but a real inconsistency worth resolving before RBAC (two controllers, two authorization postures, for what the naming suggests is the same conceptual data).
