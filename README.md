# JCI HRMS — Development Environment

Infrastructure and starter backend for the JCI HRMS project, targeting AWS
(ap-south-1 / Mumbai, for data residency).

## What's in here

```
infra/       Terraform for VPC, RDS PostgreSQL, ECS Fargate, ALB, ECR, S3
backend/     Spring Boot backend skeleton (Java 21)
frontend/    React/Vite frontend
deploy/      Non-AWS host deployment helpers (e.g. the Windows service installer for a LAN box)
.github/     GitHub Actions CI/CD pipeline: build -> push to ECR -> deploy to ECS
```

See `docs/DEPLOYMENT.md` for the full dev/production network, TLS, and backend-lifecycle architecture
(API routing, the Vite dev proxy, dev-HTTPS for camera/geolocation testing, and how to run the backend
as a real auto-restarting service instead of a manually-started process).

## One-time setup before first use

1. **Install tools locally**: Terraform >= 1.5, AWS CLI v2, Java 21, Maven, Docker.
2. **Configure the AWS CLI** with an IAM user or role that has permission to
   create the resources in `infra/` (VPC, RDS, ECS, ALB, S3, ECR, IAM roles).
   Do this with your IAM admin user from account setup, not root.
3. **Set the DB password** as an environment variable rather than committing
   it anywhere:
   ```
   export TF_VAR_db_password="choose-a-strong-password"
   ```
4. **(Recommended) Set up a remote Terraform state backend** — an S3 bucket
   + DynamoDB lock table — before the first `apply`, so state isn't only on
   one person's laptop. The commented-out block in `infra/versions.tf` shows
   the shape; create the bucket/table once, then uncomment and
   `terraform init -migrate-state`.

## Provisioning the infrastructure

```
cd infra
terraform init
terraform plan    # review what will be created
terraform apply
```

This creates a VPC with public/private subnets, an RDS PostgreSQL instance,
an ECS Fargate cluster + service behind an ALB, an ECR repo, and an S3
bucket for documents. Note the outputs at the end — `ecr_repository_url` and
`alb_dns_name` are needed next.

The ECS service will fail to start containers until an image exists in ECR
(see next section) — that's expected on the very first apply.

## Building and running the backend locally

```
cd backend
mvn spring-boot:run
```

Point it at a local Postgres (or override `SPRING_DATASOURCE_URL` to the
RDS endpoint from Terraform's output, if your machine can reach it — it
won't be able to by default, since RDS sits in private subnets).

Health check: `GET http://localhost:8080/actuator/health`

## Pushing the first image manually (before CI/CD is wired up)

```
cd backend
aws ecr get-login-password --region ap-south-1 | docker login --username AWS --password-stdin <ECR_REGISTRY>
docker build -t <ECR_REGISTRY>/jci-hrms-backend:latest .
docker push <ECR_REGISTRY>/jci-hrms-backend:latest
```

Then force a new ECS deployment:
```
aws ecs update-service --cluster jci-hrms-dev-cluster --service jci-hrms-dev-backend --force-new-deployment
```

## Setting up CI/CD (GitHub Actions)

The workflow in `.github/workflows/ci-cd.yml` uses OIDC federation to assume
an AWS role rather than storing long-lived AWS keys as GitHub secrets. One-time
setup:

1. Create an IAM OIDC identity provider for `token.actions.githubusercontent.com`
   in your AWS account (if one doesn't already exist).
2. Create an IAM role that trusts that provider, scoped to your GitHub repo,
   with permissions to push to ECR and update the ECS service/task definition.
3. Add that role's ARN as a GitHub Actions secret named `AWS_DEPLOY_ROLE_ARN`
   in the repo settings.

From then on, every push to `main` that touches `backend/**` builds, tests,
pushes an image, and deploys it to ECS automatically.

## What's simplified for dev — tighten before production

Most of these now flip on automatically based on `var.environment` — no
extra flags needed beyond setting `environment = "staging"` or `"prod"`
(e.g. `-var="environment=prod"`), except the HTTPS listener which also
needs a real certificate:

- **RDS Multi-AZ + customer-managed KMS key**: single-AZ + AWS-managed key
  on `dev`; Multi-AZ + a dedicated CMK (`aws_kms_key.data`, rotation
  enabled) on `staging`/`prod`.
- **S3 customer-managed KMS key**: same CMK as RDS is used for the
  documents bucket's SSE on `staging`/`prod`; AWS-managed key on `dev`.
- **WAF in front of the ALB**: an `aws_wafv2_web_acl` with AWS's Common and
  Known-Bad-Inputs managed rule groups is attached on `staging`/`prod` only.
- **DB password via Secrets Manager**: always on now, in every environment
  — the ECS task pulls `SPRING_DATASOURCE_PASSWORD` from
  `aws_secretsmanager_secret.db_password` via the task definition's
  `secrets` block, not a plaintext env var.
- **NAT gateway per AZ**: still a single NAT on `dev`/`staging` to keep
  cost down; `prod` gets one NAT gateway (and private route table) per AZ.
- **ALB HTTPS listener**: still HTTP-only until you set the
  `acm_certificate_arn` variable to a real ACM certificate ARN — once set,
  a 443 listener is created and the HTTP listener starts redirecting to
  HTTPS instead of forwarding directly. No cert exists yet for a fresh
  account/domain, so this one isn't tied to `environment`.

## Compliance reminders specific to JCI

- Keep everything in `ap-south-1` — this is enforced by hardcoded region
  defaults here, but should also be enforced via Service Control Policies
  once JCI's AWS Organization is set up.
- Get MeitY/GI Cloud empanelment and IT Security Committee sign-off recorded
  against this project before anything beyond dev environments goes live.
- STQC audit and CERT-In empanelled VAPT should be scheduled before any
  production launch, particularly given Aadhaar-linked data.
