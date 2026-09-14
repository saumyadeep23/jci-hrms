import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { apiClient } from '../../../api/client'
import { Badge, Card, ErrorState, LoadingState, PageHeader, PrimaryButton, SecondaryButton } from '../../../components/common/ui'
import { describeApiError } from '../../../lib/apiError'
import type { JciEccsIntegrityCheckResultResponse, JciEccsIntegrityCheckSeverity } from '../../../types/api'

function severityTone(severity: JciEccsIntegrityCheckSeverity): 'success' | 'warning' | 'danger' {
  switch (severity) {
    case 'CRITICAL':
      return 'danger'
    case 'WARNING':
      return 'warning'
    default:
      return 'success'
  }
}

function ResultsTable({ results }: { results: JciEccsIntegrityCheckResultResponse[] }) {
  if (results.length === 0) {
    return (
      <div className="rounded-md border border-dashed border-slate-300 bg-slate-50 p-6 text-center text-sm text-slate-500">
        No integrity findings - every checked figure matches.
      </div>
    )
  }
  return (
    <div className="overflow-x-auto rounded-md border border-slate-200 bg-white">
      <table className="w-full text-left text-sm">
        <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
          <tr>
            <th className="px-3 py-2">Severity</th>
            <th className="px-3 py-2">Check</th>
            <th className="px-3 py-2">Entity</th>
            <th className="px-3 py-2">Message</th>
            <th className="px-3 py-2">Expected</th>
            <th className="px-3 py-2">Actual</th>
            <th className="px-3 py-2">Detected</th>
          </tr>
        </thead>
        <tbody>
          {results.map((r, i) => (
            <tr key={`${r.checkType}-${r.entityType}-${r.entityId}-${i}`} className="border-b border-slate-100 align-top last:border-0">
              <td className="px-3 py-2">
                <Badge tone={severityTone(r.severity)}>{r.severity}</Badge>
              </td>
              <td className="px-3 py-2 text-slate-600">{r.checkType}</td>
              <td className="px-3 py-2 text-slate-600">
                {r.entityType} #{r.entityId}
              </td>
              <td className="px-3 py-2 text-slate-700">{r.message}</td>
              <td className="px-3 py-2 text-slate-600">{r.expectedValue}</td>
              <td className="px-3 py-2 text-slate-600">{r.actualValue}</td>
              <td className="px-3 py-2 text-[11px] text-slate-400">{new Date(r.detectedAt).toLocaleString('en-IN')}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

/** Route: /jcieccs/payroll-recovery/integrity-check - the admin-facing view of JciEccsIntegrityCheckService
 * (spec section 15-16/22). Every check here is on-demand and ephemeral: nothing is persisted by running a
 * check, and nothing here can repair a finding - that's deliberate, matching the backend's own javadoc
 * ("it returns findings, it never repairs anything"). Genuine repairs go through Deduction Reconciliation's
 * explicit, remarked resolution actions instead. */
export function IntegrityCheckPage() {
  const [loanId, setLoanId] = useState('')
  const [recoveryId, setRecoveryId] = useState('')
  const [payrollRunId, setPayrollRunId] = useState('')
  const [results, setResults] = useState<JciEccsIntegrityCheckResultResponse[] | null>(null)
  const [lastRun, setLastRun] = useState<string | null>(null)

  const fullCheckMutation = useMutation({
    mutationFn: async () => (await apiClient.post<JciEccsIntegrityCheckResultResponse[]>('/jcieccs/integrity-check/run')).data,
    onSuccess: (data) => {
      setResults(data)
      setLastRun('Full System Check')
    },
  })

  const loanCheckMutation = useMutation({
    mutationFn: async () =>
      (await apiClient.get<JciEccsIntegrityCheckResultResponse[]>(`/jcieccs/integrity-check/loans/${loanId}`)).data,
    onSuccess: (data) => {
      setResults(data)
      setLastRun(`Loan #${loanId}`)
    },
  })

  const recoveryCheckMutation = useMutation({
    mutationFn: async () =>
      (await apiClient.get<JciEccsIntegrityCheckResultResponse[]>(`/jcieccs/integrity-check/recoveries/${recoveryId}`)).data,
    onSuccess: (data) => {
      setResults(data)
      setLastRun(`Recovery #${recoveryId}`)
    },
  })

  const payrollCheckMutation = useMutation({
    mutationFn: async () =>
      (await apiClient.post<JciEccsIntegrityCheckResultResponse[]>(`/jcieccs/integrity-check/payroll-runs/${payrollRunId}/run`)).data,
    onSuccess: (data) => {
      setResults(data)
      setLastRun(`Payroll Run ${payrollRunId}`)
    },
  })

  const anyPending =
    fullCheckMutation.isPending || loanCheckMutation.isPending || recoveryCheckMutation.isPending || payrollCheckMutation.isPending
  const anyError = fullCheckMutation.error ?? loanCheckMutation.error ?? recoveryCheckMutation.error ?? payrollCheckMutation.error

  const critical = (results ?? []).filter((r) => r.severity === 'CRITICAL').length
  const warning = (results ?? []).filter((r) => r.severity === 'WARNING').length
  const info = (results ?? []).filter((r) => r.severity === 'INFO').length

  return (
    <div>
      <PageHeader
        title="Integrity Checker"
        description="On-demand administrative checks across loans, recoveries and payroll collections - findings only, never auto-repaired"
      />

      <div className="mb-4 grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <Card className="p-4">
          <p className="mb-2 text-xs font-medium text-slate-600">Full System Check</p>
          <PrimaryButton disabled={anyPending} onClick={() => fullCheckMutation.mutate()}>
            {fullCheckMutation.isPending ? 'Running...' : 'Run Full Check'}
          </PrimaryButton>
        </Card>

        <Card className="p-4">
          <p className="mb-2 text-xs font-medium text-slate-600">Check a Loan</p>
          <div className="flex gap-2">
            <input
              value={loanId}
              onChange={(e) => setLoanId(e.target.value)}
              placeholder="Loan ID"
              className="w-24 rounded-md border border-slate-300 px-2 py-1.5 text-sm"
            />
            <SecondaryButton disabled={!loanId || anyPending} onClick={() => loanCheckMutation.mutate()}>
              Check
            </SecondaryButton>
          </div>
        </Card>

        <Card className="p-4">
          <p className="mb-2 text-xs font-medium text-slate-600">Check a Recovery</p>
          <div className="flex gap-2">
            <input
              value={recoveryId}
              onChange={(e) => setRecoveryId(e.target.value)}
              placeholder="Recovery ID"
              className="w-24 rounded-md border border-slate-300 px-2 py-1.5 text-sm"
            />
            <SecondaryButton disabled={!recoveryId || anyPending} onClick={() => recoveryCheckMutation.mutate()}>
              Check
            </SecondaryButton>
          </div>
        </Card>

        <Card className="p-4">
          <p className="mb-2 text-xs font-medium text-slate-600">Check a Payroll Run</p>
          <div className="flex gap-2">
            <input
              value={payrollRunId}
              onChange={(e) => setPayrollRunId(e.target.value)}
              placeholder="Payroll Run ID"
              className="w-28 rounded-md border border-slate-300 px-2 py-1.5 text-sm"
            />
            <SecondaryButton disabled={!payrollRunId || anyPending} onClick={() => payrollCheckMutation.mutate()}>
              Check
            </SecondaryButton>
          </div>
        </Card>
      </div>

      {anyError && <ErrorState message={describeApiError(anyError, 'Could not run this integrity check.')} />}
      {anyPending && <LoadingState label="Running integrity check..." />}

      {results && !anyPending && (
        <>
          <div className="mb-4 flex items-center gap-4">
            <p className="text-xs font-medium text-slate-600">{lastRun}</p>
            <div className="flex gap-2 text-xs">
              <Badge tone="danger">{critical} Critical</Badge>
              <Badge tone="warning">{warning} Warning</Badge>
              <Badge tone="success">{info} Info</Badge>
            </div>
          </div>
          <ResultsTable results={results} />
        </>
      )}
    </div>
  )
}
