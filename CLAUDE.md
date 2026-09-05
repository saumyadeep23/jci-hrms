# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Early-stage infrastructure and backend skeleton for JCI HRMS, an HR management system for JCI (a government-adjacent Indian entity — see compliance notes below). Everything targets AWS `ap-south-1` (Mumbai) for data residency. Domain logic is just getting started: an `Employee` entity/schema (Flyway-migrated) is in place; no other HR domains (payroll, etc.) exist yet.

## Repository layout

- `infra/` — Terraform for VPC, RDS PostgreSQL, ECS Fargate, ALB, ECR, S3, parameterized by `var.environment` (`dev`, `staging`, `prod`), currently deployed for `dev` only
- `backend/` — Spring Boot 3.3.2 backend (Java 21), Maven build
- `.github/workflows/ci-cd.yml` — build → test → push to ECR → deploy to ECS, triggered on push to `main` touching `backend/**`

## Common commands

**Backend (run from `backend/`):**
```
mvn spring-boot:run              # run locally against a local Postgres
mvn -B clean verify              # build + run tests (what CI runs)
mvn test -Dtest=ClassName        # run a single test class
```
Requires a local Postgres reachable at the URL in `application.yml`, or override via `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` env vars. Health check: `GET http://localhost:8080/actuator/health`. There's also a lightweight `GET /api/ping` separate from actuator, for a quick manual sanity check after deploys.

**Infra (run from `infra/`):**
```
terraform init
terraform plan
terraform apply
```
Requires `TF_VAR_db_password` set (never commit a password/tfvars with it). Terraform state is local by default — an S3+DynamoDB backend is stubbed out (commented) in `versions.tf` and should be wired up before this is used by more than one person.

**Docker:**
```
cd backend
docker build -t <ECR_REGISTRY>/jci-hrms-backend:latest .
```
Multi-stage build (Maven build stage → JRE-only run stage), runs as non-root `appuser`.

## Architecture notes

- **Networking**: 2-AZ VPC, public subnets (ALB + NAT gateway(s)) and private subnets (ECS tasks + RDS, no public access).
- **Security groups are chained**: internet → ALB SG (80/443) → ECS SG (container_port only, from ALB SG) → RDS SG (5432 only, from ECS SG). When adding a new resource that needs DB or service access, follow this chain rather than opening broader CIDR ranges.
- **IAM roles are split**: `ecs_task_execution` role (pulls images, writes logs — AWS-managed policy) vs `ecs_task` role (what the *application* can do at runtime, e.g. S3 access to the documents bucket). Add new app permissions to the task role, not the execution role.
- **DB credentials**: sourced from Secrets Manager (`aws_secretsmanager_secret.db_password`) via the task definition's `secrets` block, in every environment — not a plaintext env var. Wire any new sensitive config through the same `secrets` block rather than adding plaintext env vars.
- **Environment-aware hardening**: `main.tf` derives `local.hardened = contains(["staging", "prod"], var.environment)` and gates several resources on it — `dev` stays on cheap/simple defaults, `staging`/`prod` get tightened automatically, no extra flags beyond setting `-var="environment=staging"` (or `"prod"`):
  - RDS: single-AZ on `dev`; `multi_az = true` when hardened.
  - KMS: `dev` uses the AWS-managed key for RDS/S3 encryption; hardened environments get a dedicated customer-managed key (`aws_kms_key.data`, rotation enabled) shared by RDS and the S3 documents bucket.
  - WAF: an `aws_wafv2_web_acl` (AWS Common + Known-Bad-Inputs managed rule groups) is attached to the ALB only when hardened.
  - Two things are *not* tied to `environment` and need explicit handling: NAT gateways (one shared NAT on `dev`/`staging`, one per AZ only on `prod` — `local.is_prod`, not `local.hardened`) and the ALB HTTPS listener, which stays HTTP-only until `var.acm_certificate_arn` is set to a real ACM cert (no cert exists yet for any environment).
- **CI/CD** uses GitHub OIDC federation to assume an AWS role (`AWS_DEPLOY_ROLE_ARN` secret) — no long-lived AWS keys in GitHub. The pipeline only runs build+test on PRs; the push-to-ECR-and-deploy job only runs on `push` to `main`, and only deploys the `dev` ECS cluster/service (hardcoded in the workflow env vars) — it does not yet handle staging/prod.
- Region (`ap-south-1`) is hardcoded as the Terraform variable default — do not parameterize this away without checking the data-residency requirement below.

## Compliance constraints (JCI-specific — keep in mind when making infra/data changes)

- All resources must stay in `ap-south-1` — this is a data residency requirement, not just a cost choice.
- The system may eventually handle Aadhaar-linked data — STQC audit and CERT-In empanelled VAPT are expected before any production launch; MeitY/GI Cloud empanelment and IT Security Committee sign-off are expected before going beyond dev environments.
- Two things in `infra/` are still intentionally simplified regardless of environment and called out inline (`README.md` has the full list): the ALB HTTPS listener (HTTP-only until `acm_certificate_arn` is set — no cert exists yet) and the single NAT gateway on non-`prod` environments. Everything else that used to be a flat dev simplification (RDS Multi-AZ, customer-managed KMS, WAF, Secrets Manager for the DB password) is now handled by the `local.hardened`/`local.is_prod` environment gating described above. Don't "fix" the two remaining flat items as drive-by cleanup — they're deliberate for now (no domain/cert provisioned, cost tradeoff on `staging`) — but flag them if a change is heading toward a real staging/prod rollout.
