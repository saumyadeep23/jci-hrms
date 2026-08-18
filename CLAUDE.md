# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Early-stage infrastructure and backend skeleton for JCI HRMS, an HR management system for JCI (a government-adjacent Indian entity — see compliance notes below). Everything targets AWS `ap-south-1` (Mumbai) for data residency. The backend is currently just a health-check endpoint; no domain logic (employees, payroll, etc.) exists yet.

## Repository layout

- `infra/` — Terraform for VPC, RDS PostgreSQL, ECS Fargate, ALB, ECR, S3 (single environment, parameterized by `var.environment`, currently used for `dev`)
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

- **Networking**: 2-AZ VPC, public subnets (ALB + single NAT gateway — dev-cost tradeoff, not HA) and private subnets (ECS tasks + RDS, no public access).
- **Security groups are chained**: internet → ALB SG (80/443) → ECS SG (container_port only, from ALB SG) → RDS SG (5432 only, from ECS SG). When adding a new resource that needs DB or service access, follow this chain rather than opening broader CIDR ranges.
- **IAM roles are split**: `ecs_task_execution` role (pulls images, writes logs — AWS-managed policy) vs `ecs_task` role (what the *application* can do at runtime, e.g. S3 access to the documents bucket). Add new app permissions (e.g. Secrets Manager access) to the task role, not the execution role.
- **DB credentials**: currently passed as plain ECS task-definition env vars (`main.tf`'s `environment` block). There's a placeholder `secrets` block already in the task definition — when adding Secrets Manager-backed credentials, wire them there rather than adding more plaintext env vars.
- **CI/CD** uses GitHub OIDC federation to assume an AWS role (`AWS_DEPLOY_ROLE_ARN` secret) — no long-lived AWS keys in GitHub. The pipeline only runs build+test on PRs; the push-to-ECR-and-deploy job only runs on `push` to `main`.
- Region (`ap-south-1`) is hardcoded as the Terraform variable default — do not parameterize this away without checking the data-residency requirement below.

## Compliance constraints (JCI-specific — keep in mind when making infra/data changes)

- All resources must stay in `ap-south-1` — this is a data residency requirement, not just a cost choice.
- The system may eventually handle Aadhaar-linked data — STQC audit and CERT-In empanelled VAPT are expected before any production launch; MeitY/GI Cloud empanelment and IT Security Committee sign-off are expected before going beyond dev environments.
- Several things in `infra/` are intentionally simplified for dev and called out inline (`README.md` has the full list): single-AZ RDS with no CMK, S3 using the AWS-managed KMS key instead of a customer-managed one, HTTP-only ALB listener (no HTTPS/ACM cert), no WAF, DB password as a plain env var instead of Secrets Manager, single NAT gateway. Don't "fix" these silently as drive-by cleanup — they're deliberate for now, but should be flagged if a change is heading toward staging/prod.
