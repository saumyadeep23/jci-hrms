import { Download, Loader2, Play, Send } from 'lucide-react'
import { Badge, Card, SecondaryButton } from '../../components/common/ui'
import type { PayrollBatchResponse } from '../../types/api'

const STATUS_TONE: Record<string, 'neutral' | 'success' | 'brand' | 'warning' | 'danger'> = {
  DRAFT: 'neutral',
  CALCULATED: 'brand',
  HR_FINALIZED: 'warning',
  FINANCE_APPROVED: 'warning',
  DISBURSED: 'success',
  REJECTED_TO_HR: 'danger',
  CANCELLED: 'danger',
}

interface PayrollBatchCardProps {
  batch: PayrollBatchResponse
  selected: boolean
  onSelect: () => void
  onCompute: () => void
  onFinalize: () => void
  onDisburse: () => void
  onOpenReports: () => void
  computing: boolean
  finalizing: boolean
  disbursing: boolean
}

/** One payroll batch summary card - action buttons each stop propagation so clicking one doesn't also re-trigger onSelect(). */
export function PayrollBatchCard({
  batch, selected, onSelect, onCompute, onFinalize, onDisburse, onOpenReports, computing, finalizing, disbursing,
}: PayrollBatchCardProps) {
  return (
    <Card className={`cursor-pointer transition-shadow hover:shadow-md ${selected ? 'ring-2 ring-brand-forest' : ''}`}>
      <div onClick={onSelect}>
        <div className="flex items-center justify-between">
          <p className="font-medium text-slate-800">
            {batch.salYear}-{String(batch.salMonth).padStart(2, '0')} (Batch #{batch.id}) · {batch.batchNo}
          </p>
          <Badge tone={STATUS_TONE[batch.status] ?? 'neutral'}>{batch.status}</Badge>
        </div>
        <p className="text-xs text-slate-400">
          {batch.batchType} · {batch.totalEmployees} employees · Net ₹{batch.totalNet.toFixed(2)}
        </p>
      </div>
      {/* stopPropagation here (not just relying on the buttons being outside the onClick div above) is
          defense-in-depth against a future layout change nesting this row inside that div. */}
      <div className="mt-3 flex flex-wrap gap-2" onClick={(e) => e.stopPropagation()}>
        {(batch.status === 'DRAFT' || batch.status === 'CALCULATED') && (
          <SecondaryButton onClick={onCompute} disabled={computing}>
            {computing ? <Loader2 size={14} className="animate-spin" /> : <Play size={14} />}
            Compute
          </SecondaryButton>
        )}
        {batch.status === 'CALCULATED' && (
          <SecondaryButton onClick={onFinalize} disabled={finalizing}>
            {finalizing ? <Loader2 size={14} className="animate-spin" /> : <Send size={14} />}
            Finalize
          </SecondaryButton>
        )}
        {(batch.status === 'HR_FINALIZED' || batch.status === 'FINANCE_APPROVED') && (
          <SecondaryButton onClick={onDisburse} disabled={disbursing}>
            {disbursing ? <Loader2 size={14} className="animate-spin" /> : <Send size={14} />}
            Disburse
          </SecondaryButton>
        )}
        <SecondaryButton onClick={onOpenReports}>
          <Download size={14} /> Reports
        </SecondaryButton>
      </div>
    </Card>
  )
}
