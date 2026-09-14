import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { BookOpen, ShieldAlert } from 'lucide-react'
import { apiClient } from '../../../../api/client'
import { useAuth } from '../../../../auth/AuthContext'
import { formatDate } from '../../../../lib/date'
import { Modal } from '../../../../components/common/Modal'
import { JsonDiff } from '../../../../components/common/JsonDiff'
import { Badge, Card, EmptyState, ErrorState, LoadingState, SecondaryButton } from '../../../../components/common/ui'
import type { AuditLogResponse, CpfTrustMemberResponse, Page } from '../../../../types/api'
import { EpsEligibilityPanel } from './EpsEligibilityPanel'
import { formatInr, SETTLEMENT_STATUS_LABELS, settlementStatusTone } from './cpfMemberUtils'

export type CpfMemberDrawerTab = 'overview' | 'balance' | 'settlement' | 'uan' | 'eps' | 'documents' | 'audit'

const TABS: { key: CpfMemberDrawerTab; label: string }[] = [
  { key: 'overview', label: 'Overview' },
  { key: 'balance', label: 'CPF Balance' },
  { key: 'settlement', label: 'Settlement' },
  { key: 'uan', label: 'UAN' },
  { key: 'eps', label: 'EPS Eligibility' },
  { key: 'documents', label: 'Documents' },
  { key: 'audit', label: 'Audit Trail' },
]

/** Section 17 - the Member 360 drawer. Built on the same Modal-as-drawer pattern this codebase already uses for Employee360Drawer, rather than introducing a new sliding side-panel primitive. */
export function CpfMemberDrawer({
  member,
  initialTab = 'overview',
  onClose,
  onOpenLedger,
  onUpdateUan,
}: {
  member: CpfTrustMemberResponse
  initialTab?: CpfMemberDrawerTab
  onClose: () => void
  onOpenLedger: () => void
  onUpdateUan: () => void
}) {
  const [tab, setTab] = useState<CpfMemberDrawerTab>(initialTab)

  return (
    <Modal title={`${member.fullName} - ${member.cpfAcNo}`} onClose={onClose} maxWidthClassName="max-w-2xl">
      <div className="mb-4 flex flex-wrap items-center gap-2">
        <Badge tone={member.isSeparated ? 'neutral' : 'success'}>{member.status.replaceAll('_', ' ')}</Badge>
        {!member.uanNo && <Badge tone="warning">UAN Missing</Badge>}
      </div>

      <div className="mb-4 flex flex-wrap gap-2 border-b border-slate-100 pb-3">
        {TABS.map((t) => (
          <button
            key={t.key}
            type="button"
            onClick={() => setTab(t.key)}
            className={`rounded-full px-3 py-1 text-xs font-medium transition-colors ${
              tab === t.key ? 'bg-brand-forest text-white' : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === 'overview' && <OverviewTab member={member} onOpenLedger={onOpenLedger} />}
      {tab === 'balance' && <BalanceTab member={member} />}
      {tab === 'settlement' && <SettlementTab member={member} onOpenLedger={onOpenLedger} />}
      {tab === 'uan' && <UanTab member={member} onUpdateUan={onUpdateUan} />}
      {tab === 'eps' && <EpsEligibilityPanel employeeId={member.employeeId} />}
      {tab === 'documents' && <DocumentsTab />}
      {tab === 'audit' && <AuditTab member={member} />}
    </Modal>
  )
}

function Grid({ rows }: { rows: [string, string | number | null | undefined][] }) {
  return (
    <div className="grid grid-cols-2 gap-x-4 gap-y-1 text-sm">
      {rows.map(([label, val]) => (
        <div key={label} className="flex justify-between gap-2 border-b border-slate-50 py-1">
          <span className="text-slate-400">{label}</span>
          <span className="text-right text-slate-700">{val ?? '-'}</span>
        </div>
      ))}
    </div>
  )
}

function OverviewTab({ member, onOpenLedger }: { member: CpfTrustMemberResponse; onOpenLedger: () => void }) {
  return (
    <div className="space-y-4">
      <Grid
        rows={[
          ['Employee Code', member.employeeCode],
          ['UAN', member.uanNo ?? 'Missing'],
          ['Status', member.status.replaceAll('_', ' ')],
          ['Separation Date', member.separationDate ? formatDate(member.separationDate) : '-'],
        ]}
      />
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
        <StatTile label="CPF Balance" value={member.cpfBalance} />
        <StatTile label="Accrued Interest" value={member.accruedInterest} />
        <StatTile label="Total Payable" value={member.totalPayable} emphasize />
      </div>
      <SecondaryButton onClick={onOpenLedger}>
        <BookOpen size={14} /> Open Full Ledger
      </SecondaryButton>
    </div>
  )
}

function BalanceTab({ member }: { member: CpfTrustMemberResponse }) {
  return (
    <div className="space-y-4">
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
        <StatTile label="EE (Employee) Share" value={member.eeBalance} />
        <StatTile label="VPF" value={member.vpfBalance} />
        <StatTile label="ER (Employer) Share" value={member.erBalance} />
      </div>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <StatTile label="CPF Balance (EE + VPF + ER)" value={member.cpfBalance} />
        <StatTile label="Accrued Interest (current FY, not yet posted)" value={member.accruedInterest} />
      </div>
      <StatTile label="Total Payable" value={member.totalPayable} emphasize />
    </div>
  )
}

function SettlementTab({ member, onOpenLedger }: { member: CpfTrustMemberResponse; onOpenLedger: () => void }) {
  if (!member.isSeparated) {
    return <EmptyState message="Not applicable - this member is still in active service." />
  }
  return (
    <div className="space-y-4">
      <Grid
        rows={[
          ['Separation Date', formatDate(member.separationDate)],
          ['Settlement Status', SETTLEMENT_STATUS_LABELS[member.settlementStatus]],
          ['Settlement Due Date', member.settlementDueDate ? formatDate(member.settlementDueDate) : '-'],
          ['Settlement Date', member.settlementDate ? formatDate(member.settlementDate) : '-'],
        ]}
      />
      <div>
        <Badge tone={settlementStatusTone(member.settlementStatus)}>{member.settlementLagLabel}</Badge>
      </div>
      <SecondaryButton onClick={onOpenLedger}>
        <BookOpen size={14} /> View Settlement Transactions in Ledger
      </SecondaryButton>
    </div>
  )
}

function UanTab({ member, onUpdateUan }: { member: CpfTrustMemberResponse; onUpdateUan: () => void }) {
  return (
    <div className="space-y-4">
      {member.uanNo ? (
        <div className="rounded-md bg-slate-50 p-3 text-sm">
          <p className="text-xs text-slate-500">Universal Account Number</p>
          <p className="mt-0.5 font-medium tabular-nums text-slate-800">{member.uanNo}</p>
        </div>
      ) : (
        <div className="flex items-center gap-2 rounded-md border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">
          <ShieldAlert size={16} className="shrink-0" /> No UAN on record for this member.
        </div>
      )}
      <SecondaryButton onClick={onUpdateUan}>Update UAN</SecondaryButton>
    </div>
  )
}

function DocumentsTab() {
  return (
    <EmptyState message="No CPF Trust-specific document store exists yet in this system - this tab is a placeholder pending that backend capability." />
  )
}

function AuditTab({ member }: { member: CpfTrustMemberResponse }) {
  const { hasRole } = useAuth()
  const canView = hasRole('SUPER_ADMIN', 'HR_ADMIN')

  const { data, isLoading, isError } = useQuery({
    queryKey: ['cpf-member-audit-trail', member.employeeId],
    queryFn: async () =>
      (await apiClient.get<Page<AuditLogResponse>>('/audit-logs', { params: { entityName: 'Employee', entityId: member.employeeId, size: 20 } })).data,
    enabled: canView,
  })

  if (!canView) {
    return (
      <EmptyState message="Full audit trail is restricted to Super Admin / HR Admin. Ask an HR/Super Admin to check the Audit Trail Logs page for this member if needed." />
    )
  }
  if (isLoading) return <LoadingState label="Loading audit trail..." />
  if (isError) return <ErrorState message="Could not load the audit trail." />
  if (!data || data.content.length === 0) return <EmptyState message="No audit history recorded for this member yet." />

  return (
    <div className="max-h-[50vh] space-y-3 overflow-auto">
      {data.content.map((log) => (
        <Card key={log.id} className="p-3">
          <div className="mb-1 flex items-center justify-between text-xs text-slate-500">
            <span className="font-medium text-slate-700">{log.action}</span>
            <span>{formatDate(log.createdAt, 'dd-MM-yyyy hh:mm a')} · {log.actingUsername}</span>
          </div>
          <JsonDiff before={log.beforeState} after={log.afterState} />
        </Card>
      ))}
    </div>
  )
}

function StatTile({ label, value, emphasize = false }: { label: string; value: number; emphasize?: boolean }) {
  return (
    <div className={`rounded-md border p-3 ${emphasize ? 'border-brand-forest/40 bg-brand-forest/5' : 'border-slate-200'}`}>
      <p className="text-xs text-slate-500">{label}</p>
      <p className={`mt-1 text-lg font-semibold tabular-nums ${emphasize ? 'text-brand-forest' : 'text-slate-800'}`}>{formatInr(value)}</p>
    </div>
  )
}
