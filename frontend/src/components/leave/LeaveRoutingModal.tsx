import { useQuery } from '@tanstack/react-query'
import { CheckCircle2, Circle, Clock, XCircle } from 'lucide-react'
import { apiClient } from '../../api/client'
import { formatDate } from '../../lib/date'
import { Badge, ErrorState, LoadingState } from '../common/ui'
import { Modal } from '../common/Modal'
import type { LeaveApplicationResponse, LeaveRoutingActionResponse } from '../../types/api'

const WORKFLOW_STAGE_TONE: Record<string, 'neutral' | 'success' | 'warning' | 'danger'> = {
  SUBMITTED: 'warning',
  RECOMMENDED: 'warning',
  SANCTIONED: 'success',
  REJECTED: 'danger',
  CANCELLED: 'neutral',
}

interface Step {
  key: string
  title: string
  by: string | null
  designation?: string | null
  date: string | null
  remarks?: string | null
  kind: 'applied' | 'forwarded' | 'sanctioned' | 'rejected' | 'pending'
}

function stepIcon(kind: Step['kind']) {
  if (kind === 'sanctioned') return <CheckCircle2 className="text-emerald-600" size={18} />
  if (kind === 'rejected') return <XCircle className="text-red-600" size={18} />
  if (kind === 'pending') return <Clock className="text-amber-500" size={18} />
  return <Circle className="text-brand-forest" size={18} fill="currentColor" />
}

function buildSteps(application: LeaveApplicationResponse, history: LeaveRoutingActionResponse[]): Step[] {
  const submit = history.find((a) => a.actionType === 'SUBMIT')
  const steps: Step[] = [
    {
      key: 'applied',
      kind: 'applied',
      title: 'Applied',
      by: submit ? submit.actionByName : application.employeeCode,
      date: submit ? submit.createdAt : application.createdAt,
    },
  ]

  history
    .filter((a) => a.actionType === 'RECOMMEND_FORWARD')
    .forEach((a) => {
      steps.push({
        key: `forward-${a.id}`,
        kind: 'forwarded',
        title: `Forwarded / Recommended by ${a.actionByName}${a.forwardedToName ? ` to ${a.forwardedToName}` : ''}`,
        by: a.actionByName,
        designation: a.actionByDesignation,
        date: a.createdAt,
        remarks: a.remarks,
      })
    })

  const decision = history.find((a) => a.actionType === 'SANCTION' || a.actionType === 'REJECT')
  if (decision) {
    steps.push({
      key: 'decision',
      kind: decision.actionType === 'SANCTION' ? 'sanctioned' : 'rejected',
      title: decision.actionType === 'SANCTION' ? 'Sanctioned' : 'Rejected',
      by: decision.actionByName,
      designation: decision.actionByDesignation,
      date: decision.createdAt,
      remarks: decision.remarks,
    })
  } else if (application.status === 'PENDING_APPROVAL') {
    steps.push({
      key: 'pending',
      kind: 'pending',
      title: 'Pending with',
      by: application.currentAssignedToName,
      designation: application.currentAssignedToDesignation,
      date: null,
    })
  }

  return steps
}

/**
 * Read-only routing/audit timeline for one leave application - opened from LeavePage's "Look Up
 * Application" summary and from the sanctioning queue's Pending/History tabs. Steps are derived
 * from the leave_application_actions log (GET /v1/leaves/{id}/routing-history); an application
 * submitted before that log existed (V65) falls back to the application's own createdAt/employee
 * for the "Applied" step, since it has no SUBMIT action row.
 */
export function LeaveRoutingModal({ applicationId, onClose }: { applicationId: number; onClose: () => void }) {
  const applicationQuery = useQuery({
    queryKey: ['leave-application', applicationId],
    queryFn: async () => (await apiClient.get<LeaveApplicationResponse>(`/leave-applications/${applicationId}`)).data,
  })

  const historyQuery = useQuery({
    queryKey: ['leave-routing-history', applicationId],
    queryFn: async () => (await apiClient.get<LeaveRoutingActionResponse[]>(`/v1/leaves/${applicationId}/routing-history`)).data,
  })

  const isLoading = applicationQuery.isLoading || historyQuery.isLoading
  const isError = applicationQuery.isError || historyQuery.isError
  const application = applicationQuery.data
  const history = historyQuery.data

  return (
    <Modal title={`Routing History - Application #${applicationId}`} onClose={onClose} maxWidthClassName="max-w-lg">
      {isLoading && <LoadingState label="Loading routing history..." />}
      {isError && <ErrorState message="Could not load the routing history for this application." />}

      {application && history && (
        <>
          <div className="mb-4 flex flex-wrap items-center justify-between gap-2 rounded-md border border-slate-200 bg-slate-50 px-3 py-2">
            <div>
              <p className="text-sm font-medium text-slate-700">
                {application.employeeCode} · {application.leaveTypeCode}
              </p>
              <p className="text-xs text-slate-500">
                {formatDate(application.startDate)} to {formatDate(application.endDate)} ({application.totalDays} days)
              </p>
            </div>
            <div className="text-right">
              <Badge tone={WORKFLOW_STAGE_TONE[application.workflowStage] ?? 'neutral'}>
                {application.workflowStage.replace(/_/g, ' ')}
              </Badge>
              {application.status === 'PENDING_APPROVAL' && application.currentAssignedToName && (
                <p className="mt-1 text-[11px] text-slate-400">
                  Currently with {application.currentAssignedToName}
                  {application.currentAssignedToDesignation ? ` (${application.currentAssignedToDesignation})` : ''}
                </p>
              )}
            </div>
          </div>

          <ol className="relative space-y-6 border-l border-slate-200 pl-5">
            {buildSteps(application, history).map((step) => (
              <li key={step.key} className="relative">
                <span className="absolute -left-[27px] top-0 flex h-5 w-5 items-center justify-center rounded-full bg-white">
                  {stepIcon(step.kind)}
                </span>
                <p className="text-sm font-medium text-slate-800">{step.title}</p>
                {(step.by || step.designation) && (
                  <p className="text-xs text-slate-500">
                    {step.by ?? 'Unassigned'}
                    {step.designation ? `, ${step.designation}` : ''}
                  </p>
                )}
                {step.date && <p className="text-xs text-slate-400">{formatDate(step.date)}</p>}
                {step.remarks && <p className="mt-1 rounded-md bg-slate-50 px-2 py-1 text-xs text-slate-600">"{step.remarks}"</p>}
              </li>
            ))}
          </ol>
        </>
      )}
    </Modal>
  )
}
