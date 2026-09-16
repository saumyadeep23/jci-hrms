# RBAC Permission Matrix

Source of truth: `backend/src/main/resources/db/migration/V94__rbac_core_schema.sql` (`role_permissions` seed). This document is a readable projection of it — if they ever disagree, the migration is authoritative.

## Role → Permission → Default Scope → Checker Capability → Financial Authority

| Role | Permissions granted | Typical scope | Checker (approve/post/reverse)? | **Financial authority** |
|---|---|---|---|---|
| **SYSTEM_ADMIN** | `USER_VIEW`, `USER_PROVISION`, `USER_INVITE`, `USER_INVITE_RESEND`, `USER_INVITE_REVOKE`, `USER_DISABLE`, `USER_ROLE_ASSIGN`, `SECURITY_DIAGNOSTICS_VIEW`, `AUDIT_VIEW`, `EMPLOYEE_VIEW` | ALL_JCI | No | **NONE — by design (SEC-010)** |
| USER | `EMPLOYEE_SELF_VIEW`, `CPF_APPLY_SELF`, `ATTENDANCE_SELF_PUNCH`, `ATTENDANCE_REGULARIZE`, `LEAVE_SELF_APPLY` | SELF | No | None |
| HR_ADMIN | `USER_VIEW/PROVISION/INVITE/INVITE_RESEND/INVITE_REVOKE`, `EMPLOYEE_VIEW`, `ATTENDANCE_VIEW/APPROVE`, `LEAVE_APPROVE` | ALL_JCI (typical) | Yes (attendance/leave) | None |
| HR_ADMIN_PERS / HR_MAKER_PERS | (reserved — personnel prepare/approve; no live endpoint gated by these yet this phase) | office/region | ADMIN=yes, MAKER=no | None |
| HR_MAKER_BILL | `PAYROLL_VIEW`, `PAYROLL_PREPARE` | office/region | No (maker) | None |
| HR_ADMIN_BILL | `PAYROLL_VIEW`, `PAYROLL_FINALIZE`, `PAYROLL_REVERSE` | ALL_JCI (typical) | Yes | **Yes** (payroll finalize/reverse) |
| HR_ADMIN_EST / HR_MAKER_EST | (reserved — establishment prepare/approve; no live endpoint gated by these yet) | office/region | ADMIN=yes, MAKER=no | None |
| FIN_ADMIN | (reserved — general finance; no permissions seeded yet, `financial_authority=true` in `roles` table) | ALL_JCI | — | **Yes (reserved)** |
| FIN_MAKER_CPF | `CPF_VIEW`, `CPF_PREPARE` | ALL_JCI (typical) | No (maker) | None |
| **FIN_ADMIN_CPF** | `CPF_VIEW`, `CPF_SANCTION`, `CPF_DISBURSE`, `CPF_REJECT`, `CPF_INTEREST_RUN`, `CPF_INTEREST_POST`, `CPF_INTEREST_REVERSE`, `CPF_SETTLEMENT_APPROVE` | ALL_JCI (typical) | Yes | **Yes** |
| FIN_MAKER_DISB | `DISBURSEMENT_PREPARE` | ALL_JCI (typical) | No (maker) | None |
| **FIN_ADMIN_DISB** | `DISBURSEMENT_AUTHORIZE`, `DISBURSEMENT_REVERSE` | ALL_JCI (typical) | Yes | **Yes** |
| JCIECCS_MAKER | `JCIECCS_VIEW`, `JCIECCS_PREPARE` | ALL_JCI (typical) | No (maker) | None |
| **JCIECCS_ADMIN** | `JCIECCS_VIEW`, `JCIECCS_APPROVE`, `JCIECCS_REVERSE`, `JCIECCS_RECONCILE`, `JCIECCS_SETTLE` | ALL_JCI (typical) | Yes | **Yes** |
| IT_ADMIN_STORE / IT_MAKER_STORE | (reserved — `STORE_VIEW/PREPARE/APPROVE` permissions defined in `RBAC_SECURITY_REQUIREMENTS.md` catalogue but not yet seeded — no live Store module exists in this codebase to gate) | office/region | ADMIN=yes, MAKER=no | None |

**Bold** rows = roles carrying real financial checker authority. Note the visual gap: **`SYSTEM_ADMIN` has no bold row** — it is the only "ADMIN"-named role in the entire table with zero financial permissions, which is the concrete evidence closing SEC-010 for the new model.

## Permission catalogue actually wired to a live `@PreAuthorize` check this phase

| Permission | Enforced at |
|---|---|
| `USER_INVITE` | `AdminOnboardingController.preview/previewBulk/invite/bulkInvite` |
| `USER_INVITE_RESEND` | `AdminOnboardingController.resend` |
| `USER_INVITE_REVOKE` | `AdminOnboardingController.revoke` |
| `USER_ROLE_ASSIGN` | `AdminUserRoleController` (class-level) |

All other seeded permissions (`CPF_*`, `PAYROLL_*`, `JCIECCS_*`, `DISBURSEMENT_*`, `ATTENDANCE_*`, `LEAVE_*`) are **defined in the catalogue and ready to gate endpoints** but the corresponding controllers were **not** migrated off their legacy `hasAnyRole(...)` checks this phase (see `RBAC_MIGRATION_REPORT.md`) — reserved for the next incremental migration pass, endpoint by endpoint, per RBAC_SECURITY_REQUIREMENTS.md's explicit "do not assume these mappings cover every endpoint" instruction.
