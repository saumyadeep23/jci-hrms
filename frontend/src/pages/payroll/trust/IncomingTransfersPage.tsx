import { useState } from 'react'
import { Plus } from 'lucide-react'
import { PageHeader, PrimaryButton } from '../../../components/common/ui'
import { IncomingFundTransferModal } from './IncomingFundTransferModal'
import { IncomingTransfersQueueTable } from './IncomingTransfersQueueTable'

/** Route: /payroll/trust/incoming-transfers - Section 3 of the CPF Trust ingestion task. */
export function IncomingTransfersPage() {
  const [creating, setCreating] = useState(false)

  return (
    <div>
      <PageHeader
        title="Incoming Fund Transfers"
        description="Prior-service PF & Pension corpus transfer-in vouchers - submit, verify, and credit to the CPF Trust ledger"
        actions={
          <PrimaryButton onClick={() => setCreating(true)}>
            <Plus size={15} /> New Transfer
          </PrimaryButton>
        }
      />

      <IncomingTransfersQueueTable />

      {creating && <IncomingFundTransferModal onClose={() => setCreating(false)} />}
    </div>
  )
}
