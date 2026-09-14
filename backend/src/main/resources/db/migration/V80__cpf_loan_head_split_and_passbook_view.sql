-- Head-wise CPF Trust loan/withdrawal accounting: a Non-Refundable Withdrawal (NRW) sanction must specify
-- how much comes out of each fund (EE/ER/VPF) rather than one lump sanctioned_amount, so the officer can
-- allocate it - see CpfLoanApplicationService.sanctionLoan()'s new validation (sum must equal
-- sanctioned_amount for NON_REFUNDABLE_WITHDRAWAL; both stay 0 for REFUNDABLE_LOAN, which always debits EE
-- only, spilling into VPF if EE is insufficient - unchanged from existing behavior).
ALTER TABLE cpf_loan_applications ADD COLUMN IF NOT EXISTS sanc_nrw_ee  NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE cpf_loan_applications ADD COLUMN IF NOT EXISTS sanc_nrw_er  NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE cpf_loan_applications ADD COLUMN IF NOT EXISTS sanc_nrw_vpf NUMERIC(12,2) NOT NULL DEFAULT 0;

-- Per-transaction diversion/loan-repayment amounts, and their own running balances (maintained the same
-- way running_ee_balance/running_er_balance/running_vpf_balance already are - carried forward from the
-- member's immediately preceding ledger row at write time, by whichever service posts the row, so they
-- stay correct across financial-year boundaries rather than resetting each FY):
--   sanc_cpf_loan               - this row's Refundable Loan disbursement (debits EE, see disburseLoan()).
--   sanc_nrw_ee/er/vpf          - this row's Non-Refundable Withdrawal disbursement, split by fund.
--   loan_repay_principal        - this row's Refundable Loan principal repayment (credits EE).
--   loan_repay_interest         - this row's Refundable Loan interest repayment (credits EE - Trust income
--                                 that is nonetheless folded back into the member's own EE corpus, per the
--                                 Member CPF Passbook's own accounting rule).
--   running_loan_cpf_balance    - outstanding Refundable Loan balance after this row (+sanc_cpf_loan,
--                                 -loan_repay_principal each row it applies to; unchanged otherwise).
--   running_nrw_ee/er/vpf_balance - cumulative Non-Refundable Withdrawals taken from each fund to date
--                                 (NRW is never repaid, so these only ever accumulate).
ALTER TABLE cpf_trust_member_ledger_entries ADD COLUMN IF NOT EXISTS sanc_cpf_loan            NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE cpf_trust_member_ledger_entries ADD COLUMN IF NOT EXISTS sanc_nrw_ee               NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE cpf_trust_member_ledger_entries ADD COLUMN IF NOT EXISTS sanc_nrw_er               NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE cpf_trust_member_ledger_entries ADD COLUMN IF NOT EXISTS sanc_nrw_vpf              NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE cpf_trust_member_ledger_entries ADD COLUMN IF NOT EXISTS loan_repay_principal       NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE cpf_trust_member_ledger_entries ADD COLUMN IF NOT EXISTS loan_repay_interest        NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE cpf_trust_member_ledger_entries ADD COLUMN IF NOT EXISTS running_loan_cpf_balance   NUMERIC(14,2) NOT NULL DEFAULT 0;
ALTER TABLE cpf_trust_member_ledger_entries ADD COLUMN IF NOT EXISTS running_nrw_ee_balance     NUMERIC(14,2) NOT NULL DEFAULT 0;
ALTER TABLE cpf_trust_member_ledger_entries ADD COLUMN IF NOT EXISTS running_nrw_er_balance     NUMERIC(14,2) NOT NULL DEFAULT 0;
ALTER TABLE cpf_trust_member_ledger_entries ADD COLUMN IF NOT EXISTS running_nrw_vpf_balance    NUMERIC(14,2) NOT NULL DEFAULT 0;

-- Member CPF Passbook read model - display_period is the "Mon-YYYY" period label (falls back to
-- value_date when sal_month/sal_year aren't populated, e.g. LOAN_WITHDRAWAL/ANNUAL_INTEREST rows).
-- remarks is deliberately omitted - the passbook display never shows it (see CpfPassbookResponseDto/
-- CpfPassbookView.tsx). All running balances (including the loan/NRW ones added above) are already
-- maintained as of-this-row columns by the write-side services, so this view is a plain projection with
-- no window functions needed.
CREATE OR REPLACE VIEW v_member_cpf_passbook AS
SELECT
    e.id                            AS id,
    e.employee_id                   AS employee_id,
    e.fin_year                      AS fin_year,
    e.sal_month                     AS sal_month,
    e.sal_year                      AS sal_year,
    e.value_date                    AS value_date,
    TO_CHAR(
        CASE WHEN e.sal_month IS NOT NULL AND e.sal_year IS NOT NULL
             THEN MAKE_DATE(e.sal_year, e.sal_month, 1)
             ELSE e.value_date
        END, 'Mon-YYYY')            AS display_period,
    e.entry_type                    AS entry_type,

    e.ee_share_credit               AS ee_share_credit,
    e.er_share_credit               AS er_share_credit,
    e.vpf_credit                    AS vpf_credit,

    e.sanc_cpf_loan                 AS sanc_cpf_loan,
    e.sanc_nrw_ee                   AS sanc_nrw_ee,
    e.sanc_nrw_er                   AS sanc_nrw_er,
    e.sanc_nrw_vpf                  AS sanc_nrw_vpf,

    e.loan_repay_principal          AS loan_repay_principal,
    e.loan_repay_interest           AS loan_repay_interest,

    e.running_ee_balance            AS running_ee_balance,
    e.running_er_balance            AS running_er_balance,
    e.running_vpf_balance           AS running_vpf_balance,
    e.running_total_balance         AS running_total_balance,

    e.running_loan_cpf_balance      AS running_loan_cpf_balance,
    e.running_nrw_ee_balance        AS running_nrw_ee_balance,
    e.running_nrw_er_balance        AS running_nrw_er_balance,
    e.running_nrw_vpf_balance       AS running_nrw_vpf_balance,

    e.loan_id                       AS loan_id,
    e.reference_doc_no              AS reference_doc_no
FROM cpf_trust_member_ledger_entries e;
