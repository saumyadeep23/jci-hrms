# SEC-001 / SEC-002 Security Blocker Remediation

**Scope:** This remediation closes exactly two findings from `docs/security/PRE_RBAC_SECURITY_AUDIT.md` — SEC-001 (JWT authentication fail-closed) and SEC-002 (attendance-punch object-level authorization) — plus the object-level authorization bug SEC-002's fix necessarily touches in `MobilePunchService`. No other VAPT finding, RBAC work, maker-checker, onboarding, or Attendance Calculation Engine work was performed. Baseline commit before this remediation: `6067227b40c679d9be83ad1f709e818d93405b71`.

---

## SEC-001 — JWT authentication fail-closed

### Root cause (re-verified before modifying anything)

`backend/src/main/java/in/gov/jci/hrms/security/SecurityConfig.java` is a standard Spring OAuth2 resource-server configuration (`spring-boot-starter-oauth2-resource-server`, stateless bearer-token JWT auth, no sessions). Its `jwtDecoder()` bean previously chose its decoder purely on whether `spring.security.oauth2.resourceserver.jwt.issuer-uri` was blank: blank → build a symmetric HS256 `NimbusJwtDecoder` keyed by `security.jwt.local-dev-secret`, whose published default value is `[REDACTED — the literal string committed in application.yml, unchanged by this remediation, never reproduced here]`. `backend/src/main/resources/application.yml` set both `issuer-uri` and the local-dev secret's default via env-var placeholders, and `infra/main.tf`'s ECS task definition set neither `JWT_ISSUER_URI` nor `JWT_LOCAL_DEV_SECRET` nor any `SPRING_PROFILES_ACTIVE` marker anywhere. `JwtRoleConverter.java` maps a validated JWT's `realm_access.roles`/`resource_access.*.roles` claims straight to `ROLE_*` authorities with no additional allow-list — confirmed unchanged and confirmed that role conversion only ever runs on a token that has already passed the configured decoder's signature/expiry/issuer checks (Spring's `JwtAuthenticationProvider` always authenticates before `JwtAuthenticationConverter` runs), so SEC-001's fix did not need to touch this class at all (per remediation task instruction 8).

**Old attack path:** deploy the app as-is (e.g. via this repo's own Terraform) → no issuer configured anywhere → `jwtDecoder()` silently builds the HS256 decoder → anyone who reads the public source (the default secret is a literal string in a GitHub-hosted file) can forge a JWT with `realm_access.roles: ["SUPER_ADMIN"]` and any `employee_id` claim → full authentication and authorization bypass, including every SELF-scope check documented in `CURRENT_AUTHORIZATION_MODEL.md` (those checks trust the same forgeable `employee_id` claim).

### New security control

**Explicit, positive authentication-mode selection**, gated by a fail-closed startup guard — no more "issuer blank → infer local-dev" logic anywhere.

1. **New property `security.auth.mode`** (`application.yml`, default `${SECURITY_AUTH_MODE:local-dev}`) — must be exactly `oidc` or `local-dev` (case/hyphen-tolerant parsing). This is the positive switch the remediation brief required in place of inferring behavior from `issuer-uri`'s absence.
2. **New class `JwtAuthMode`** (`backend/src/main/java/in/gov/jci/hrms/security/JwtAuthMode.java`) — a two-value enum (`OIDC`, `LOCAL_DEV`) with a tolerant parser that returns `null` (never guesses) for anything unrecognized or blank.
3. **New class `AuthenticationModeGuard`** (`backend/src/main/java/in/gov/jci/hrms/security/AuthenticationModeGuard.java`) — side-effect-free validation logic, kept separate from `SecurityConfig` specifically so it's unit-testable without a Spring context, embedded datasource, or network access (this codebase's own established constraint — see `SecurityConfigTest`'s class javadoc for why a full `@SpringBootTest` isn't used here). It defines:
   - `PRODUCTION_LIKE_PROFILES = {"staging", "prod", "production"}` — deliberately reusing Terraform's existing `var.environment` vocabulary (`infra/variables.tf`), so the exact same value already gating RDS Multi-AZ/KMS/WAF hardening also gates authentication mode.
   - `isProductionLike(String[] activeProfiles)` — true if any active Spring profile matches the set above.
   - `validate(JwtAuthMode mode, boolean productionLike, String issuerUri, String localDevSecret)` — throws `IllegalStateException` (never including the secret's value) for every unsafe combination; returns normally only when safe.
4. **`SecurityConfig.jwtDecoder()`** now: parses the mode, computes `productionLike` from `Environment.getActiveProfiles()` (constructor now takes `org.springframework.core.env.Environment`, a framework-provided bean — no new wiring needed), calls `AuthenticationModeGuard.validate(...)` **before** building any decoder, then proceeds exactly as before (`JwtDecoders.fromIssuerLocation(issuerUri)` for OIDC, the existing HS256 `NimbusJwtDecoder` for local-dev).

`IllegalStateException` thrown from inside a `@Bean` method is a standard Spring mechanism for aborting `ApplicationContext` refresh — i.e. it fails application startup, which is exactly section 6/13's "fail closed" requirement. This codebase's own established testing convention avoids `@SpringBootTest` (no embedded/test datasource is configured anywhere — see `EmployeeRepositoryTest`, the one test that needs a live Postgres and is excluded from the standard run, and `SecurityConfigTest`'s own class javadoc explaining the same constraint), so "proves startup fails" is demonstrated by proving the guard throws before any decoder is returned — see Tests below.

### Environment behaviour

| `SPRING_PROFILES_ACTIVE` | `security.auth.mode` | `issuer-uri` | `local-dev-secret` | Result |
|---|---|---|---|---|
| none / `dev` / `test` | `local-dev` (default) | — | anything | **Starts** — unchanged local dev/CI/test experience, zero setup |
| none / `dev` / `test` | `oidc` | set | anything | **Starts**, real OIDC decoder built (lets a developer test against a real IdP without a production-like profile) |
| none / `dev` / `test` | `oidc` | blank | anything | **Fails to start** — oidc mode always requires an issuer, in any environment |
| `staging` / `prod` | `local-dev` (or unset/default) | — | anything | **Fails to start** — local-dev auth is never permitted on a production-like profile |
| `staging` / `prod` | `oidc` | blank | anything | **Fails to start** — missing OIDC configuration |
| `staging` / `prod` | `oidc` | set | still the published default | **Fails to start** — defense in depth: catches a rotated-mode-but-not-rotated-secret misconfiguration |
| `staging` / `prod` | `oidc` | set | rotated | **Starts** — the only combination a production-like deploy can reach |
| any | unrecognized/blank mode string | — | — | **Fails to start** everywhere, not just in production — never silently defaults |

### Infrastructure/configuration changes (no `terraform apply` run; `terraform fmt`/`validate` used, both clean)

- `infra/variables.tf`: new `variable "jwt_issuer_uri"` (default `""`, documented as required for staging/prod).
- `infra/main.tf`: new local `jwt_auth_mode = local.hardened ? "oidc" : "local-dev"` (reuses the existing `local.hardened` gate that already drives RDS/KMS/WAF); ECS task definition's `environment` list gains `SPRING_PROFILES_ACTIVE = var.environment`, `JWT_AUTH_MODE = local.jwt_auth_mode`, `JWT_ISSUER_URI = var.jwt_issuer_uri`. For `dev` (the only environment currently deployed) this resolves to `SPRING_PROFILES_ACTIVE=dev`, `JWT_AUTH_MODE=local-dev`, `JWT_ISSUER_URI=""` — identical effective behavior to today, now explicit rather than incidental. No IdP is provisioned yet for any environment (per `CLAUDE.md`), so `jwt_issuer_uri` has no real value to set yet — this is configuration *readiness*, not a claim that OIDC is live (per remediation task instruction 11).
- No identity provider was hardcoded (instruction 7) — the app remains standards-based OAuth2/OIDC resource server; issuer/audience stay externally configurable.

### Audience validation (carried forward, not implemented here)

`JwtDecoders.fromIssuerLocation(issuerUri)` (unchanged, Spring Security's own helper) already performs signature verification (via JWKS), expiry validation (`JwtTimestampValidator`), and issuer validation as part of its standard OIDC-discovery-based configuration. **Audience validation is not currently configured** and fixing it would require a real IdP's client-id/audience value, which doesn't exist yet (instruction 9 explicitly says not to invent one). This is carried forward as a requirement for the RBAC/production-authentication phase, already captured in `docs/security/RBAC_SECURITY_REQUIREMENTS.md` §10 and the production fail-closed checklist in `VAPT_READINESS_REGISTER.md` (SEC-018).

### Secret handling

No secret value appears in any exception message, log statement, or this document (all references above use `[REDACTED]` or describe the value structurally). No new production secret was committed. The local-dev default remains exactly what it was — SEC-001's fix is entirely about *when* that default is allowed to be reached, not its value.

### Tests (`backend/src/test/java/in/gov/jci/hrms/security/`)

| File | Covers |
|---|---|
| `JwtAuthModeTest.java` | Mode-string parsing (canonical, case/hyphen variants, unrecognized/blank → null) |
| `AuthenticationModeGuardTest.java` | All fail-closed/allowed combinations directly against `AuthenticationModeGuard.validate()` — items A-D, J from the remediation brief's test list, plus `isProductionLike` |
| `SecurityConfigJwtDecoderTest.java` | The same scenarios through the **real** `SecurityConfig` constructor → `jwtDecoder()` call (via `MockEnvironment`, no Spring context), to catch any wiring mistake between `SecurityConfig` and `AuthenticationModeGuard` — items A-D again, end to end |
| `JwtAuthenticationSecurityTest.java` | The **real** decoder (not `@MockBean`-replaced, unlike `SecurityConfigTest.RoleMatrixTest`) actually rejects a malformed token, an expired token, and a token signed with the wrong key — items F, G, H. Confirms the SEC-001 change didn't weaken existing signature/expiry validation. |
| `SecurityConfigTest.java` (pre-existing, unmodified) | Still passes — `JwtRoleConverter`/`SecurityUtils`/role-matrix coverage unaffected |

Item E ("OIDC configuration selects trusted OIDC decoder path") is covered by `AuthenticationModeGuardTest`/`SecurityConfigJwtDecoderTest` proving `validate()` returns normally (i.e. execution proceeds to the `JwtDecoders.fromIssuerLocation(...)` call) for every valid OIDC combination, without requiring a live IdP (instruction 12's explicit constraint) — the one-line delegation to Spring's own well-tested helper isn't independently re-tested. Item I ("forged local-HMAC JWT is not accepted in production/OIDC mode") follows structurally: in OIDC mode the decoder is never HS256/symmetric-key-based at all (`JwtDecoders.fromIssuerLocation` builds an RSA/JWKS-based `NimbusJwtDecoder`), so no token signed with the local-dev secret — forged or otherwise — can ever validate against it; this is inherent to using a different decoder type, not a new check to test.

**Status: REMEDIATED AND VERIFIED.**

---

## SEC-002 — Attendance-punch object-level authorization

### Root cause (confirmed before modifying anything)

`MobilePunchController.create()` (`POST /api/attendance/punch`) was gated only `@PreAuthorize("isAuthenticated()")` — no role restriction at all. `MobilePunchRequest.employeeId()` is a plain, client-writable field; when present, `MobilePunchService.create(MobilePunchRequest)` looked it up directly (`employeeRepository.findById(request.employeeId())`) with no comparison against the caller's own identity. The existing `MobilePunchControllerTest` even had a test (`create_asFinanceAdminWithEmployeeIdInBody_returns201`) that asserted this as passing behavior — i.e. the vulnerability was covered by a green test before this fix.

**Old attack path:** any authenticated user — any role, including plain `EMPLOYEE` — sends `POST /api/attendance/punch` with `employeeId` set to a different employee's id, plus arbitrary `latitude`/`longitude`/`punchTime`/`punchType` → a fabricated attendance record (including falsified GPS location) is created and attributed to that other employee, which `MobilePunchService.scheduleDailyAttendanceSync()` then feeds into `daily_attendance`.

### Investigation before changing anything (remediation brief section 15/17)

- **Existing SELF mechanism to reuse:** `AttendanceAggregationSecurity` (`@attendanceAggSec`), already used by `AttendanceAggregationController` and `LeaveLedgerEntryController` for the exact same shape of rule ("self, or HR_ADMIN/SUPER_ADMIN acting on someone else's behalf"). Reused directly rather than inventing a second ownership framework (`EmployeeSecurity`'s `isSelf` is the other established pattern in this codebase but doesn't expose the "or privileged" half as a standalone check the way `AttendanceAggregationSecurity` already does).
- **Non-self capture workflows checked for:** grepped for any kiosk/biometric-terminal/device-integration controller or service-level caller identity distinct from a normal authenticated employee. Found `registered_devices` (`V38__attendance_device_registration.sql`) — but that table is itself **employee-scoped** (`employee_id BIGINT NOT NULL REFERENCES employees(id)`, one row per employee's own registered device) and its own migration comment states `MobilePunchController` never cross-checks a punch's `device_id` against it. There is no shared-kiosk/system-integration caller identity anywhere in the codebase. The only documented non-self workflow was the controller's own prior javadoc comment — "an explicit employeeId in the body (e.g. from an HR tool) is honored as-is" — which named `HR_ADMIN`/`SUPER_ADMIN` in spirit but enforced nothing. This remediation makes that existing intent actually enforced, using the same role pair (`HR_ADMIN`, `SUPER_ADMIN`) this codebase already uses everywhere else for "administer another employee's data" (e.g. `EmployeeController`, `EmployeeBankAccountController`). No new role was invented. Whether `HR_ADMIN` should retain this specific proxy-capture authority (versus a narrower future RBAC role) is noted as a judgment call bounded by existing precedent, not a new business decision manufactured for this fix — flagged forward in `RBAC_SECURITY_REQUIREMENTS.md` for confirmation during RBAC design, not blocking here.
- **Migration/batch-import path:** no batch-import or migration caller of `MobilePunchService.create()` was found (`LegacyMigrationController` covers other domains, not mobile punches).

### New ownership model

**Server-derived identity, checked in two layers (defense in depth):**

1. **Controller (`MobilePunchController.create()`)** — `@PreAuthorize("@attendanceAggSec.canEvaluateFor(authentication, #request.employeeId())")`. `canEvaluateFor` returns true when: the body omits `employeeId` (self-derivation happens downstream, see below), or the body's `employeeId` equals the caller's own JWT `employee_id` claim, or the caller holds `HR_ADMIN`/`SUPER_ADMIN`. Everyone else is rejected with 403 **before the controller method body ever runs**.
2. **Service (`MobilePunchService.create(MobilePunchRequest request, Long callerEmployeeId, boolean onBehalfOfOthersPermitted)`)** — signature changed from a single `MobilePunchRequest` parameter to three: the caller's authoritative identity and authorization decision, both resolved by the controller from the JWT/role check, are now explicit parameters the service itself re-checks (`targetEmployeeId = request.employeeId() != null ? request.employeeId() : callerEmployeeId`; throws `AccessDeniedException` — mapped to 403 by Spring Security's standard `ExceptionTranslationFilter`, same as a `@PreAuthorize` denial — if `!onBehalfOfOthersPermitted && !targetEmployeeId.equals(callerEmployeeId)`). This is the defense-in-depth layer the remediation brief required: a future caller of this service method through some other API path cannot pass a mismatched employeeId without *explicitly and visibly* passing `onBehalfOfOthersPermitted=true` — there is no way to "accidentally" bypass ownership.
3. **`AttendanceAggregationSecurity`** gained one new method, `canActOnBehalfOfOthers(Authentication)`, extracted (pure refactor, behavior-preserving) from the role-check half of the pre-existing `canEvaluateFor`, so both the controller's `@PreAuthorize` and its service-call argument use the exact same rule with no duplicated role list.

Mass assignment (instruction 26): the request DTO still cannot set `approvedBy`/`regularizationStatus`/`attendanceStatus` or any other server-controlled field — `MobilePunchRequest` never carried those fields, and this fix doesn't add any. Geolocation/device evidence fields (`latitude`, `longitude`, `accuracyMeters`, `deviceId`, `photoS3Key`) are unchanged and still recorded exactly as before (instruction 20) — this fix is entirely about *whose* employeeId the record is filed under, not what evidence is captured.

**Separate finding, not fixed here (instruction 21):** `MobilePunchRequest.punchTime()` remains client-supplied (`@NotNull Instant punchTime`) with no trusted-device/server-time cross-check. This is a distinct integrity question (client-controlled authoritative timestamp) from SEC-002's ownership question and is out of this remediation's scope — noted here for the next phase, not fixed.

### Tests

`backend/src/test/java/in/gov/jci/hrms/controller/MobilePunchControllerTest.java` (updated) and `.../service/MobilePunchServiceTest.java` (updated) — 15 and 18 tests respectively (was 11 and 14). New/rewritten coverage:

- **SELF** — an employee punching their own (explicit or omitted) `employeeId` succeeds (201); response is attributed to the correct employee.
- **Cross-employee, ordinary `EMPLOYEE`** — 403, and `mobilePunchService` is verified via `verifyNoInteractions` to have never been called (the record is provably never created).
- **Cross-employee, non-HR admin-tier role (`FINANCE_ADMIN`)** — also 403 (proves the fix isn't accidentally role-name-substring-matching or overly broad).
- **Cross-employee, privileged (`HR_ADMIN`)** — 201, the legitimate workflow still works.
- **Unauthenticated** — 401, `verifyNoInteractions` confirms no record created (this test pre-existed; strengthened with the interaction verification).
- **Service-level defense in depth** — `MobilePunchServiceTest` calls `create()` directly with mismatched `callerEmployeeId`/`onBehalfOfOthersPermitted=false` and asserts `AccessDeniedException`; and with `onBehalfOfOthersPermitted=true` and asserts success — proving the service's own check, independent of the controller.
- `AttendanceAggregationSecurityTest.java` (new) — direct unit coverage of both `canEvaluateFor` (proving the extraction didn't change its behavior) and the new `canActOnBehalfOfOthers`.

**Status: REMEDIATED AND VERIFIED.**

---

## Related Findings (SEC-005 / SEC-006 / SEC-007)

**Not fixed — remain open, as instructed (no scope creep).** These share SEC-002's exact root cause (a client-supplied `employeeId` in a self-service creation DTO, checked only by role, not ownership) but live in entirely separate controllers/services (`AttendanceRegularizationController`/`Service`, `LeaveEncashmentController`/`Service`, `LeaveApplicationController`/`Service`) with their own DTOs. The correction made here does not automatically reach them — each would need the same kind of `@PreAuthorize` + service-signature change applied deliberately. This is documented as a known, larger follow-up for the RBAC/security phase (`docs/security/RBAC_SECURITY_REQUIREMENTS.md` §6, §11), not silently left unmentioned.

---

## Files Changed

**Production code:**
- `backend/src/main/java/in/gov/jci/hrms/security/SecurityConfig.java` (SEC-001)
- `backend/src/main/java/in/gov/jci/hrms/security/JwtAuthMode.java` (new, SEC-001)
- `backend/src/main/java/in/gov/jci/hrms/security/AuthenticationModeGuard.java` (new, SEC-001)
- `backend/src/main/resources/application.yml` (SEC-001)
- `backend/src/main/java/in/gov/jci/hrms/controller/MobilePunchController.java` (SEC-002)
- `backend/src/main/java/in/gov/jci/hrms/service/MobilePunchService.java` (SEC-002)
- `backend/src/main/java/in/gov/jci/hrms/security/AttendanceAggregationSecurity.java` (SEC-002 — new `canActOnBehalfOfOthers` method, pure extraction from existing `canEvaluateFor`)

**Infrastructure (config only, no deploy performed):**
- `infra/variables.tf` (new `jwt_issuer_uri` variable)
- `infra/main.tf` (new `jwt_auth_mode` local; three new ECS task environment entries)

**Tests (new):**
- `backend/src/test/java/in/gov/jci/hrms/security/JwtAuthModeTest.java`
- `backend/src/test/java/in/gov/jci/hrms/security/AuthenticationModeGuardTest.java`
- `backend/src/test/java/in/gov/jci/hrms/security/SecurityConfigJwtDecoderTest.java`
- `backend/src/test/java/in/gov/jci/hrms/security/JwtAuthenticationSecurityTest.java`
- `backend/src/test/java/in/gov/jci/hrms/security/AttendanceAggregationSecurityTest.java`

**Tests (updated):**
- `backend/src/test/java/in/gov/jci/hrms/controller/MobilePunchControllerTest.java`
- `backend/src/test/java/in/gov/jci/hrms/service/MobilePunchServiceTest.java`

**Documentation (this task's deliverables, under `docs/security/` only):**
- `docs/security/SEC_001_002_REMEDIATION.md` (this file)
- `docs/security/VAPT_READINESS_REGISTER.md` (SEC-001, SEC-002 rows updated to COMPLIANT — no other row touched)

**Explicitly NOT changed:** `JwtRoleConverter.java` (confirmed not the root cause of SEC-001, per instruction 8); any CPF/Payroll/JCIECCS production code; any RBAC/role model; any onboarding code; the Attendance Calculation Engine; `AttendanceRegularizationController`/`LeaveEncashmentController`/`LeaveApplicationController` (SEC-005/006/007, left open); any historical Flyway migration; `dev.auto.tfvars` or any other locally-gitignored file.

## Database/Migration Impact

**NONE.** No Flyway migration added or modified, no schema change. Both fixes are authentication-configuration and authorization-logic changes only — no new column, table, or constraint was needed for either SEC-001 or SEC-002.
