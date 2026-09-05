import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { AlertTriangle, ArrowRight, FileText } from 'lucide-react'
import { apiClient } from '../../api/client'
import { formatDate } from '../../lib/date'
import { openPdf } from '../../lib/pdfDownload'
import { SubmitJoiningReportModal } from '../../components/pims/SubmitJoiningReportModal'
import { Badge, Card, EmptyState, ErrorState, LoadingState, PageHeader, PrimaryButton, SecondaryButton } from '../../components/common/ui'
import type { EmployeeMovementRecordResponse } from '../../types/api'

export function MyTransfersPage() {
  const [submittingFor, setSubmittingFor] = useState<EmployeeMovementRecordResponse | null>(null)

  const { data, isLoading, isError } = useQuery({
    queryKey: ['movement-records-mine'],
    queryFn: async () => (await apiClient.get<EmployeeMovementRecordResponse[]>('/v1/pims/movements/records/mine')).data,
  })

  return (
    <div>
      <PageHeader title="My Transfers & Promotions" description="Movement orders affecting you, and joining report submission" />

      {isLoading && <LoadingState />}
      {isError && <ErrorState message="Could not load your movement records." />}
      {data && data.length === 0 && <EmptyState message="No transfer or promotion orders on record for you." />}

      {data && data.length > 0 && (
        <div className="space-y-3">
          {data.map((movement) => (
            <Card key={movement.id}>
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <p className="flex items-center gap-1.5 text-sm font-medium text-slate-800">
                    {movement.fromOfficeName} <ArrowRight size={13} className="text-slate-400" /> {movement.toOfficeName}
                  </p>
                  <p className="mt-0.5 text-xs text-slate-500">
                    {movement.orderType.replace(/_/g, ' ')} &middot; Order {movement.orderRefNo} &middot; {formatDate(movement.orderDate)}
                  </p>
                </div>
                <div className="flex flex-col items-end gap-1.5">
                  <Badge tone={movement.movementStatus === 'JOINED' ? 'success' : movement.movementStatus === 'RELIEVED' ? 'warning' : 'neutral'}>
                    {movement.movementStatus}
                  </Badge>
                  {movement.joiningStatus !== 'NOT_SUBMITTED' && (
                    <Badge
                      tone={
                        movement.joiningStatus === 'ACCEPTED'
                          ? 'success'
                          : movement.joiningStatus === 'REJECTED'
                            ? 'danger'
                            : movement.joiningStatus === 'CLARIFICATION_REQUESTED'
                              ? 'warning'
                              : 'brand'
                      }
                    >
                      {movement.joiningStatus.replace(/_/g, ' ')}
                    </Badge>
                  )}
                </div>
              </div>

              {movement.joiningStatus === 'CLARIFICATION_REQUESTED' && (
                <div className="mt-3 rounded-md border border-amber-300 bg-amber-50 p-3 text-xs text-amber-800">
                  <p className="flex items-center gap-1.5 font-semibold">
                    <AlertTriangle size={13} /> Joining Report Returned for Clarification
                  </p>
                  <p className="mt-1">{movement.clarificationRemarks}</p>
                </div>
              )}

              {movement.movementStatus === 'RELIEVED' && movement.joiningStatus === 'NOT_SUBMITTED' && (
                <div className="mt-3 flex justify-end">
                  <PrimaryButton onClick={() => setSubmittingFor(movement)}>Submit Joining Report</PrimaryButton>
                </div>
              )}

              {movement.joiningStatus === 'CLARIFICATION_REQUESTED' && (
                <div className="mt-3 flex justify-end">
                  <PrimaryButton onClick={() => setSubmittingFor(movement)}>Amend &amp; Re-submit Report</PrimaryButton>
                </div>
              )}

              {movement.joiningDate && (
                <p className="mt-3 text-xs text-slate-500">
                  Joined {formatDate(movement.joiningDate)} ({movement.joiningSession})
                  {movement.elCredited && <span className="ml-2 text-emerald-700">+{movement.elCreditedDays} Days EL Credited</span>}
                </p>
              )}

              <div className="mt-3 flex flex-wrap gap-2 border-t border-slate-100 pt-3">
                <SecondaryButton onClick={() => openPdf(`/v1/pims/movements/records/${movement.id}/my-documents/order-pdf`)}>
                  <FileText size={12} /> {movement.orderType === 'PROMOTION' ? 'Promotion' : 'Transfer'} Order
                </SecondaryButton>
                {movement.releaseDate && (
                  <SecondaryButton onClick={() => openPdf(`/v1/pims/movements/records/${movement.id}/my-documents/release-pdf`)}>
                    <FileText size={12} /> Release Order
                  </SecondaryButton>
                )}
                {movement.lpcIssueDate && (
                  <SecondaryButton onClick={() => openPdf(`/v1/pims/movements/records/${movement.id}/my-documents/lpc-pdf`)}>
                    <FileText size={12} /> Last Pay Certificate
                  </SecondaryButton>
                )}
              </div>
            </Card>
          ))}
        </div>
      )}

      {submittingFor && (
        <SubmitJoiningReportModal
          movement={submittingFor}
          isResubmission={submittingFor.joiningStatus === 'CLARIFICATION_REQUESTED'}
          onClose={() => setSubmittingFor(null)}
        />
      )}
    </div>
  )
}
