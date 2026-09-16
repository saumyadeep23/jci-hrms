# Pre-RBAC Baseline Repair

## 1. Commit SHA Tested

Starting SHA: `6067227b40c679d9be83ad1f709e818d93405b71` ("feat(jcieccs): Phase 5 production hardening - concurrency, invariants, traceability"), working tree clean.

## 2. Original Eight Failing/Erroring Tests

All eight were **errors** (not assertion failures) on the full backend suite:

| # | Test | Location |
|---|---|---|
| 1 | `CpfLoanApplicationServiceTest.monthlyPayrollRecoveryHook_principalPhaseClears_flipsToInterestPhaseWithoutClosing` | line 240 (`payrollBatchRepository.save(...)`) |
| 2-8 | `PayrollBatchCascadeAndReportsTest.setUp` | line 104 (`payrollBatchRepository.save(...)`), one error per one of the class's 7 `@Test` methods, since `setUp()` runs before every method and threw every time |

## 3. Exact Exceptions

```
org.springframework.dao.DataIntegrityViolationException:
could not execute statement [ERROR: duplicate key value violates unique constraint "uq_payroll_batch_month_year"
  Detail: Key (sal_month, sal_year)=(7, 2026) already exists.] ...          <- CpfLoanApplicationServiceTest
```

```
org.springframework.dao.DataIntegrityViolationException:
could not execute statement [ERROR: duplicate key value violates unique constraint "uq_payroll_batch_month_year"
  Detail: Key (sal_month, sal_year)=(8, 2026) already exists.] ...          <- PayrollBatchCascadeAndReportsTest (x7)
```

SQLSTATE: `23505` (unique_violation). Constraint: `uq_payroll_batch_month_year`.

## 4. Root Cause — The Mechanism, Not Just the Symptom

`uq_payroll_batch_month_year` is `UNIQUE (sal_month, sal_year)` on `payroll_batches`, defined in `V69__payroll_computation_engine_accommodation_heads.sql` (line 32). This is the **correct, intentional** production invariant: one payroll batch globally per salary month/year — confirmed by the `PayrollBatch` entity (no office/region/type column feeds the batch's identity; `batch_type` REGULAR/SUPPLEMENTARY is explicitly *not* part of the unique key), by `PayrollBatchService.create()` (which checks-then-inserts on exactly `(salMonth, salYear)` and throws `MasterDataConflictException` on a real duplicate), and by there being no evidence anywhere in the codebase — entity, repository, service, controller, or any existing test — of a per-office/per-region batching model. **The constraint was never the problem and was not touched.**

The actual cause: two real rows already existed in the shared local dev Postgres database (`jci-hrms-local-db`, `jcihrms`) *before either test ran*:

```
 id  |  batch_no  | sal_month | sal_year | status     | created_at
-----+------------+-----------+----------+------------+-------------------------------
 166 | PB-2026-07 |         7 |     2026 | DRAFT      | 2026-09-09 13:07:10.406949+00
 167 | PB-2026-08 |         8 |     2026 | CALCULATED | 2026-09-09 13:07:28.256415+00
```

`batch_no` format `"PB-" + salYear + "-" + %02d(salMonth)` matches `PayrollBatchService.create()`'s own generator exactly (`src/main/java/in/gov/jci/hrms/service/PayrollBatchService.java:45`) — these are **real rows created through the live application** (no automated test in this repository ever calls `PayrollBatchService`; no `PayrollBatchController`/`PayrollBatchServiceTest` exists), most plausibly from earlier interactive/manual verification against this same local dev database, six days before this repair. They are not test fixtures and were never cleaned up because nothing in the test suite owns them.

`CpfLoanApplicationServiceTest` (line 240, pre-fix) and `PayrollBatchCascadeAndReportsTest` (line 104, pre-fix) each independently hardcoded `(7, 2026)` / `(8, 2026)` — values close to "now" that a developer manually exercising "this month's batch" through the running app would very plausibly have already created. Both test classes *are* correctly `@Transactional` (roll back after every method — verified: their own fixtures never leaked into each other), so this is not test-order-dependence, incomplete test cleanup, a sequence/identity bug, or a Flyway/schema mismatch. It is **fixture fragility against a shared, persistent database**: a hardcoded near-current-date collided with real, unrelated, already-committed data.

Six *other* test classes referencing the same month/year literals (`DeputationLifecycleTest`, `IdaArrearComputationServiceTest`, `IdaProjectionEngineTest`, `PayrollBatchComputationServiceTest`, `PayrollBatchEditServiceTest`, `SuspensionLifecycleTest`) were checked and ruled out: all six are `@ExtendWith(MockitoExtension.class)` pure-mock unit tests with `@Mock` repositories — they never touch Postgres, so they could not have created rows 166/167 and are not part of this root cause.

**Classification: B — shared database contamination**, compounded by fragile test fixtures that assumed exclusive ownership of a near-"current" month/year on a database they don't actually control. Not A/C/D/E/F/G/H/I/J.

### A second, previously-masked issue

Once `setUp()` was fixed, `PayrollBatchCascadeAndReportsTest` revealed two further failures that had *always* been present but were masked because `setUp()` never got far enough to run any test body:

1. **`editBasicPay_cascadesDaHraCpfEpfJcpfAndLogsEveryChangedHead`** — asserted `l.getHeadCount() == HEAD_BASIC` (1) implies reason `"Correction per revised pay order"`. `payroll_edit_logs.head_count` carries no salary-vs-statutory namespace flag, and `HEAD_BASIC` (salary head 1, "Basic Pay") and `STAT_HEAD_EMPLOYER_EPF` (statutory head 1, "Employer EPF") are numerically identical by coincidence of two independent numbering schemes. The assertion's filter caught both rows; the EPF cascade row correctly carries `"Cascaded from Basic Pay modification"`, not the officer's own reason. This is a **test-assertion defect**, not an application defect — every computed financial value in the same test (gross, deductions, net, CPF, EPF, Pension, JCPF) was already correct.
2. **`cpfSchedule_reflectsEeAndErShareAfterEdit`** — asserted `report.rows().get(0).get("EEShare")).isEqualTo(new BigDecimal("8424.00"))` using AssertJ's scale-sensitive `isEqualTo` (`BigDecimal#equals`), while `PayrollBatchEditService` persists cascaded amounts via `round()` (scale 0), and this `@Transactional` test reads them back from the *same* persistence context without a fresh fetch, so the in-memory value legitimately carries scale 0 (`8424`) rather than the column's declared `NUMERIC(12,2)` scale (`8424.00`) a cross-transaction read would normalize to. Every other BigDecimal assertion in this same file (17 of them) already correctly uses `isEqualByComparingTo` — this was a lone inconsistency.

Both are genuine **test-code defects** (G ruled out for application code specifically — the application's computed values were correct in both cases), fixed at the test level only.

## 5. Fix

**Files changed (test code only — no production code, no schema, no migration):**

- `backend/src/test/java/in/gov/jci/hrms/service/CpfLoanApplicationServiceTest.java` — moved the one stray `(7, 2026)` fixture to `(7, 2050)` (matching the file's own pre-existing `SANCTION_DATE = LocalDate.of(2050, 6, 15)` far-future convention, with its own comment already explaining exactly this class of collision risk for a *different* seeded row).
- `backend/src/test/java/in/gov/jci/hrms/service/PayrollBatchCascadeAndReportsTest.java`:
  - Moved `(8, 2026)` to `(8, 2085)` for the batch and its `PayrollMonthlyRecord` (financial year string updated to match: `"2085-2086"`). The `DaRateHistory`/`PayrollHraRate` seed rows were deliberately left at `2026-08-15` — verified their lookup queries (`findTopByScaleTypeAndActiveTrueAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc`, and the HRA range query) have no staleness upper bound, so they still resolve correctly as "the most recent applicable rate" for the far-future batch period.
  - Fixed the `editBasicPay...` test's log-filter to also pin on `newAmount` (`60000.00`, unique to the Basic Pay line among all 8 logged changes), isolating it from the numerically-coincident EPF statutory row, and added an explicit `hasSize(1)` to make the disambiguation self-documenting.
  - Fixed the `cpfSchedule...` test's two BigDecimal assertions from `isEqualTo` to `isEqualByComparingTo`, matching this file's own established (17-instance) convention.

**Why this is the correct, minimal fix:** it does not touch `uq_payroll_batch_month_year`, `PayrollBatch`, `PayrollBatchService`, `PayrollBatchEditService`, or any other production code; it does not delete the two real rows on the shared dev database (they may still be in active manual use by someone testing "this month's" payroll flow, and the fix works whether or not they exist); it follows the exact idiom this codebase already uses elsewhere for real-DB integration tests (`CpfLoanConcurrencyTest` uses year 2056; this file's own `SANCTION_DATE` already uses 2050 with an explicit collision-avoidance comment); and it fixes the two newly-surfaced test-assertion bugs at the assertion level only, since the underlying computed values were already financially correct.

## 6. Database/Migration Impact

**None.** No migration added, no migration modified, no schema change, no row deleted, no row inserted, no destructive operation of any kind performed against the database. Rows 166/167 remain exactly as found.

## 7. Tests Added/Changed

No tests added. Three existing test bodies edited (fixture values in 2, assertion precision in 2, within the same file) as described above. No test was disabled, skipped, or had its assertions weakened to merely pass.

## 8. Final Full-Suite Result

**1457 tests run, 0 failures, 0 errors, 0 skipped** (aggregated from `target/surefire-reports/*.txt` across 193 report files).

## 9. Frontend Result

`tsc -b --force`: pass. `oxlint`: pass (pre-existing warnings only, in files untouched by this repair; zero errors). `npm run build`: pass. No frontend test script is configured in this project (`package.json` defines `build`/`lint` only, no `test`).

## 10. JCIECCS Result

JCIECCS-scoped suite (`JciEccs*Test`, `ExitClearanceServiceTest`, `TerminalSettlementServiceTest`, `CycleResolverServiceTest`) run alongside both repaired classes: **106/106 passing**, zero regressions, Phase 5 concurrency controls (pessimistic locks, lock ordering, deadlock retry, idempotency keys) untouched.

## 11. Remaining Known Failures

None. The full suite is green.

## 12. Known Findings Not Fixed (Out of Scope, Recorded)

- **P2** — `PayrollBatchService.create()` performs check-then-insert (`findBySalMonthAndSalYear` then save) with no pessimistic lock; a genuine TOCTOU race under concurrent batch creation would still surface as the same `DataIntegrityViolationException` rather than the service's own `MasterDataConflictException`. The unique constraint remains the correct final backstop either way — no financial corruption risk, only an imprecise error type under a rare concurrent-creation race. Not part of the original eight errors; not fixed here per task scope (section 17: "document it separately rather than expanding the task").
- **P3** — `payroll_edit_logs.head_count` has no column distinguishing salary-head vs statutory-head numbering, and the two schemes can coincide (Head 1 "Basic Pay" vs Statutory Head 1 "Employer EPF" in this codebase's own numbering). This only matters for a future report/UI built directly against raw `head_count` filtering; no current code path is affected, and no financial value is wrong. Worth a column (e.g., `head_kind`) if such a report is ever built — not attempted here per task scope.
- **P3** — Real, manually-created rows 166 (`PB-2026-07`) and 167 (`PB-2026-08`) remain on the shared local dev database. Left untouched deliberately (see Fix rationale above); flagging only so a future developer isn't surprised to find them.
- **P3** — The standalone `flyway:info`/`flyway:validate` Maven CLI goals are not configured with connection properties for this project and additionally hit an unrelated JVM/OS timezone-name mismatch (`Asia/Calcutta` not recognized by the PgJDBC driver as a valid Postgres `TimeZone` GUC value) when invoked outside the Spring Boot application context. Spring Boot's own embedded Flyway run is the project's actual validation path and passed cleanly on every one of the dozens of test-suite runs performed during this repair (`Successfully validated 93 migrations`, `Schema "public" is up to date`). Not a migration defect; a pre-existing standalone-tooling gap, unrelated to this task.
