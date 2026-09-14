import { PageHeader } from '../../../components/common/ui'

/**
 * Route: /jcieccs/members/thrift-ledger. Per-member thrift transaction history
 * (jcieccs_thrift_transaction: CONTRIBUTION/ADJUSTMENT/REFUND/OPENING_BALANCE, ₹500-₹5,000 monthly
 * subscription tracking) has no read endpoint on the backend yet - JciEccsThriftTransactionRepository
 * exists but is only consumed internally by JciEccsDebitConfirmationService, not exposed via any
 * controller. This page is a placeholder until a GET /api/jcieccs/members/{employeeId}/thrift-ledger
 * (or similar) endpoint is added.
 */
export function ThriftLedgerPage() {
  return (
    <div>
      <PageHeader title="Thrift Fund Ledger" description="Monthly thrift subscription (₹500-₹5,000) contribution history per member" />
      <div className="rounded-md border border-dashed border-slate-300 bg-slate-50 p-8 text-center text-sm text-slate-500">
        No backend endpoint exposes the thrift transaction ledger yet. The underlying data
        (<code className="rounded bg-slate-200 px-1 py-0.5 text-xs">jcieccs_thrift_transaction</code>) is
        posted correctly by payroll debit confirmation, but nothing serves it to the frontend yet -
        add a read endpoint on <code className="rounded bg-slate-200 px-1 py-0.5 text-xs">JciEccsMemberController</code> (or a
        new controller) to complete this page.
      </div>
    </div>
  )
}
