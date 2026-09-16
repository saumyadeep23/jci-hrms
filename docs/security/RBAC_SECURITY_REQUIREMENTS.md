# JCI HRMS — RBAC Security Requirements

**Purpose:** Translate the findings in `PRE_RBAC_SECURITY_AUDIT.md` into concrete requirements for the upcoming RBAC implementation. This document specifies *what the RBAC design must guarantee*, not how to code it — no RBAC implementation happens as part of this audit.

Reference: `docs/security/CURRENT_AUTHORIZATION_MODEL.md` documents exactly what exists today (7 roles, `isSelf` pattern, no data scope) as the starting point these requirements build on or replace.

---

## 1. Backend-authoritative permissions

Every authorization decision — role, scope, and object-ownership — **must** be enforced in the backend service/controller layer. Frontend route guards (`ProtectedRoute.tsx`) are confirmed UX-only in this codebase today and must remain purely cosmetic; they must never be the sole gate on any sensitive action. This is already the case for role checks (100% `@PreAuthorize`-driven) — RBAC must preserve that property for the new role/scope model, not introduce any client-trusted authorization signal.

## 2. Multiple roles per user

The current model assumes one flat role set per JWT's `realm_access.roles`. The future model (`SYSTEM_ADMIN`, `HR_ADMIN_PERS/BILL/EST`, `HR_MAKER_PERS/BILL/EST`, `FIN_ADMIN_CPF/DISB`, `FIN_MAKER_CPF/DISB`, `JCIECCS_ADMIN`/`JCIECCS_MAKER`, `IT_ADMIN_STORE`/`IT_MAKER_STORE`, `USER`) must support a user legitimately holding several of these simultaneously (e.g., a `SYSTEM_ADMIN` who is separately also `FIN_ADMIN_CPF`). Authorization checks must evaluate the full role set the user holds, not assume a single role — and must not let the *presence* of one role (especially `SYSTEM_ADMIN`) implicitly grant capabilities that belong to another (see §5).

## 3. Data scopes

No `SELF`/`OFFICE`/`REGION`/`HO`/`ALL_JCI` scoping exists anywhere in the current codebase (confirmed — every role check today is implicitly `ALL_JCI`-wide). This must be designed and enforced **centrally in the backend service layer**, not per-controller ad hoc and never in the frontend alone. Recommend a single, reusable scope-resolution mechanism (analogous to the existing `SecurityUtils.currentEmployeeId()` helper) that every service can call to get "what scope does this caller have for this resource type," rather than each service reinventing scope logic.

## 4. SELF isolation

**Generalize, don't replace, the existing pattern.** The codebase already has a correct, well-tested SELF-isolation idiom in two forms:
- `@PreAuthorize("hasAnyRole(...) or @xSecurity.isSelf(authentication, #id)")` (Employee sub-resources, loans, CPF applications, leave applications, APAR, JCIECCS members).
- JWT-claim-derived identity with a second server-side ownership re-check (`EssPayrollController`/`EssPayrollService`, `CpfSelfServicePassbookController`/`CpfTrustPassbookService`, `AttendanceHistoryController`).

RBAC must apply one of these two patterns to the confirmed gaps (see §11) and to any new self-service endpoint, rather than introducing a third mechanism. The JWT-claim-derived pattern is strictly stronger (it cannot be spoofed by a client-supplied body field) and should be preferred for all new self-service creation endpoints.

## 5. SYSTEM_ADMIN financial boundary

`SUPER_ADMIN` today is allow-listed in ~85 of ~260 `@PreAuthorize` expressions across every domain, including every financial sanction/disbursement/posting/reversal action — there is no existing separation to migrate cleanly; this must be designed from scratch as a clean boundary, which is the simpler starting position. Requirements:

- `SYSTEM_ADMIN` must be defined purely as a technical/application-administration role (user management, configuration, non-financial master data) and must **not** implicitly satisfy any `@PreAuthorize` check that gates a financial sanction/posting/reversal/disbursement action.
- A user who needs both technical admin and financial approval authority must hold both `SYSTEM_ADMIN` and the specific financial role (e.g., `FIN_ADMIN_CPF`) explicitly — never derive one from the other.
- Add an explicit test suite asserting a `SYSTEM_ADMIN`-only principal is rejected (403) from every CPF/Payroll/JCIECCS sanction/disburse/post/reverse endpoint.

## 6. Object-level authorization

Every endpoint identified in `CURRENT_AUTHORIZATION_MODEL.md` §4-5 as missing an ownership check (SEC-002, SEC-005, SEC-006, SEC-007) must be fixed using the pattern in §4 above, either as an immediate pre-RBAC patch (recommended for SEC-002 given its severity) or as part of the RBAC rollout. Any new object-scoped endpoint introduced during RBAC/onboarding work must include an object-level check from day one — including the future document-download endpoint (SEC-014), which currently has zero ownership tracking in its storage layer and must not be built without designing that in first.

## 7. Maker-checker

**Not currently implementable within the existing 7-role model** — this is a first-class RBAC design requirement, not a patch. Required properties:

- For every sensitive financial workflow (CPF apply→sanction→disburse→settle, Payroll batch create→edit→finalize→reversal — the latter not independently re-verified in this audit and should be spot-checked before RBAC design finalizes, JCIECCS recovery create→reverse/reconcile/settle, and any future financial-master configuration change), the actor who initiates ("maker") and the actor who approves/posts/reverses ("checker") must be enforced as different users **at the backend business-service level** — `maker_user_id != checker_user_id` — not merely through UI separation or distinct role names that the same user could hold simultaneously.
- This requires the new roles to be structured as explicit maker/checker pairs (`FIN_MAKER_CPF` vs `FIN_ADMIN_CPF`, `JCIECCS_MAKER` vs `JCIECCS_ADMIN`, `HR_MAKER_BILL` vs `HR_ADMIN_BILL`, etc.) and requires every financial-transition service method to persist and compare the acting user's identity, which today only JCIECCS does correctly (`performedByEmployeeId`) — CPF persists no actor identity at all (SEC-008) and must gain this as a prerequisite for maker-checker enforcement to even be checkable.
- Recommended test pattern: for every maker/checker pair of endpoints, assert that the same authenticated identity cannot complete both steps of the same transaction.

## 8. Onboarding authority

Only `SYSTEM_ADMIN` and `HR_ADMIN` may initiate onboarding (per the future design). See `docs/security/ONBOARDING_SECURITY_REQUIREMENTS.md` for the full translated requirement set (email-domain validation, token security, bootstrap-ID-8 handling, etc.) — not duplicated here.

## 9. Exact `jcimail.in` validation

Official email eligibility for onboarding must validate: non-null, syntactically valid email, and an **exact** domain match to `jcimail.in` (not a suffix/substring match — reject `jcimail.in.attacker.com` or `notjcimail.in`). See `ONBOARDING_SECURITY_REQUIREMENTS.md` §Official Email Eligibility for the full specification.

## 10. Token security

Applies both to the future invitation-token design (see onboarding doc) and to the existing JWT bearer-token architecture:

- Existing JWTs: SEC-001 must be closed (production fail-closed on the HS256 fallback) before RBAC's new role/scope claims can be trusted at all — a forgeable token forges everything built on top of it, including new role and scope claims.
- Existing JWT storage: `localStorage` (SEC-016) — recommend a short token TTL and, once a CSP is defined (SEC-018), evaluate whether the residual XSS-exfiltration risk is acceptable for this application's data sensitivity or whether a migration to httpOnly-cookie-based token delivery is warranted at that time (a larger architectural change, not required by this audit but worth a deliberate decision rather than default inertia).
- Future invitation tokens: cryptographically random, hashed at rest, single-use, time-limited — full spec in the onboarding document.

## 11. Last SYSTEM_ADMIN invariant

The future RBAC must enforce that **at least one ACTIVE `SYSTEM_ADMIN` always exists** — no code path (role revocation, account disable, bulk update) may leave zero active `SYSTEM_ADMIN` accounts. Current codebase has no role-assignment/revocation mechanism at all yet (no role-management endpoint exists), so there is nothing to retrofit — this invariant must be built into the role-assignment service from its first implementation, most naturally as a check-before-commit in whatever service handles role revocation/deactivation (reject the operation if it would leave zero active `SYSTEM_ADMIN`s), backed by a race-condition-safe check (the same TOCTOU class documented in SEC-009 is relevant here — a check-then-update without a lock or a DB-level constraint could allow two concurrent revocations to both "succeed" and leave zero admins).

## 12. Role assignment authorization

No role-assignment/management functionality exists yet in this codebase. When built, it must prevent:

- **Self-escalation**: a user must not be able to grant themselves a higher-privilege role than they currently hold, including via any indirect path (e.g., an onboarding-initiation flow that lets the initiator assign roles to the record they're creating).
- **Unauthorized role assignment**: only `SYSTEM_ADMIN` (or a narrowly-scoped role-management role, if the design introduces one) may assign/revoke roles — this must be its own `@PreAuthorize` check, separate from general `SYSTEM_ADMIN` administrative capability, if role-assignment authority should ever need to be delegated independently of other admin capability.
- **Scope escalation**: a user with an `OFFICE`-scoped role must not be able to assign a `REGION`- or `ALL_JCI`-scoped role to themselves or anyone else beyond their own scope.
- **Maker→checker self-promotion**: a user holding a maker role for a domain must not be able to grant themselves the corresponding checker role for the same domain (this would defeat §7 entirely) — enforce this as an explicit business rule in the role-assignment service, not just as a hoped-for consequence of separate role definitions.

## 13. Role/scope audit history

Every role grant, revocation, and scope change must be recorded with actor, timestamp, target user, old value, new value, and (where applicable) reason — following the pattern JCIECCS already does correctly for financial transactions (`performedByEmployeeId` persistence) rather than CPF's gap (SEC-008). Recommended auditable event set, per spec §78: `ROLE_ASSIGNED`, `ROLE_REVOKED`, `SCOPE_CHANGED`, `ACCOUNT_DISABLED`, plus the existing-domain events (`CPF_APPROVED`, `CPF_INTEREST_POSTED`, `CPF_INTEREST_REVERSED`, `PAYROLL_FINALIZED`, `DISBURSEMENT_AUTHORIZED`, `JCIECCS_APPROVED`, `JCIECCS_REVERSED`, `ATTENDANCE_REGULARIZED`) and the onboarding-specific events in `ONBOARDING_SECURITY_REQUIREMENTS.md`. None of this is implemented today (no `AuditorAware`/JPA auditing infrastructure exists anywhere) — this is a net-new capability to design, most naturally as a shared audit-logging service every domain calls, rather than each domain inventing its own (as CPF/JCIECCS/Payroll currently have, inconsistently).

## 14. Production authentication fail-closed

SEC-001 must be closed. Minimum required behavior: production startup (or a deploy-time gate) must refuse to proceed if `issuer-uri` is blank while a production-marker profile/property is active, or if `local-dev-secret` still equals its committed literal default. See `PRE_RBAC_SECURITY_AUDIT.md` SEC-001 and `VAPT_READINESS_REGISTER.md` for full evidence and a draft production fail-closed checklist (7 conditions, covering issuer configuration, JWT secret, active profile, DB password default, CORS origin list, actuator exposure scope, and the infra-side ACM certificate requirement).

## 15. Privilege escalation tests

For every requirement above, RBAC implementation should ship with a corresponding negative test. Minimum required coverage before RBAC is considered complete:

- 401 unauthenticated access to every new RBAC-gated endpoint.
- 403 for a role lacking the required permission, on every new endpoint.
- SELF-isolation test for every self-service creation endpoint (closing SEC-002/005/006/007 specifically).
- Scope-isolation test (a `OFFICE`-scoped user cannot read/act on another office's data) once scopes exist.
- Maker≠checker enforcement test for every financial maker/checker pair (closing SEC-003/004, and the not-yet-verified Payroll equivalent).
- `SYSTEM_ADMIN`-does-not-imply-financial-authority test (closing SEC-010).
- Role-assignment authorization tests: no self-escalation, no unauthorized assignment, no scope escalation, no maker→checker self-promotion.
- Last-SYSTEM_ADMIN-invariant test, including a concurrent-revocation race test.
- JWT validation tests: expired token rejected, wrong-audience/issuer rejected, malformed token rejected, and (post-SEC-001-fix) a token signed with the old default HS256 secret rejected once a real issuer is configured.

The existing baseline of 47 backend test files already exercising `@WithMockUser`/401/403 scenarios is a reasonable foundation to extend, not a starting point of zero.
