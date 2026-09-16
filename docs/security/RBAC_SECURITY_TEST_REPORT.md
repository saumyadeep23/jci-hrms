# RBAC Security Test Report

All tests below are new or updated this phase, in addition to the pre-existing suite (baseline 1506 tests, 0 failures). All are Mockito-unit or real-local-Postgres-integration tests following this codebase's existing conventions — no test sends real email, no test uses production/real employee data, bootstrap tests never touch a real database (pure Mockito).

## New test files

| File | Tests | Covers |
|---|---|---|
| `security/JwtAuthModeTest`, `AuthenticationModeGuardTest`, `SecurityConfigJwtDecoderTest`, `JwtAuthenticationSecurityTest`, `security/AttendanceAggregationSecurityTest` | (prior SEC-001/002 phase, unchanged, still passing) | SEC-001/002 regression |
| `security/RbacSecurityTest` | 8 | `hasPermission`/`hasPermissionInScope` across SELF/OFFICE/HO/ALL_JCI/REGION(always-deny); anonymous caller denied |
| `security/onboarding/OfficialEmailValidatorTest` | 9 | Full jcimail.in matrix: valid, missing, malformed, gmail, jci.gov.in, subdomain, lookalike, evil-suffix, case/whitespace normalization |
| `security/onboarding/InvitationTokenServiceTest` | 5 | ≥256-bit entropy, uniqueness, deterministic hash, hash ≠ raw token |
| `security/onboarding/UserOnboardingServiceTest` | 11 | preview classification (eligible/missing-email/wrong-domain/already-active); invite happy-path + ineligible-rejected; resend invalidates previous; activate success/expired/invalid/confirmation-failure-does-not-roll-back |
| `security/onboarding/UserRoleAssignmentServiceTest` | 7 | assign idempotent; self-escalation denied (assign+revoke); last-SYSTEM_ADMIN blocked; second-admin revoke allowed; non-admin-role revoke skips the invariant check entirely |
| `security/onboarding/SystemAdminBootstrapServiceTest` | 7 | happy path (both assignments created); idempotent re-run (no duplicates); preserves unrelated assignments; missing employee → no exception, no account; invalid/missing official email → no invented username; bootstrap grants zero financial roles |
| `audit/AuditActorTest` | 5 (rewritten) | Real authenticated principal used; anonymous → null; **spoofed `X-Acting-User` header is ignored even when present alongside real auth** |

**Total new/rewritten: 52 tests**, all passing in isolation (see per-file run confirmations during development).

## Updated existing test files (signature changes from SEC-002/005/006/007/003/004 fixes)

`MobilePunchControllerTest`/`ServiceTest`, `AttendanceRegularizationServiceTest` (+2 ownership tests), `LeaveEncashmentServiceTest` (+1), `LeaveApplicationServiceTest`/`ControllerTest` (+2 each), `CpfLoanApplicationServiceTest` (+6: 4 maker-checker + 2 actor-persistence), `JciEccsRecoveryServiceTest`/`JciEccsFinancialInvariantSuiteTest`/`JciEccsReconciliationServiceTest`/`JciEccsConcurrencyHardeningTest` (actor-id adjustments only, no new test methods — see `MAKER_CHECKER_IMPLEMENTATION.md`).

## Security test matrix coverage (spec §83) vs. what exists

| Required scenario | Covered by |
|---|---|
| Unauthenticated → 401 | Pre-existing `SecurityConfigTest`, `MobilePunchControllerTest`, `LeaveApplicationControllerTest` (unchanged, still passing) |
| USER SELF → own resource allowed | `RbacSecurityTest` (SELF scope), `MobilePunchControllerTest`/`LeaveApplicationControllerTest` self-punch/self-create tests |
| USER → another employee denied | `MobilePunchControllerTest`, `LeaveApplicationControllerTest`, `AttendanceRegularizationServiceTest`, `LeaveEncashmentServiceTest` — all with `verifyNoInteractions`/`AccessDeniedException` proof |
| OFFICE scope allowed/denied | `RbacSecurityTest.hasPermissionInScope_office_*` |
| HO scope boundary | `RbacSecurityTest.hasPermissionInScope_ho_*` |
| ALL_JCI | `RbacSecurityTest.hasPermissionInScope_allJci_alwaysCovers` |
| REGION (denies — no backing data) | `RbacSecurityTest.hasPermissionInScope_region_alwaysDenies` |
| SYSTEM_ADMIN → CPF/Payroll/JCIECCS denied without the specific financial role | Structurally verified via the V94 seed (`SYSTEM_ADMIN` grants none of `CPF_*`/`PAYROLL_*`/`JCIECCS_*`) — **not independently re-asserted via a dedicated `rbac.hasPermission` test per financial permission code**; `RbacSecurityTest` proves the mechanism generically (arbitrary permission code, no grant → false), which is the same code path every financial permission would hit. Flagged as a gap: a dedicated test enumerating every `CPF_*`/`PAYROLL_*`/`JCIECCS_*` code against a `SYSTEM_ADMIN`-only assignment was not added this phase. |
| FIN_MAKER_CPF prepare allowed / checker denied | Not directly tested via `RbacSecurity` (no controller endpoint gates CPF actions via the new permission model yet — see `RBAC_MIGRATION_REPORT.md`); the **maker≠checker business rule itself** (which is the actual security-relevant invariant) is directly tested in `CpfLoanApplicationServiceTest` |
| Same user, CPF maker+admin roles → cannot approve own | Directly tested: `sanctionLoan_bySameOfficerWhoApplied_throwsAccessDenied`, `disburseLoan_bySameOfficerWhoApplied_throwsAccessDenied` |
| JCIECCS equivalent | `JciEccsRecoveryServiceTest` (adjusted), same-actor-reversal now blocked (structurally, via the code path; no standalone new positive test asserting the denial was added to that specific file beyond the regression proving legitimate different-actor reversal still works) |
| Payroll/Disbursement equivalent | **Not implemented this phase** — see `MAKER_CHECKER_IMPLEMENTATION.md` |
| Last SYSTEM_ADMIN invariant, incl. concurrency | `UserRoleAssignmentServiceTest` covers the single-actor case with a mocked repository; a genuine concurrent-DB-transaction test (two real threads racing the row-locked count) was **not** added this phase — the row-locking SQL pattern mirrors JCIECCS's own proven-safe idiom, but is not independently re-proven under real concurrency here |
| Onboarding: 24h boundary, replay, revoked, resend-invalidates, concurrent activation | Hash/expiry logic unit-tested (`UserOnboardingServiceTest`); the `@Version`-based concurrent-activation race is **not** exercised by a real multi-threaded integration test this phase (mechanism reused from Hibernate's standard optimistic locking, not independently stress-tested here) |
| Bootstrap matrix | Fully covered — `SystemAdminBootstrapServiceTest`, 7 tests |

## Honest gaps (not silently omitted)

- No dedicated per-financial-permission `SYSTEM_ADMIN`-denied test (structural proof exists; per-code enumeration doesn't).
- No real-multi-thread concurrency test for the last-SYSTEM_ADMIN invariant or onboarding activation race (both rely on proven, reused locking/versioning mechanisms rather than freshly-verified-under-load ones).
- Payroll/Disbursement maker-checker: no tests, because no implementation this phase.
- Full 18-role × 5-scope × every-permission cross-product was not exhaustively tested (52 targeted tests were written covering the security-critical paths, not a combinatorial matrix).
