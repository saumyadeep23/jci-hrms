# RBAC Implementation

Implements the DB-backed authorization foundation from `RBAC_SECURITY_REQUIREMENTS.md`. Scope and honest limitations are stated explicitly — see "What this phase does NOT do" at the end.

## Schema (Flyway V94-V96)

- `V94__rbac_core_schema.sql` — `permissions`, `roles`, `role_permissions`, `application_users` (unique `employee_id`, unique `username`), `user_role_assignments` (append-only; `revoked_at IS NULL` = active; partial unique index `uq_user_role_assignments_active` prevents duplicate active grants). Seeds the 18 canonical roles and ~40 permission codes, with `role_permissions` grants for each.
- `V95__user_invitations.sql` — `user_invitations` (token hash only, `@Version` optimistic lock for concurrency safety).
- `V96__cpf_loan_actor_attribution.sql` — 4 nullable `BIGINT` actor columns on `cpf_loan_applications` (no FK, matching the existing `jcieccs_recovery.created_by` convention).

All additive; no historical migration touched.

## Entities / Repositories

`ApplicationUser`, `UserRoleAssignment`, `UserInvitation` (package `in.gov.jci.hrms.entity`) + matching repositories. `ScopeType` enum: `SELF`, `OFFICE`, `REGION`, `HO`, `ALL_JCI`. `UserAccountStatus`: `PENDING_INVITATION`, `INVITED`, `ACTIVE`, `LOCKED`, `DISABLED`, `EXPIRED`, `SEPARATED`.

## Authenticated-user resolution + permission/scope evaluation

`RbacSecurity` (`@Component("rbac")`, `in.gov.jci.hrms.security`) is the single point that:
- Resolves JWT → `ApplicationUser` id (`resolveCurrentUserId`) — the Phase D "authoritative resolver."
- Evaluates `hasPermission(authentication, code)` / `hasPermissionInScope(authentication, code, targetEmployeeId)` — the Phase E centralized evaluator, generalizing the existing `isSelf` pattern rather than replacing it.

Deliberately does **not** trust JWT role claims for authorization decisions (`JwtRoleConverter` is unchanged and untouched) — every permission check resolves through `application_users`/`user_role_assignments` in the database. This directly satisfies RBAC_SECURITY_REQUIREMENTS.md's "OIDC claim trust" requirement: identity comes from the token, authority comes from the app DB.

### Scope resolution (inspected before implementing — see `RbacSecurity` javadoc)

`Employee.regionalOffice` (a `RegionalOffice`, typed `HEAD_OFFICE`/`REGIONAL_OFFICE`/`WAREHOUSE` via `OfficeType`) is the **only** organizational-unit tier in this schema — there is no separate "Region" entity grouping multiple offices.

- `SELF` — caller's own employeeId only.
- `OFFICE` — `scope_value` is a `RegionalOffice.id`; covers employees whose `regionalOffice.id` matches.
- `HO` — covers employees whose own `regionalOffice.officeType == HEAD_OFFICE`.
- `ALL_JCI` — unrestricted.
- `REGION` — **modeled but not resolvable**: no Region grouping exists in the org model, and inventing one would violate the explicit "do not invent parallel organizational structures" instruction. A `REGION`-scoped assignment is assignable (for forward compatibility) but `RbacSecurity.hasPermissionInScope` **always denies** it — fail-closed, not fail-open, consistent with SEC-001's precedent. **REQUIRES_BUSINESS_CONFIRMATION**: define the Region concept before this scope type is operationally useful.

## Role/scope assignment

`UserRoleAssignmentService` (`assign`/`revoke`):
- **Self-escalation/self-revocation blocked outright**: `actingUserId == targetUserId` → `AccessDeniedException`, for both assign and revoke, on any role. No user can grant or remove their own roles through this service.
- **Idempotent assign**: an identical active (user, role, scope) grant is returned as-is, not duplicated; `uq_user_role_assignments_active` is the concurrency-safe DB backstop.
- **Last-SYSTEM_ADMIN invariant**: revoking a `SYSTEM_ADMIN` assignment runs `countActiveByRoleCodeForUpdate('SYSTEM_ADMIN')` — a `SELECT COUNT(*) FROM (... FOR UPDATE) locked` row-lock pattern matching the codebase's existing JCIECCS Phase 5 concurrency idiom — and rejects if the count is `<= 1`, genuinely serializing concurrent last-admin-removal attempts, not just a check-then-update race.
- Every assign/revoke is recorded via the existing `AuditLogRecorder` (reused, not a new audit table).

Endpoints: `POST/DELETE /api/admin/users/roles` (`AdminUserRoleController`), gated `@rbac.hasPermission(authentication, 'USER_ROLE_ASSIGN')` (`SYSTEM_ADMIN` only per the V94 seed).

## SYSTEM_ADMIN financial boundary (SEC-010)

`SYSTEM_ADMIN`'s `role_permissions` seed grants only `USER_*`, `AUDIT_VIEW`, `SECURITY_DIAGNOSTICS_VIEW`, `EMPLOYEE_VIEW` — zero `CPF_*`/`PAYROLL_*`/`DISBURSEMENT_*`/`JCIECCS_*` permissions. Verified directly by `RbacSecurityTest`'s permission-resolution tests (a `SYSTEM_ADMIN`-only assignment does not satisfy `hasPermission(..., "CPF_SANCTION")`). This is **structural** — the boundary exists because of what the seed data grants, not an application-code special case.

## Audit trail (SEC-008 / instruction 43)

Reused, not rebuilt: this codebase already has a generic `AuditLog`/`Auditable`/`AuditableEntityListener`/`AuditLogRecorder` system (JPA entity listener + `X-Acting-User`-based actor resolution) that the original Pre-RBAC audit pass did not identify. Two changes:
1. **Fixed `AuditActor.currentUsername()`** — it trusted a client-spoofable `X-Acting-User` HTTP header (a pre-auth-era placeholder). Now resolves the real `SecurityContextHolder` principal, falling back to `null` only when there genuinely is none (e.g. a background/bootstrap context). No behavior change for any currently-audited entity beyond making the actor field trustworthy.
2. New RBAC/onboarding events (`USER_INVITED`, `ROLE_ASSIGNED`, `USER_ACTIVATED`, etc.) are recorded via the same `AuditLogRecorder.record(entityName, entityId, action, before, after)` call used elsewhere — no parallel audit table.

## What this phase does NOT do (explicit, matching the phased scope in RBAC_SECURITY_REQUIREMENTS.md)

- Does **not** migrate the ~96 legacy `@PreAuthorize("hasAnyRole(...)")` controllers to the new permission model — see `RBAC_MIGRATION_REPORT.md`. Only the endpoints this phase specifically touches (onboarding, role assignment, the SEC-002/005/006/007 fixes) use `@rbac.hasPermission`/`@attendanceAggSec` checks; everything else is unchanged and still legacy-role-gated.
- Does **not** implement Payroll/Disbursement maker-checker (deferred — see `MAKER_CHECKER_IMPLEMENTATION.md`).
- Does **not** implement frontend RBAC UI (admin user management screens, onboarding UI, activation page) — backend only this phase.
- Does **not** resolve `REGION` scope (no organizational data to resolve it against).
