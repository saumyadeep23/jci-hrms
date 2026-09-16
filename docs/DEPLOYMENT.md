# Deployment & Network Architecture

Companion to `README.md` (infra provisioning) and `docs/security/VAPT_READINESS_REGISTER.md` (SEC-018/
SEC-023/SEC-026, the security findings this closure overlaps with). This document exists because a real
LAN deployment (a developer/test machine reachable at e.g. `10.0.11.100`) hit three distinct, unrelated
connectivity failures at once — this records the root cause of each and the architecture that prevents
them recurring.

## The three failures this fixes (root causes)

**A — `ERR_CONNECTION_REFUSED` after a reboot / overnight.** Not a code bug: per the old "Building and
running the backend locally" instructions, the only way to start this backend was `mvn spring-boot:run`
in a terminal/IDE session. That process has no OS-level supervisor — a reboot, a closed SSH/RDP session,
or a crash simply left it dead with nothing to restart it. The AWS `dev` environment doesn't have this
problem (ECS Fargate's `desired_count=1` + the ALB health check in `infra/main.tf` already restart a
failed task automatically) — this is specific to any ad-hoc host running the jar directly. See
**Production — backend process** and `deploy/windows/` below for the fix.

**B — `ERR_SSL_PROTOCOL_ERROR` against `:8080`, on both `localhost` and `10.0.11.100`.** Confirmed by
reading `application.yml`: `server.ssl.enabled` defaults to `false` — port 8080 has always served plain
HTTP unless `SERVER_SSL_ENABLED=true` is explicitly set. The frontend's old `api/client.ts` built its API
base URL as `` `${window.location.protocol}//${window.location.hostname}:8080/api` `` — copying the
*page's own* protocol. The moment the page itself was loaded over HTTPS (see cause C — that's exactly
when this happens), every API call became `https://<whatever-host>:8080/...` against an HTTP-only port,
regardless of hostname — which is exactly why swapping `localhost` for `10.0.11.100` didn't help; the
hostname was never the bug. Fixed by making the frontend never guess the backend's protocol at all — see
**Development — API routing** below.

**C — `ERR_CERT_COMMON_NAME_INVALID` on `https://172.20.64.1:5173`.** The optional dev-HTTPS mkcert
certificate (`vite.config.ts`) is generated for specific hostnames/IPs at generation time
(`mkcert ... localhost 127.0.0.1 ::1 <lan-ip>`). The machine has more than one active network address
(`10.0.11.100` and `172.20.64.1` — almost certainly two different network adapters), and the cert's
Subject Alternative Names only covered one of them. Not a bug to "fix" in code — regenerate the cert
listing every address in one run (see **Development — HTTPS for camera/geolocation testing** below).

A fourth, latent issue was found in the same pass and closed alongside these: `SecurityConfig`'s CORS
allowlist only covered two specific `/24` subnets (`192.168.31.*`, `192.168.137.*`), neither of which is
`10.0.11.100` or `172.20.64.1` — once B and C were fixed, that LAN box would have hit a silent CORS
rejection next. The dev-proxy architecture below removes the need for CORS on the primary path entirely,
so this class of failure can't recur regardless of which subnet a dev machine is on.

---

## Development

### Starting the backend

```
cd backend
mvn spring-boot:run
```

Requires a local Postgres reachable at the URL in `application.yml` (override via
`SPRING_DATASOURCE_URL`/`_USERNAME`/`_PASSWORD`). Health check: `GET http://localhost:8080/actuator/health`.
Port 8080, **plain HTTP**, always, in every dev configuration — see Root Cause B above. Do not set
`SERVER_SSL_ENABLED=true` here unless you are specifically doing the mobile-camera/geolocation LAN testing
described below, and even then the frontend never needs to know about it (see next section).

### Starting the frontend

```
cd frontend
npm run dev
```

Serves on `:5173`, bound to `0.0.0.0` so it's reachable from another device on the LAN (a phone testing
the mobile punch flow).

### API routing (the actual fix for Root Cause B)

`frontend/src/api/client.ts`'s `apiClient` now uses a **relative** base URL (`/api`), never one derived
from `window.location`. Two consequences:

- In dev, the browser's request for `/api/employees` goes to whatever origin served the page (Vite, on
  `:5173`, whatever protocol/host that happened to be) and Vite's own dev-server proxy
  (`vite.config.ts`'s `server.proxy['/api']`, `target: 'http://localhost:8080'`) forwards it to the
  backend as a plain Node-to-Node HTTP request — never a browser fetch, so it is **not subject to CORS or
  mixed-content blocking** no matter what protocol the page itself used.
- In production, the same relative `/api/...` request goes to whichever reverse-proxy origin served the
  built frontend, which forwards it to the backend — see **Production** below.

No React component, hook, or page constructs a backend host/port itself — `apiClient` (one `axios.create`
call, in `client.ts`) is the only place. An escape hatch, `VITE_API_BASE_URL` (see
`frontend/.env.example`), exists for a bundle deployed somewhere that genuinely isn't behind a proxy —
it must be a full origin the operator has verified serves that exact protocol; never derive it from the
page's own protocol again.

**Before this fix:** `API_BASE_URL = \`${window.location.protocol}//${window.location.hostname}:8080/api\``
**After this fix:** `API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api'`, proxied by Vite in dev.

### HTTPS for camera/geolocation testing (Root Cause C)

`Attendance/CameraCapture.tsx` (`navigator.mediaDevices.getUserMedia`) and
`attendance/useGeolocation.ts` (`navigator.geolocation`) are genuinely secure-context-only browser APIs.
`localhost` gets a built-in exception; a LAN IP (needed to test from an actual phone) does not — so
testing the mobile punch flow from a real device does need HTTPS, and this is **not** something to
remove. It's independent of the backend's protocol (see Root Cause B — that's exactly the mistake that
was previously made by conflating the two).

To enable it:

```
mkcert -install   # once per dev machine - installs mkcert's local CA into the OS/browser trust store
ipconfig          # find EVERY IPv4 address this machine currently has (multiple adapters = multiple IPs)
mkcert -cert-file certs/dev-cert.pem -key-file certs/dev-key.pem localhost 127.0.0.1 ::1 <ip-1> <ip-2> ...
```

Run from the repo root, listing **every** address in one command — a cert missing one produces exactly
`ERR_CERT_COMMON_NAME_INVALID` for anyone reaching it by that address (Root Cause C). Re-run the whole
command (it overwrites the same two files) whenever a new address becomes relevant; there's no incremental
"add one more SAN" step with mkcert.

Optionally also set `SERVER_SSL_ENABLED=true` with the matching PKCS12 keystore
(`SERVER_SSL_KEY_STORE`/`_KEY_STORE_PASSWORD`, see `application.yml`) if you need the *backend* itself
over HTTPS directly — normally you don't, because the Vite proxy talks to it over plain HTTP regardless
of the frontend's protocol.

**Trusting the cert on other JCI machines:** `mkcert -install` only trusts the CA on the machine that
generated it. For a second machine (e.g. testing from a colleague's phone or laptop) to trust the cert
without a browser warning, export `mkcert -CAROOT`'s `rootCA.pem` and install it as a trusted root on
that device (Android/iOS: install the CA profile; desktop: import into the OS certificate store) — do
**not** just click through a browser security warning as normal practice (Phase 18's explicit
prohibition). If JCI has an internal PKI / approved CA, prefer issuing from that instead of a
developer-machine-local mkcert CA once more than one or two people need this regularly.

### CORS

`SecurityConfig.corsConfigurationSource()` is unchanged in substance, just re-scoped in its own javadoc:
with the proxy above, normal browser traffic to this API is same-origin and never triggers CORS at all.
The existing allowlist (`localhost`/`127.0.0.1` variants, two wildcarded `/24`s) remains only as a
fallback for calling the backend directly, bypassing the proxy — not required for the primary dev flow
on any host/subnet.

---

## Production

Per README.md's own "What's simplified for dev" section, the ALB HTTPS listener already exists in
`infra/main.tf` and activates the moment `acm_certificate_arn` is set to a real ACM certificate — no
Terraform change was needed for this task. The architecture below is what that activation, plus one
frontend change, gets you:

```
Employee Browser
     |  HTTPS :443  (ACM cert, once acm_certificate_arn is set)
     v
ALB  (infra/main.tf: aws_lb.main)
     |
     +---> /api/*  --->  ECS Fargate backend, plain HTTP internally (container_port, target group)
     |
     +---> /*      --->  static frontend build (see below - not yet wired to the ALB; see "Remaining work")
```

- **One trusted origin.** Employees use `https://<approved-hrms-hostname>/` — never `:5173` (a dev
  server, not meant to face users) and never `:8080` directly (the backend's internal port). The
  frontend's own `apiClient` requesting relative `/api/...` (see above) is what makes this same-origin
  routing work without any frontend rebuild per environment.
- **DNS hostname and TLS certificate are infrastructure-administrator responsibilities, not invented
  here.** `infra/main.tf` already gates the HTTPS listener on `var.acm_certificate_arn`; provisioning
  that certificate (matching the real DNS hostname JCI approves for this application, with the correct
  SAN) and pointing DNS at the ALB are the two concrete remaining actions — see "Remaining
  infrastructure work" below. Do not deploy production TLS against an IP address or a certificate issued
  for a different hostname (Phase 18/19's explicit prohibition — VAPT_READINESS_REGISTER.md's SEC-026
  already tracks this as `INFRA_VERIFICATION_REQUIRED`).
- **Serving the React build**: `frontend`'s `npm run build` output (`dist/`) needs a static-file origin
  in front of the ALB's `/api/*` routing — e.g. an S3+CloudFront origin, or a small Nginx/static
  container added to the same ECS cluster serving `dist/` and reverse-proxying `/api/*` to the backend
  service. Neither exists in `infra/main.tf` yet; this is genuinely new infrastructure, not a
  reconfiguration of something already there, and is called out under "Remaining work" rather than
  invented here.
- **Backend stays plain HTTP internally.** Do not enable `SERVER_SSL_ENABLED` in the ECS task definition
  merely because employees reach the ALB over HTTPS — TLS terminates at the ALB (or whichever reverse
  proxy replaces/fronts it), exactly per this task's Phase 2 instruction. This matches the existing
  `local.hardened` pattern in `infra/main.tf` (`staging`/`prod` already assume ALB-terminated TLS).

### Backend process (ad-hoc / non-AWS hosts — Root Cause A)

For any host running this backend *outside* the ECS-managed AWS environment (e.g. the LAN test box that
triggered this investigation), the backend needs the same restart-on-failure/restart-on-reboot guarantee
ECS gives the AWS deployment for free. `deploy/windows/install-hrms-backend-service.ps1` installs it as a
genuine Windows service (via NSSM) with `Restart=on-failure`-equivalent behavior and auto-start on boot —
see that script's own header comment for full usage, and `deploy/windows/hrms-backend.env.example` for
the required environment variables (copy it, fill in real values, never commit the filled-in copy — it
holds the database password).

```powershell
# From an elevated PowerShell, from a checkout of this repo, after `mvn -B clean package -DskipTests`:
cd backend; mvn -B clean package -DskipTests; cd ..
Copy-Item deploy\windows\hrms-backend.env.example C:\hrms\hrms-backend.env   # then edit it
.\deploy\windows\install-hrms-backend-service.ps1 -NssmExe 'C:\nssm\nssm.exe' -EnvFile 'C:\hrms\hrms-backend.env'
Start-Service JciHrmsBackend
Get-Service JciHrmsBackend
```

Verify: `Get-Service JciHrmsBackend` should show `Running`; `Invoke-WebRequest http://localhost:8080/actuator/health`
should return `200`. Reboot the machine and confirm the service is `Running` again with no manual step.

### Database startup resilience

No arbitrary HikariCP tuning was added — this app's actual load has never been measured, and Spring
Boot's/HikariCP's own defaults (10 connections, 30s connection timeout, standard idle/max-lifetime) are
reasonable for a system this early-stage; inventing different numbers without load data would be guessing,
which this task's own instructions rule out. The genuine fix for "app was briefly unavailable because the
database started later than it did" is the same **service-supervisor restart** as Root Cause A above: if
Spring Boot fails to start (Flyway can't reach the DB yet), NSSM (dev/LAN host) or ECS (AWS) restarts the
whole process automatically after a short backoff, rather than requiring a human to notice and re-run it.
On the AWS side, if backend and RDS are ever provisioned to depend on strict startup ordering, Terraform's
own `depends_on` between the ECS service and RDS instance would be the mechanism — not currently needed
since RDS in this architecture is already up well before any ECS deployment triggers.

### Health / readiness

`spring-boot-starter-actuator` is already present, already minimally exposed
(`management.endpoints.web.exposure.include: health,info` — nothing else, per SEC-025's existing
verification), and already wired as the ALB's target-group health check (`infra/main.tf`,
`aws_lb_target_group.backend.health_check.path = "/actuator/health"`) and as the Windows service's
verification step above. No further Actuator surface was added — `show-details: when-authorized` already
avoids leaking dependency detail to unauthenticated callers. A liveness/readiness *split* (e.g. Spring
Boot's `management.endpoint.health.probes.enabled`) was not added this pass: this app has one simple
external dependency (Postgres) and the existing single `/actuator/health` already reflects it via the
default `DataSourceHealthIndicator` — introducing separate liveness/readiness endpoints would be new
surface without a concrete driving requirement yet.

### Logging

No `logging:` section previously existed in `application.yml` (Spring Boot's bare console-only default) —
on the AWS side this doesn't matter (the `awslogs` driver in `infra/main.tf` already captures container
stdout/stderr into CloudWatch, persisting past any task restart). On a non-AWS host, stdout/stderr alone
disappears the moment the process dies unless something captures it — `install-hrms-backend-service.ps1`
redirects NSSM's captured stdout/stderr to `logs/hrms-backend.{out,err}.log` with basic size/day-based
rotation (`AppRotateBytes`/`AppRotateSeconds`) so this survives crashes and reboots. No secrets are logged
by design already: `AuditActor` resolves the real Spring Security principal (not headers), and nothing in
this change touches request/credential logging. When adding any future log statement, continue to never
log passwords, JWTs, Aadhaar, biometric templates, bank details, or raw payroll figures (existing
convention, unchanged).

To inspect the Windows service's logs: `Get-Content logs\hrms-backend.out.log -Tail 100 -Wait`. AWS side:
`aws logs tail /ecs/<project>-<environment>-backend --follow` (exact log group name from
`aws_cloudwatch_log_group.backend` in `infra/main.tf`).

### Frontend network-failure handling

`api/client.ts`'s response interceptor now distinguishes "the whole backend is unreachable"
(`isNetworkError` in `lib/apiError.ts` — no `error.response` at all: connection refused, DNS failure,
timeout, offline) from an HTTP error status, and publishes that to a small shared module
(`lib/networkStatus.ts`) rather than firing one toast per failed request. `NetworkStatusBanner.tsx`
(mounted once, in `main.tsx`) shows a single "HRMS service is temporarily unavailable. Retrying..."
banner instead of leaving users to conclude Employees/Payroll/Audit/Holidays independently broke.
GETs already retry once by default (`main.tsx`'s `QueryClient({ defaultOptions: { queries: { retry: 1 } } })`,
unchanged); mutations (`useMutation`) are not retried unless a specific call opts in, which none do —
matching the "don't blindly retry non-idempotent writes" requirement without any new code, since this was
already react-query's default behavior.

---

## Remaining infrastructure work (requires a JCI administrator / infra decision — not invented here)

- **Approved DNS hostname** for the production HRMS origin — nothing here should assume or hardcode one.
- **TLS certificate** for that hostname (ACM if staying on this AWS architecture — set
  `var.acm_certificate_arn`; or JCI's internal PKI if policy requires it) — must list the real hostname
  as its SAN and never be validated against by IP address.
- **A place to serve the production frontend build** and route `/api/*` to the backend alongside it —
  not yet provisioned in `infra/main.tf` (see "Serving the React build" above); an S3+CloudFront origin
  or a small static-serving container are the two most natural fits for the existing ALB-fronted
  architecture.
- **Whichever non-AWS LAN/test hosts run this backend today** need `deploy/windows/install-hrms-backend-service.ps1`
  actually run on them by whoever administers that specific machine — this repository cannot reach out
  and install a service on a machine it isn't running on.
