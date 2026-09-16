# JCI HRMS — Attack Surface Inventory

**Audit type:** Internal pre-RBAC VAPT readiness review (read-only, source/configuration-based).
**Baseline commit:** `6067227b40c679d9be83ad1f709e818d93405b71` (branch `main`).
**Generated:** 2026-09-15.
**Method:** Static review of `backend/src/main/java`, `frontend/src`, `infra/*.tf`, `.github/workflows/ci-cd.yml`. No dynamic scanning against any live/production system was performed. Endpoint counts below reflect independent verification (grep + source reading), not a trust of commit messages or CLAUDE.md alone.

---

## 1. Backend — REST surface

- **96** `@RestController` classes under `backend/src/main/java/in/gov/jci/hrms/controller/`.
- **474** HTTP-method-mapping annotations (`@GetMapping`/`@PostMapping`/`@PutMapping`/`@DeleteMapping`/`@PatchMapping`).
- **260** `@PreAuthorize`/`@Secured` occurrences across 93 files (class-level annotations cover most methods in a class — see `CURRENT_AUTHORIZATION_MODEL.md` for the full matrix).
- Full endpoint-by-endpoint authorization matrix: `docs/security/CURRENT_AUTHORIZATION_MODEL.md`.

### 1.1 Authentication boundary (`SecurityConfig.java`)

All requests are stateless-JWT bearer-token authenticated (`SessionCreationPolicy.STATELESS`). Exactly four path patterns are `permitAll` (`backend/src/main/java/in/gov/jci/hrms/security/SecurityConfig.java:60-61`):

| Path | Purpose |
|---|---|
| `OPTIONS /**` | CORS preflight |
| `/api/ping` | Lightweight liveness check (non-actuator) |
| `/actuator/health`, `/actuator/health/**` | Health probe |
| `/actuator/info` | Build/info probe |

Everything else is `anyRequest().authenticated()` — i.e. no endpoint is reachable by a fully anonymous caller except the four above. Method-level `@PreAuthorize` then narrows by role/object ownership on top of that baseline.

### 1.2 Zero-`@PreAuthorize` controllers (informational — none are sensitive)

| Controller | Reason not annotated | Risk |
|---|---|---|
| `HealthController` | `/api/ping`, explicitly `permitAll` | None |
| `FinanceLookupController`, `GeoLookupController`, `LookupController` | Thin read-only reference-data lookups (pincode/IFSC/master lists), no PII | Low — still gated by `anyRequest().authenticated()`, not internet-open |

### 1.3 Admin / financial / statutory controllers (high sensitivity — see authorization model doc for per-endpoint detail)

- **CPF**: `CpfLoanController`, `CpfApplicationController`, `CpfTrustController`, `CpfTrustMemberController`, `CpfWithdrawalRuleController`, `CpfWithdrawalMasterController`, `PfLedgerController`, `LoanController`, `CpfSelfServicePassbookController`, `CpfSelfServiceDisputeController`.
- **Payroll**: `PayrollMasterController`, `PayrollBatchController` (and computation/edit/report services behind it), `EssPayrollController` (self-service salary slips).
- **JCIECCS** (Phase 5, commit `6067227`): `JciEccsLoanController`, `JciEccsRecoveryController`, `JciEccsSettlementController`, `JciEccsReconciliationController`, `JciEccsPayrollBatchController`, `JciEccsIntegrityCheckController`, `JciEccsAuditTrailController`, `JciEccsMemberController`, `JciEccsMigrationController` (9 controllers total — the "8 Phase 5 controllers" from the commit message plus a migration controller; independently confirmed 7/9 carry class-level `@PreAuthorize` and the remaining 2 are fully covered at method level).
- **Attendance / geolocation**: `MobilePunchController` (punch creation + retrieval — see critical finding SEC-002 in `PRE_RBAC_SECURITY_AUDIT.md`), `AttendanceHistoryController`, `AttendanceRegularizationController`.
- **Employee master + PII sub-resources**: `EmployeeController` plus ~13 `Employee*Controller`s for bank accounts, addresses, family, dependents, nominees, qualifications, past service, social profile, vehicle/quarter allotment.
- **Onboarding (draft only — no activation flow exists yet)**: `EmployeeOnboardingDraftRepository`/related DTOs found; no controller for invitation/activation exists in this codebase today (see `ONBOARDING_SECURITY_REQUIREMENTS.md`).
- **Document upload**: `DocumentUploadController` (`/api/v1/documents` or similar) — upload only; **no document-download endpoint exists anywhere in the backend** (`DocumentStorageService` interface exposes only `store()`).
- **Leave**: `LeaveApplicationController`, `LeaveEncashmentController`, `LeaveLedgerEntryController`, `LeaveTypeMasterController`.
- **APAR**: `AparController` — uses a mature object-level pattern (`@aparSec.isParticipant/isSelf/isReportingOfficer/isReviewingOfficer`).
- **Master/reference data**: ~15 controllers (`DepartmentController`, `DesignationController`, `DpcController`, `HolidayController`, `PostMasterController`, `RegionalOfficeController`, `ShiftController`, `VendorMasterController`, `MasterStateController`/`StateMasterController` (inconsistent posture — see authorization model doc), `DistrictMasterController`, etc.) — mostly `HR_ADMIN`/`SUPER_ADMIN` gated.

### 1.4 Actuator

- `management.endpoints.web.exposure.include: health,info` only (`backend/src/main/resources/application.yml`) — no other profile file exists to override this (`find` confirms `application.yml` is the sole config file).
- `management.endpoint.health.show-details: when-authorized` — Spring's default authorization check for this (`request.isUserInRole("ADMIN")`) does not align with this app's custom JWT role names (`SUPER_ADMIN` etc., no `management.endpoint.health.roles` override configured), so in practice detailed health is unlikely to activate for any caller under the current config.
- No `beans`, `env`, `configprops`, `heapdump`, `threaddump`, `metrics`, or `mappings` actuator endpoints are exposed.

### 1.5 Swagger / OpenAPI

No `springdoc`/`swagger` dependency anywhere in `backend/pom.xml`. **Not present — nothing to secure.**

### 1.6 File upload / download

- **Upload**: `DocumentUploadController` → `DocumentUploadService` → `UploadCategoryPolicy` → `LocalStorageServiceImpl`. Category-scoped MIME+extension allow-lists (only `image/jpeg`, `image/png`, `application/pdf` across all 16 categories), magic-byte validation, per-category size caps, sanitized filenames, storage-path traversal double-guarded. See `PRE_RBAC_SECURITY_AUDIT.md` §File/Report Security.
- **Download**: **not implemented** — no retrieval endpoint exists. Flagged forward for when it is built (see onboarding/RBAC requirement docs).

### 1.7 PDF / report generation

- PDFBox (`org.apache.pdfbox:pdfbox`, pinned `3.0.3`) generates salary slips and similar documents by direct content-stream text drawing — not a template/expression engine (no SSTI surface).
- Apache POI (`poi-ooxml`, pinned `5.3.0`) generates XLSX exports (`XlsxExportService`) — see MEDIUM finding SEC-012 (CSV/formula-injection hardening) in the main audit report.
- Ad-hoc SQL reporting (`AdHocReportService`) uses a hardcoded column allow-list plus bound parameters — reviewed, safe.

### 1.8 Webhook / callback / integration endpoints

No webhook, payroll-callback, or JCIECCS-callback endpoint was found in the controller inventory (no `@PostMapping` path containing `callback`/`webhook`/`hook`, and no unauthenticated inbound integration surface beyond the four `permitAll` paths above). External calls are outbound only (India Post pincode lookup, Razorpay IFSC lookup — both read-only, fixed-base-URL, strictly-validated-input proxies; see SSRF review in the main report).

---

## 2. Frontend surface (React 19 SPA, Vite build)

- **Routing**: `frontend/src` uses `react-router-dom` (7.18.2); authenticated routes are wrapped by `ProtectedRoute.tsx` (`frontend/src/components/common/ProtectedRoute.tsx:11-26`), which performs a **client-side-only** check (`isAuthenticated`/`hasRole` from `AuthContext`) — no server call. This is explicitly documented in the main audit report as **UX-only**; the backend `@PreAuthorize` layer is authoritative (spec §87).
- **Token storage**: JWT is stored in `localStorage` under key `jci-hrms-token` (`frontend/src/auth/AuthContext.tsx:16,20,30,35`; `frontend/src/api/client.ts:4,21,51`) — not an httpOnly cookie. Standard SPA/bearer-token tradeoff; see hardening recommendation in the main report (XSS-exfiltration exposure).
- **API client**: `frontend/src/api/client.ts:11` derives the API base URL at runtime from `window.location.protocol`/`hostname` (not a build-time env var) — `${protocol}//${hostname}:8080/api`.
- **Environment/config exposure**: no `VITE_*`/`import.meta.env` secret-shaped values found anywhere in `frontend/src`. No frontend secret-exposure surface exists today because there is no env-var-driven config to expose.
- **XSS sinks**: zero `dangerouslySetInnerHTML`/`.innerHTML`/`eval(` usages repo-wide — React's default JSX escaping applies to all rendered text (names, remarks, addresses, etc.).
- **A full frontend route-by-route inventory (every page/component) was not exhaustively enumerated in this pass** — the above reflects the security-relevant surface (auth guard mechanism, token handling, API client, XSS sinks) verified directly. A future pass should walk `frontend/src/pages` or equivalent route-definition file to produce a complete route list if required for a formal VAPT scope document.

---

## 3. Infrastructure surface (Terraform, `ap-south-1`, `dev` environment only currently deployed)

| Layer | Configuration | Evidence |
|---|---|---|
| Network | 2-AZ VPC, public subnets (ALB + NAT) / private subnets (ECS + RDS, no public access) | `infra/main.tf` |
| Security groups (chained) | Internet → ALB SG (80/443, `0.0.0.0/0`) → ECS SG (`container_port` only, from ALB SG) → RDS SG (5432 only, from ECS SG). No CIDR-based DB/app access exists anywhere. | `infra/main.tf:97-162` |
| Load balancer | Internet-facing ALB. HTTP listener forwards directly (no cert configured); HTTPS listener only materializes when `var.acm_certificate_arn` is set (currently unset — default `""`) | `infra/main.tf:386-453`, `variables.tf:79-82` |
| Compute | ECS Fargate, `awsvpc` network mode, one `backend` container, `containerPort = var.container_port` | `infra/main.tf:351-384` |
| Database | RDS PostgreSQL, `publicly_accessible = false`, `storage_encrypted = true` always, KMS CMK + Multi-AZ only when `local.hardened` (staging/prod) | `infra/main.tf:274-283` |
| Object storage | S3 documents bucket — versioning on, SSE (KMS when hardened), full public-access-block (all 4 flags) | `infra/main.tf:175-197` |
| WAF | `aws_wafv2_web_acl` (AWS Common + Known-Bad-Inputs managed rule groups) attached to ALB only when `local.hardened` (staging/prod) | `infra/main.tf:459-517` |
| Secrets | DB password sourced from Secrets Manager via ECS task `secrets` block in every environment; **no JWT/issuer configuration exists in Terraform at all** (see SEC-001 in the main report) | `infra/main.tf:372-374` |
| IAM | Split `ecs_task_execution` (image pull + logs) vs `ecs_task` (app runtime — S3 access only, scoped to the documents bucket ARN). No `Action:"*"`/`Resource:"*"` found anywhere. | `infra/main.tf:307-346` |
| CI/CD deploy identity | GitHub OIDC federation (`token.actions.githubusercontent.com`) assumes an AWS role — no long-lived AWS keys in GitHub. Trust policy `sub` scoped to `repo:saumyadeep23/jci-hrms:*` (any ref in this repo, not just `push` to `main` — see hardening note in the main report). | `infra/github-actions-deploy-role/trust-policy.json` |
| Container registry | ECR, `image_tag_mutability = IMMUTABLE`, `scan_on_push = true` | `infra/main.tf:207-209` |
| Region | `ap-south-1` (Mumbai) hardcoded as the Terraform variable default — data-residency requirement, not just cost | `infra/variables.tf` |

**Not yet provisioned / out of scope for a live attack surface today**: staging and prod environments (only `dev` is deployed — confirmed by the presence of only `infra/dev.auto.tfvars` locally, no other environment tfvars). Terraform state is local-only (`infra/terraform.tfstate`, gitignored) — see INFO finding in the main report about plaintext secrets on local disk pending the S3+DynamoDB backend migration already stubbed in `infra/versions.tf`.

---

## 4. CI/CD surface

- Single workflow: `.github/workflows/ci-cd.yml` (96 lines). `build-and-test` runs on `push`/`pull_request` to `main` (path-filtered to `backend/**`); `build-push-deploy` runs only on `push` to `main` (`if: github.ref == 'refs/heads/main' && github.event_name == 'push'`), gated on `needs: build-and-test`.
- No `pull_request_target` usage (safe — fork PRs run with fork's own read-only token, cannot access secrets).
- Explicit `permissions: { id-token: write, contents: read }` — correctly minimal.
- Third-party actions pinned to floating major-version tags (`@v4`, `@v2`, `@v1`), not commit SHAs — supply-chain hardening item (see main report).

---

## Summary of externally-reachable surface today (`dev` environment, as coded)

1. ALB (HTTP only, port 80) → ECS Fargate `backend` container, port 8080 — all backend REST endpoints listed above, behind JWT bearer-token auth (except the four `permitAll` paths).
2. No public S3 bucket, no public RDS endpoint, no public actuator detail, no Swagger, no webhook/callback inbound surface.
3. Frontend is a static SPA (Vite build) — hosting mechanism not defined in this repo (no Amplify/S3-website/CloudFront config found in `infra/`); calls the backend ALB directly from the browser.
4. GitHub Actions CI/CD pipeline — OIDC-federated deploy identity, no long-lived credentials, scoped IAM.
