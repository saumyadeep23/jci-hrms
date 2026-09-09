import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, X } from 'lucide-react'
import { apiClient } from '../../../api/client'
import { useAuth } from '../../../auth/AuthContext'
import { formatDate } from '../../../lib/date'
import { describeApiError } from '../../../lib/apiError'
import { DateField } from '../../../components/common/DateField'
import { Badge, Card, EmptyState, ErrorState, LoadingState, PageHeader, PrimaryButton } from '../../../components/common/ui'
import type { CpfStatutoryInterestRateRequest, CpfStatutoryInterestRateResponse } from '../../../types/api'

const EMPTY_FORM = {
  finYear: '',
  baseCpfRate: '',
  loanMarkupRate: '1.00',
  ministryOrderNo: '',
  orderDate: '',
}

/**
 * The notification master CpfRateResolutionService's Para 60(2) fallback reads from - notifying a rate
 * here is what makes it available to the CPF passbook's dynamic accrual and interim-settlement
 * crystallization. Every entry also lands in the generic audit-log trail automatically
 * (CpfStatutoryInterestRate implements Auditable) - the table below is this module's own business-level
 * notification history/log, visible to CPF_ADMIN/FINANCE_ADMIN without needing the SUPER_ADMIN/HR_ADMIN
 * -only /admin/audit-logs page.
 *
 * Backend only allows CPF_ADMIN/SUPER_ADMIN to POST (CpfStatutoryInterestRateController) - the Add button
 * is hidden for anyone else so a FINANCE_ADMIN (who can view this page per the route's role gate) doesn't
 * see an action that would 403 - same pattern as DaRateHistoryPage's own HR_ADMIN/SUPER_ADMIN gate.
 */
export function CpfInterestRateEntryPage() {
  const { hasRole } = useAuth()
  const canCreate = hasRole('CPF_ADMIN', 'SUPER_ADMIN')
  const queryClient = useQueryClient()
  const [modalOpen, setModalOpen] = useState(false)
  const [form, setForm] = useState(EMPTY_FORM)

  const { data, isLoading, isError } = useQuery({
    queryKey: ['cpf-statutory-interest-rates'],
    queryFn: async () => (await apiClient.get<CpfStatutoryInterestRateResponse[]>('/v1/payroll/trust/interest/rates')).data,
  })

  const createMutation = useMutation({
    mutationFn: async () => {
      const payload: CpfStatutoryInterestRateRequest = {
        finYear: form.finYear,
        baseCpfRate: Number(form.baseCpfRate),
        loanMarkupRate: Number(form.loanMarkupRate),
        ministryOrderNo: form.ministryOrderNo,
        orderDate: form.orderDate,
      }
      return (await apiClient.post<CpfStatutoryInterestRateResponse>('/v1/payroll/trust/interest/rates', payload)).data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['cpf-statutory-interest-rates'] })
      setModalOpen(false)
      setForm(EMPTY_FORM)
    },
  })

  const effectiveLoanRatePreview =
    form.baseCpfRate && form.loanMarkupRate ? (Number(form.baseCpfRate) + Number(form.loanMarkupRate)).toFixed(2) : null

  return (
    <div>
      <PageHeader
        title="CPF Rate of Interest Entry"
        description="Notify each financial year's statutory CPF interest rate - the Para 60(2), EPF Scheme fallback (an unnotified year uses the preceding year's rate provisionally) reads directly from this log."
        actions={
          canCreate && (
            <PrimaryButton onClick={() => setModalOpen(true)}>
              <Plus size={15} /> Notify Rate
            </PrimaryButton>
          )
        }
      />

      <Card>
        {isLoading && <LoadingState label="Loading CPF interest rate notifications..." />}
        {isError && <ErrorState message="Could not load the CPF interest rate log." />}
        {data && data.length === 0 && <EmptyState message="No CPF interest rate notified yet." />}

        {data && data.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[900px] border-collapse text-sm">
              <thead>
                <tr className="border-b border-slate-300 text-left text-slate-500">
                  <th className="py-2 pr-3">Financial Year</th>
                  <th className="py-2 pr-3 text-right">Base CPF Rate</th>
                  <th className="py-2 pr-3 text-right">Loan Markup</th>
                  <th className="py-2 pr-3 text-right">Effective Loan Rate</th>
                  <th className="py-2 pr-3">Ministry Order No</th>
                  <th className="py-2 pr-3">Order Date</th>
                  <th className="py-2 pr-3">Notified On</th>
                  <th className="py-2 pl-3">Status</th>
                </tr>
              </thead>
              <tbody>
                {data.map((rate) => (
                  <tr key={rate.id} className="border-b border-slate-100">
                    <td className="py-2 pr-3">
                      <Badge tone="brand">{rate.finYear}</Badge>
                    </td>
                    <td className="py-2 pr-3 text-right tabular-nums font-medium">{rate.baseCpfRate.toFixed(2)}%</td>
                    <td className="py-2 pr-3 text-right tabular-nums">{rate.loanMarkupRate.toFixed(2)}%</td>
                    <td className="py-2 pr-3 text-right tabular-nums">{rate.effectiveLoanRate.toFixed(2)}%</td>
                    <td className="py-2 pr-3 text-xs text-slate-500">{rate.ministryOrderNo}</td>
                    <td className="py-2 pr-3 text-xs text-slate-500">{formatDate(rate.orderDate)}</td>
                    <td className="py-2 pr-3 text-xs text-slate-400">{formatDate(rate.createdAt, 'dd-MM-yyyy hh:mm a')}</td>
                    <td className="py-2 pl-3">
                      <Badge tone={rate.isActive ? 'success' : 'neutral'}>{rate.isActive ? 'Active' : 'Inactive'}</Badge>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {modalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-xl bg-white p-6 shadow-xl">
            <div className="mb-4 flex items-center justify-between">
              <h2 className="text-lg font-semibold text-slate-800">Notify CPF Interest Rate</h2>
              <button onClick={() => setModalOpen(false)} className="text-slate-400 hover:text-slate-600">
                <X size={20} />
              </button>
            </div>
            <form
              className="space-y-3"
              onSubmit={(e) => {
                e.preventDefault()
                createMutation.mutate()
              }}
            >
              <div>
                <label className="mb-1 block text-xs font-medium text-slate-600">Financial Year</label>
                <input
                  required
                  value={form.finYear}
                  onChange={(e) => setForm({ ...form, finYear: e.target.value })}
                  placeholder="2026-2027"
                  pattern="^[0-9]{4}-[0-9]{4}$"
                  title='Must be in "YYYY-YYYY" form'
                  className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="mb-1 block text-xs font-medium text-slate-600">Base CPF Rate %</label>
                  <input
                    required
                    type="number"
                    step="0.01"
                    min="0"
                    value={form.baseCpfRate}
                    onChange={(e) => setForm({ ...form, baseCpfRate: e.target.value })}
                    className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                  />
                </div>
                <div>
                  <label className="mb-1 block text-xs font-medium text-slate-600">Loan Markup %</label>
                  <input
                    required
                    type="number"
                    step="0.01"
                    min="0"
                    value={form.loanMarkupRate}
                    onChange={(e) => setForm({ ...form, loanMarkupRate: e.target.value })}
                    className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                  />
                </div>
              </div>
              {effectiveLoanRatePreview && (
                <p className="text-xs text-slate-400">Effective loan rate will be {effectiveLoanRatePreview}% (base + markup).</p>
              )}

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="mb-1 block text-xs font-medium text-slate-600">Ministry Order No</label>
                  <input
                    required
                    value={form.ministryOrderNo}
                    onChange={(e) => setForm({ ...form, ministryOrderNo: e.target.value })}
                    className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                    placeholder="EPFO/RATIF/2026-27/01"
                  />
                </div>
                <DateField
                  label="Order Date"
                  required
                  value={form.orderDate}
                  onChange={(value) => setForm({ ...form, orderDate: value })}
                />
              </div>

              <PrimaryButton type="submit" disabled={createMutation.isPending} className="w-full justify-center">
                Save Notified Rate
              </PrimaryButton>
              {createMutation.isError && (
                <ErrorState message={describeApiError(createMutation.error, 'Could not save the CPF interest rate.')} />
              )}
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
