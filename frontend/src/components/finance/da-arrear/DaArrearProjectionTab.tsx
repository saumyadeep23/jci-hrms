import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Printer, Save } from 'lucide-react'
import { daArrearApi } from './daArrearApi'
import { DaSimulationForm, emptySimulationForm, type DaSimulationFormState } from './DaSimulationForm'
import { DaProjectionSummaryCards } from './DaProjectionSummaryCards'
import { EmployeeArrearScheduleTable } from './EmployeeArrearScheduleTable'
import { RolloverConfirmationModal } from './RolloverConfirmationModal'
import { useAuth } from '../../../auth/AuthContext'
import { useToast } from '../../common/ToastProvider'
import { describeApiError } from '../../../lib/apiError'
import { Card, ErrorState, LoadingState, PrimaryButton, SecondaryButton } from '../../common/ui'
import type { IdaProjectionSummaryResponse } from '../../../types/api'

interface RolloverBefore {
  expectedDrawalMonth: number
  expectedDrawalYear: number
  retroMonthsCount: number
}

type CommitResult =
  | { rolloverHappened: true; before: RolloverBefore; rolled: IdaProjectionSummaryResponse }
  | { rolloverHappened: false; committed: IdaProjectionSummaryResponse }

/**
 * CPSE IDA Enhancement / Arrear Projection workflow - Tab 2 of the DA Rate Manager. Owns every piece of
 * state across the sub-components (form, current batch, rollover interceptor) so switching to Tab 1 and
 * back never loses an in-progress simulation - only unmounting this component (which DaRateHistoryPage
 * never does while just switching tabs, see its own comment) would reset it.
 */
export function DaArrearProjectionTab({ onCommitted }: { onCommitted: () => void }) {
  const { hasRole } = useAuth()
  const { show } = useToast()
  const queryClient = useQueryClient()
  const canCommit = hasRole('HR_ADMIN', 'SUPER_ADMIN')

  const [form, setForm] = useState<DaSimulationFormState>(emptySimulationForm())
  const [batch, setBatch] = useState<IdaProjectionSummaryResponse | null>(null)
  const [rollover, setRollover] = useState<{ before: RolloverBefore; after: IdaProjectionSummaryResponse } | null>(null)

  const employeesQuery = useQuery({
    queryKey: ['ida-projection-employees', batch?.id],
    queryFn: () => daArrearApi.getProjectionEmployees(batch!.id),
    enabled: batch !== null,
  })

  const simulateMutation = useMutation({
    mutationFn: () =>
      daArrearApi.runSimulation({
        scaleType: form.scaleType,
        newDaRate: Number(form.newDaRate),
        effectiveFrom: form.effectiveFrom,
        drawalMonth: Number(form.drawalMonth),
        drawalYear: Number(form.drawalYear),
      }),
    onSuccess: (result) => {
      setBatch(result)
      show({ tone: 'success', message: `Simulation complete - Projection #${result.projectionCode}` })
    },
  })

  const saveDraftMutation = useMutation({
    mutationFn: () => daArrearApi.getProjectionBatch(batch!.id),
    onSuccess: (result) => {
      setBatch(result)
      show({ tone: 'success', message: `Draft simulation #${result.projectionCode} is saved.` })
    },
  })

  const commitMutation = useMutation({
    mutationFn: async (): Promise<CommitResult> => {
      if (!batch) throw new Error('No simulation to commit')
      const before: RolloverBefore = {
        expectedDrawalMonth: batch.expectedDrawalMonth,
        expectedDrawalYear: batch.expectedDrawalYear,
        retroMonthsCount: batch.retroMonthsCount,
      }
      await daArrearApi.createSanctionOrder({
        scaleType: form.scaleType,
        effectiveFrom: form.effectiveFrom,
        daPercentage: Number(form.newDaRate),
        active: true,
        orderNumber: form.orderNumber || null,
        orderDate: form.orderDate || null,
        remarks: form.remarks || null,
      })
      const rolled = await daArrearApi.checkRollover(batch.id)
      const rolloverHappened =
        rolled.expectedDrawalMonth !== before.expectedDrawalMonth || rolled.expectedDrawalYear !== before.expectedDrawalYear
      if (rolloverHappened) {
        return { rolloverHappened: true, before, rolled }
      }
      const committed = await daArrearApi.commitOrder(batch.id)
      return { rolloverHappened: false, committed }
    },
    onSuccess: (result) => {
      if (result.rolloverHappened) {
        setBatch(result.rolled)
        setRollover({ before: result.before, after: result.rolled })
      } else {
        finishCommit(result.committed)
      }
    },
  })

  const confirmRolloverMutation = useMutation({
    mutationFn: () => daArrearApi.commitOrder(rollover!.after.id),
    onSuccess: (committed) => {
      setRollover(null)
      finishCommit(committed)
    },
  })

  function finishCommit(committed: IdaProjectionSummaryResponse) {
    queryClient.invalidateQueries({ queryKey: ['da-rates'] })
    show({
      tone: 'success',
      message: `DA Sanction Order issued - ${committed.scaleType} now ${committed.newDaRate.toFixed(2)}% (Projection #${committed.projectionCode} committed).`,
    })
    setBatch(null)
    setForm(emptySimulationForm())
    onCommitted()
  }

  const activeError = simulateMutation.error ?? commitMutation.error ?? confirmRolloverMutation.error
  const canIssueOrder =
    !commitMutation.isPending && batch !== null && batch.status !== 'ORDER_COMMITTED' && form.orderNumber.trim() !== '' && form.orderDate !== ''

  return (
    <div>
      <DaSimulationForm
        form={form}
        onChange={setForm}
        onRunSimulation={() => simulateMutation.mutate()}
        isSimulating={simulateMutation.isPending}
        disabled={batch !== null && batch.status === 'ORDER_COMMITTED'}
      />

      {batch && (
        <div className="mt-6">
          <DaProjectionSummaryCards batch={batch} employees={employeesQuery.data ?? []} />
          {employeesQuery.isLoading && <LoadingState label="Loading employee schedules..." />}
          {employeesQuery.isError && <ErrorState message="Could not load the employee arrear schedule." />}
          {employeesQuery.data && <EmployeeArrearScheduleTable employees={employeesQuery.data} />}

          <Card className="sticky bottom-0 z-10 mt-6">
            <div className="flex flex-wrap justify-end gap-2">
              <SecondaryButton onClick={() => saveDraftMutation.mutate()} disabled={saveDraftMutation.isPending}>
                <Save size={14} /> Save Draft Simulation
              </SecondaryButton>
              <SecondaryButton onClick={() => window.print()}>
                <Printer size={14} /> Print / Export Board Note
              </SecondaryButton>
              {canCommit && (
                <PrimaryButton onClick={() => commitMutation.mutate()} disabled={!canIssueOrder}>
                  Issue DA Sanction Order &amp; Commit to Payroll
                </PrimaryButton>
              )}
            </div>
            {!canCommit && (
              <p className="mt-2 text-right text-xs text-slate-400">Issuing the sanction order requires HR Admin.</p>
            )}
            {canCommit && batch.status !== 'ORDER_COMMITTED' && (form.orderNumber.trim() === '' || form.orderDate === '') && (
              <p className="mt-2 text-right text-xs text-amber-600">DPE Order Number and Order Date (above) are required to commit.</p>
            )}
          </Card>

          {activeError && (
            <div className="mt-3">
              <ErrorState message={describeApiError(activeError, 'Could not complete the requested action.')} />
            </div>
          )}
        </div>
      )}

      {rollover && (
        <RolloverConfirmationModal
          before={rollover.before}
          after={rollover.after}
          isPending={confirmRolloverMutation.isPending}
          onConfirm={() => confirmRolloverMutation.mutate()}
          onCancel={() => setRollover(null)}
        />
      )}
    </div>
  )
}
