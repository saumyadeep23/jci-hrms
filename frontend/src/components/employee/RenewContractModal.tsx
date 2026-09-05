import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { formatDate } from '../../lib/date'
import { useToast } from '../common/ToastProvider'
import { describeApiErrorList, getFieldErrors } from '../../lib/apiError'
import { DatePicker } from '../common/DatePicker'
import { Modal } from '../common/Modal'
import { FormErrorBanner } from '../common/FormErrorBanner'
import { FieldError, PrimaryButton, errorInputClass } from '../common/ui'
import type {
  GradeScaleMasterResponse,
  Page,
  RenewContractRequest,
  RenewDeploymentRequest,
  VendorMasterResponse,
} from '../../types/api'

type RenewalType = 'CONTRACTUAL' | 'OUTSOURCED'

/** Renews a CONTRACTUAL employee's engagement or an OUTSOURCED employee's deployment (V50) - deactivates the current record and inserts the renewed terms as the new current one. */
export function RenewContractModal({ employeeId, onClose }: { employeeId: number; onClose: () => void }) {
  const { show } = useToast()
  const queryClient = useQueryClient()
  const [renewalType, setRenewalType] = useState<RenewalType>('CONTRACTUAL')

  const [monthlyAmount, setMonthlyAmount] = useState('')
  const [billingRate, setBillingRate] = useState('')
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [refNo, setRefNo] = useState('')
  const [scaleCode, setScaleCode] = useState('')
  const [vendorId, setVendorId] = useState('')
  const [engagementTerms, setEngagementTerms] = useState('')

  const gradeScalesQuery = useQuery({
    queryKey: ['grade-scales'],
    queryFn: async () => (await apiClient.get<GradeScaleMasterResponse[]>('/v1/masters/grade-scales')).data,
  })
  const vendorsQuery = useQuery({
    queryKey: ['vendors-all'],
    queryFn: async () => (await apiClient.get<Page<VendorMasterResponse>>('/v1/admin/masters/vendors', { params: { size: 200 } })).data.content,
    enabled: renewalType === 'OUTSOURCED',
  })

  const renewMutation = useMutation({
    mutationFn: async () => {
      if (renewalType === 'CONTRACTUAL') {
        const payload: RenewContractRequest = {
          monthlyLumpsum: Number(monthlyAmount),
          contractStartDate: formatDate(startDate),
          contractEndDate: formatDate(endDate),
          approvalRefNo: refNo,
          scaleCode: scaleCode || null,
          engagementTerms: engagementTerms || null,
        }
        return (await apiClient.post(`/v1/employees/${employeeId}/renew-contract`, payload)).data
      }
      const payload: RenewDeploymentRequest = {
        vendorId: vendorId ? Number(vendorId) : null,
        monthlyCtc: Number(monthlyAmount),
        agencyBillingRate: billingRate ? Number(billingRate) : null,
        deploymentStartDate: formatDate(startDate),
        deploymentEndDate: formatDate(endDate),
        workOrderRef: refNo,
        scaleCode: scaleCode || null,
      }
      return (await apiClient.post(`/v1/employees/${employeeId}/renew-deployment`, payload)).data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['employees'] })
      show({ tone: 'success', message: renewalType === 'CONTRACTUAL' ? 'Contract renewed.' : 'Deployment renewed.' })
      onClose()
    },
  })
  const fieldErrors = getFieldErrors(renewMutation.error)

  const formValid =
    monthlyAmount !== '' && startDate !== '' && endDate !== '' && refNo.trim() !== '' &&
    (renewalType === 'CONTRACTUAL' || vendorId !== '')

  return (
    <Modal title="Renew Contract / Deployment" onClose={onClose} maxWidthClassName="max-w-lg">
      <form
        className="space-y-3"
        onSubmit={(e) => {
          e.preventDefault()
          renewMutation.mutate()
        }}
      >
        {renewMutation.isError && (
          <FormErrorBanner title="Could not renew" errors={describeApiErrorList(renewMutation.error, 'Could not renew.')} />
        )}

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Renewal Type</label>
          <div className="flex gap-2">
            {(['CONTRACTUAL', 'OUTSOURCED'] as RenewalType[]).map((type) => (
              <button
                key={type}
                type="button"
                onClick={() => setRenewalType(type)}
                className={`rounded-full px-3 py-1 text-xs font-medium transition-colors ${
                  renewalType === type ? 'bg-brand-forest text-white' : 'bg-slate-100 text-slate-600'
                }`}
              >
                {type === 'CONTRACTUAL' ? 'Contract' : 'Deployment'}
              </button>
            ))}
          </div>
        </div>

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Benchmark Scale</label>
          <select
            value={scaleCode}
            onChange={(e) => setScaleCode(e.target.value)}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          >
            <option value="">None</option>
            {(gradeScalesQuery.data ?? []).map((s) => (
              <option key={s.scaleCode} value={s.scaleCode}>
                {s.scaleCode} - {s.cadre}
              </option>
            ))}
          </select>
        </div>

        {renewalType === 'OUTSOURCED' && (
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Manpower Vendor</label>
            <select
              required
              value={vendorId}
              onChange={(e) => setVendorId(e.target.value)}
              className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(fieldErrors?.vendorId))}`}
            >
              <option value="">Select...</option>
              {(vendorsQuery.data ?? []).map((v) => (
                <option key={v.id} value={v.id}>
                  {v.vendorCode} - {v.vendorName}
                </option>
              ))}
            </select>
            <FieldError message={fieldErrors?.vendorId} />
          </div>
        )}

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">
              {renewalType === 'CONTRACTUAL' ? 'Monthly Lumpsum (₹)' : 'Monthly CTC (₹)'}
            </label>
            <input
              required
              type="number"
              step="0.01"
              value={monthlyAmount}
              onChange={(e) => setMonthlyAmount(e.target.value)}
              className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(fieldErrors?.monthlyLumpsum || fieldErrors?.monthlyCtc))}`}
            />
            <FieldError message={fieldErrors?.monthlyLumpsum ?? fieldErrors?.monthlyCtc} />
          </div>
          {renewalType === 'OUTSOURCED' && (
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Agency Billing Rate (₹)</label>
              <input
                type="number"
                step="0.01"
                value={billingRate}
                onChange={(e) => setBillingRate(e.target.value)}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
          )}
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">
              {renewalType === 'CONTRACTUAL' ? 'Contract Start' : 'Deployment Start'}
            </label>
            <DatePicker value={startDate} onChange={setStartDate} hasError={Boolean(fieldErrors?.contractStartDate || fieldErrors?.deploymentStartDate)} />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">
              {renewalType === 'CONTRACTUAL' ? 'Contract End' : 'Deployment End'}
            </label>
            <DatePicker value={endDate} onChange={setEndDate} hasError={Boolean(fieldErrors?.contractEndDate || fieldErrors?.deploymentEndDate)} />
          </div>
        </div>

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">
            {renewalType === 'CONTRACTUAL' ? 'Approval Ref No.' : 'Work Order Ref'}
          </label>
          <input
            required
            value={refNo}
            onChange={(e) => setRefNo(e.target.value)}
            className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(fieldErrors?.approvalRefNo || fieldErrors?.workOrderRef))}`}
          />
          <FieldError message={fieldErrors?.approvalRefNo ?? fieldErrors?.workOrderRef} />
        </div>

        {renewalType === 'CONTRACTUAL' && (
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Engagement Terms (optional)</label>
            <textarea
              value={engagementTerms}
              onChange={(e) => setEngagementTerms(e.target.value)}
              rows={2}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
        )}

        <PrimaryButton type="submit" disabled={renewMutation.isPending || !formValid} className="w-full justify-center">
          Renew
        </PrimaryButton>
      </form>
    </Modal>
  )
}
