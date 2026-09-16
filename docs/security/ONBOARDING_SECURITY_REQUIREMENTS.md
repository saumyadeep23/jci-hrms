# JCI HRMS — Onboarding Security Requirements

**Purpose:** Translate the planned employee-onboarding design (per the audit task's own specification) into concrete security requirements, informed by this audit's findings. **No onboarding functionality is implemented as part of this audit.** Current codebase state: only a draft-capture mechanism exists (`EmployeeOnboardingDraftRepository`, `OnboardingDraftUpsertRequest` — deliberately unvalidated as a draft envelope, per its own javadoc, with full validation deferred to a future `finalize()` step that does not yet exist). No invitation, activation, or token model exists anywhere in the codebase today.

---

## 1. Employee-user uniqueness

The design assumes "employee must already exist" before onboarding can be initiated — onboarding creates a *user/account* for an existing employee record, not a new employee. The implementation must enforce a hard uniqueness constraint (DB-level, not just application-level) preventing more than one active user account per employee, to prevent a race condition where two concurrent onboarding-initiation requests for the same employee both succeed (the same TOCTOU class as SEC-009 in the main audit — a check-then-insert without a DB constraint backstop is not sufficient on its own; use a unique constraint as the final integrity guarantee, exactly as `uq_payroll_batch_month_year` already does for payroll batches).

## 2. Official-email eligibility

Per the design: official email must be (a) non-null, (b) syntactically valid, (c) an **exact** match to domain `jcimail.in`.

- "Exact domain match" means the email's domain component, after parsing, must equal `jcimail.in` character-for-character — not `endsWith("jcimail.in")` (which would wrongly accept `notjcimail.in` or `evil-jcimail.in`) and not a substring/contains check (which would wrongly accept `user@jcimail.in.attacker.com`). Implement via proper email parsing (extract the domain after the final unescaped `@`) followed by an exact string comparison, not a regex that could be bypassed by email-address edge cases (quoted local parts, IP-literal domains, etc.).
- Reject on any ambiguity rather than attempting a "best guess" normalization — a rejected onboarding attempt is cheap to retry with a corrected email; a wrongly-accepted one creates a real account under a domain JCI doesn't control.
- This check must run server-side, at the point of onboarding initiation, using the officially-stored `officialEmail` field on the existing `Employee` record — never a value newly supplied by the initiator in the onboarding request itself (which would let an `HR_ADMIN` onboard an employee against an arbitrary email of the HR_ADMIN's choosing rather than the employee's actual official address).

## 3. Username normalization

Username = normalized official email. "Normalized" must be specified precisely before implementation (e.g., lowercase the domain per RFC email case-insensitivity conventions, decide whether the local part is also lowercased — Indian government email systems commonly are case-insensitive end-to-end, but this should be an explicit decision, not an accident of whatever the DB collation happens to do) and enforced with a DB-level unique constraint on the normalized value, not just the raw stored value, to prevent two case-variant emails from colliding at the application layer while passing a case-sensitive DB uniqueness check (or vice versa).

## 4. Secure invitation token generation

- Generate the raw token using a cryptographically secure random source (`java.security.SecureRandom`, not `java.util.Random` or any deterministic/timestamp-derived value) with sufficient entropy (recommend at least 128 bits / 32 bytes before encoding) to make offline guessing infeasible even against a large batch of simultaneously-issued tokens.
- **The raw token must never be persisted.** Store only a salted hash of it (e.g., HMAC-SHA256 or a proper password-hashing function if the token is short enough to be brute-forced offline given a database compromise — for a 128+ bit random token, a fast hash like SHA-256 is acceptable specifically *because* the input space is large, unlike a human-chosen password; a salt is still good practice for defense-in-depth against rainbow-table-style precomputation).
- The raw token is transmitted to the employee exactly once, via the activation link sent to their official email — never logged, never returned in any API response after issuance, never included in any admin-facing "resend" confirmation screen that echoes the token back.

## 5. 24-hour validity

Enforce token expiry server-side on every validation attempt (not just at issuance-time UI hinting) — compare the token's issuance timestamp against the current server time at the moment of activation, reject with a generic "invalid or expired" message that does not distinguish "expired" from "wrong token" (avoid a timing/response-difference oracle that would let an attacker distinguish a guessed-wrong token from an expired-but-otherwise-valid one, though this is a secondary concern given the entropy requirement in §4 already makes guessing infeasible).

## 6. Single use

The token (or its hash) must be invalidated atomically with successful activation — use a single database transaction that both marks the token consumed and creates/activates the user account, so a replay of the same activation request after a successful first use cannot succeed, and so a race between two concurrent activation attempts with the same token cannot both succeed (see §9, concurrency).

## 7. Resend invalidates old token

When an initiator resends an invitation, the previous token (all previous tokens, if resend has happened more than once) must be invalidated immediately — not just superseded by a new one that happens to be checked first. Implement as an explicit invalidation (delete or mark-expired the old token row/hash) in the same transaction that creates the new one, so a leaked old token cannot be used even if the new one hasn't been delivered/used yet.

## 8. Successful activation confirmation

Per the design: send a confirmation to the official email of the **initiating officer** (not the newly-activated employee) upon successful activation. This gives the initiator a tamper-evident signal if an activation they didn't expect occurs, and should include enough context (employee name/ID, activation timestamp) for the initiator to notice an anomaly, without including the token or any other secret.

## 9. Concurrency

Two categories of race condition to design against explicitly:

- **Duplicate onboarding initiation** for the same employee (§1) — DB unique constraint as the backstop, with a controlled conflict response at the service layer (map the constraint violation to a clean business exception, following the recommendation already made for SEC-009's payroll-batch TOCTOU — don't repeat that gap here in a brand-new feature).
- **Concurrent activation attempts** with the same token (§6) — single atomic transaction covering both token-consumption and account-creation/activation, ideally with a row-level lock or the token-consumption update itself acting as the concurrency gate (an `UPDATE ... WHERE token_hash = ? AND consumed_at IS NULL` that only one concurrent request can successfully affect, checked via affected-row-count, is a proven pattern for this).

## 10. Rate limiting

The main audit (SEC-015) found no application-layer rate limiting anywhere in the current codebase. For onboarding specifically, apply rate limiting to:

- **Activation attempts** per token (or per source IP) — even though the token's entropy (§4) should make guessing infeasible, rate limiting is defense-in-depth against a future weakening of that entropy and against simple denial-of-service via repeated activation attempts.
- **Resend requests** per employee/initiator — prevent an abusive resend loop from spamming an employee's official inbox or from being used as a token-invalidation-based denial-of-service against a legitimate pending activation.
- **Bulk onboarding initiation** — if the design ever supports initiating onboarding for many employees in one operation, bound the batch size and/or rate-limit the operation itself, to prevent a compromised `HR_ADMIN`/`SYSTEM_ADMIN` account (or a confused/scripted client) from mass-issuing invitations.

## 11. Audit

Every onboarding lifecycle event must be recorded with actor, timestamp, and relevant entity references, following the same audit-trail requirement as `RBAC_SECURITY_REQUIREMENTS.md` §13. Minimum event set (extending spec §78's list): `USER_INVITED` (initiator, target employee, timestamp), `INVITATION_RESENT` (initiator, target employee, old-token-invalidated timestamp), `USER_ACTIVATED` (target employee, activation timestamp — do not log the token or its hash), and any activation-attempt failures worth tracking for abuse detection (expired-token attempts, invalid-token attempts) without logging the attempted token value itself.

## 12. Initiator notification / failure handling

Beyond the success-confirmation in §8, define and implement sanitized failure paths: if activation fails (expired/invalid/already-used token), the employee sees a generic message (no internal detail, consistent with the main audit's finding that `GlobalExceptionHandler` already follows this sanitized-response pattern for other domains — extend the same convention here) and, if the design calls for it, the initiator can be notified of repeated failed activation attempts as a signal worth reviewing (optional, but worth an explicit decision rather than silent failure).

## 13. No password emailing

The design (activation via invitation link, not a mailed password) already avoids this class of risk by construction — confirm the implementation never generates or emails a password at any point in the flow. If a password-setting step exists as part of activation (the design doesn't fully specify whether activation includes setting a password, or is purely IdP-federated), that step must happen only after the token is validated, over the activation flow itself, never via a separate emailed credential.

## 14. No personal-email fallback

The exact-`jcimail.in` requirement (§2) must have no fallback path — no "use personal email if official email is missing/invalid" logic anywhere in the initiation flow. If an employee's official email is missing or doesn't validate, onboarding for that employee must fail cleanly (with a clear reason for the initiator: missing email vs. wrong domain) rather than silently degrading to a less-trustworthy channel.

## 15. No Employee ID 8 bypass

The design specifies that employee ID 8 receives bootstrap roles (`USER`/`SELF` and `SYSTEM_ADMIN`/`ALL_JCI`) as a one-time initial-seed mapping. This audit's bootstrap-bypass check (spec §18, documented in `CURRENT_AUTHORIZATION_MODEL.md` §8) found **no** existing `employeeId == 8`-style logic anywhere in the current codebase — this is a clean starting point, not a gap to fix, but it means the requirement below applies to *new* code that doesn't exist yet:

- The bootstrap mapping for employee ID 8 must be a one-time data-seed operation (e.g., a Flyway migration or an explicit, audited, single-run administrative action) — **never** a runtime `if (employeeId == 8) { grantSystemAdmin(); }`-style conditional anywhere in request-handling code. A runtime check like that would be a permanent, standing bypass reachable by anyone who can get (or forge, per SEC-001) a token with `employee_id: 8`, rather than a one-time bootstrap.
- Once seeded, employee ID 8's `SYSTEM_ADMIN` role must be revocable and reassignable through the same role-management authorization rules as any other user's roles (subject to the last-SYSTEM_ADMIN invariant in `RBAC_SECURITY_REQUIREMENTS.md` §11) — it must not be hardcoded as permanent or exempt from normal role-management authorization once the system is running.
- Re-run this bootstrap-bypass grep-and-review check specifically after onboarding implementation lands, since that is exactly the point at which this class of bug would most plausibly be introduced (a developer implementing "the ID-8 special case" as a runtime shortcut rather than a seed operation).
