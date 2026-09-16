# Onboarding Implementation

Implements `ONBOARDING_SECURITY_REQUIREMENTS.md`. Core classes: `OfficialEmailValidator`, `InvitationTokenService`, `UserOnboardingService`, `SystemAdminBootstrapService`/`Runner` (all `in.gov.jci.hrms.security.onboarding`).

## Authority

`AdminOnboardingController` (`/api/admin/onboarding/**`) gates every operation with `@rbac.hasPermission(authentication, 'USER_INVITE'|'USER_INVITE_RESEND'|'USER_INVITE_REVOKE')`. The V94 seed grants these ONLY to `SYSTEM_ADMIN` and `HR_ADMIN` — no other role, per instruction 20.

## Eligibility (`CandidateStatus`)

`UserOnboardingService.previewCandidate`/`previewBulk` return one of: `ELIGIBLE`, `OFFICIAL_EMAIL_MISSING`, `INVALID_OFFICIAL_EMAIL`, `INVALID_OFFICIAL_EMAIL_DOMAIN`, `ALREADY_PROVISIONED`, `ALREADY_ACTIVE`, `INVITATION_PENDING`, `EMPLOYEE_INELIGIBLE`. Deterministic, no side effects — safe for preview/bulk-preview.

## Exact `jcimail.in` validation

`OfficialEmailValidator.validate()` — non-null/non-blank, syntactically valid (simple `local@domain.tld` shape), then an **exact** (not suffix, not substring, not contains) comparison of the parsed domain against `jcimail.in`. Verified against the full negative matrix: `gmail.com`, `jci.gov.in`, `subdomain.jcimail.in`, `fakejcimail.in`, `jcimail.in.evil.com` — all correctly `WRONG_DOMAIN`, none accepted. See `OfficialEmailValidatorTest`.

## Username

`OfficialEmailValidator.normalize()` = trim + lowercase the full address (both local part and domain). Enforced unique at the DB level (`application_users.username UNIQUE`, V94) and pre-checked in `invite()` (`existsByUsername`) — a real conflict is reported as a `BusinessRuleViolationException`, never silently renamed.

## Invitation token security

`InvitationTokenService`: `SecureRandom`-generated 256-bit raw token (Base64url), SHA-256 hash. **Only the hash is ever persisted** (`user_invitations.token_hash`) — the raw token exists only transiently inside `issueInvitation()`, used once to build the activation email link, then discarded; never logged, never returned from any API, never stored anywhere else.

Expiry: `hrms.onboarding.invitation-validity-hours` (default 24, `application.yml`), compared server-side (`UserInvitation.isExpired(Instant.now())`) on every activation attempt.

Single-use + concurrency: `UserInvitation` carries `@Version` (Hibernate optimistic locking). Two concurrent `activate()` calls on the same invitation, or an `activate()` racing a `resend()`, cause one side to hit `ObjectOptimisticLockingFailureException` — already mapped to 409 by the existing `GlobalExceptionHandler`, reused rather than building new concurrency machinery.

Resend: `resend(invitationId, initiatorUserId)` revokes every currently-`INVITED` invitation for that user (`UserInvitation.revoke()`) in the same transaction that issues the new one — the old token becomes unusable immediately, not eventually.

## Activation

`PublicActivationController` (`POST /api/public/activation`), explicitly `permitAll` in `SecurityConfig` (the only change made to that class this phase, beyond the SEC-001 work from the prior phase). Response is exactly `SUCCESS`/`EXPIRED`/`INVALID` — never distinguishes "wrong token" from "token for an employee that doesn't exist," never echoes the token back, never leaks an exception.

On success: `ApplicationUser.status → ACTIVE`, baseline `USER`/`SELF` role ensured (idempotent — checks for an existing active assignment first), and a best-effort confirmation email is sent to the **initiator's** official (`jcimail.in`-validated) email — resolved from `UserInvitation.initiatedByUserId → ApplicationUser → Employee.officialEmail`. If that email fails or is unresolvable/invalid, activation **still succeeds** (per §33 of the requirements) — the failure is caught and audit-logged (`ACTIVATION_CONFIRMATION_FAILED`), never rolled back, and there is no personal-email fallback.

## Email transport

`EmailService` interface + `LoggingEmailService` (logs at INFO, never sends) — no SMTP credentials exist anywhere in this codebase; swap the bean for a real provider when one is provisioned. All onboarding tests use this same logging stub — **no test ever sends real email**, satisfying instruction 98 by construction (there is no other `EmailService` implementation to accidentally invoke).

## Bulk onboarding

`AdminOnboardingController.previewBulk`/`bulkInvite` — same permission gate, same per-record eligibility classification; `bulkInvite` catches `BusinessRuleViolationException` per-record and reports the failed record's status instead of aborting the whole batch, so ineligible records are skipped deterministically rather than failing the batch.

## Not implemented this phase

- Rate limiting on activation/resend (no application-layer rate limiting exists anywhere in this codebase — see original audit SEC-015; not added here, matches the audit's own "do not build a complex distributed rate limiter without need" guidance and remains flagged for the formal-VAPT-readiness phase).
- Frontend activation page / onboarding UI.
- Referer/analytics-leakage hardening for the activation link beyond keeping it out of logs (no proxy/CDN layer exists in this repo to configure).
