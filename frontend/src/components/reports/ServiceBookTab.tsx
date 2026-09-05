import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { apiClient } from '../../api/client'
import { describeApiErrorList, getFieldErrors } from '../../lib/apiError'
import { formatDate } from '../../lib/date'
import { Modal } from '../common/Modal'
import { FormErrorBanner } from '../common/FormErrorBanner'
import { EmptyState, ErrorState, FieldError, LoadingState, PrimaryButton, SecondaryButton, errorInputClass } from '../common/ui'
import { DepartmentFilter } from './DepartmentFilter'
import type { CareerEventType, DesignationResponse, Page, PayScaleResponse, RegionalOfficeResponse, ServiceBookEventRequest, ServiceBookEventResponse } from '../../types/api'

const EVENT_TYPES: CareerEventType[] = ['PROMOTION', 'TRANSFER', 'MACP', 'PAY_REVISION', 'PENALTY_WITHHOLD_INCREMENT', 'PENALTY_CENSUROUS', 'EOL_LWP']

const EMPTY_FORM = {
  eventDate: '',
  eventType: 'PROMOTION' as CareerEventType,
  orderNumber: '',
  orderDate: '',
  departmentId: '',
  designationId: '',
  regionalOfficeId: '',
  payScaleId: '',
  basicPay: '',
  remarks: '',
}

/** PIMS_SPEC.md operational-features task, Section 3: the Digital Service Book Career Event Logger tab inside the Employee 360 drawer. */
export function ServiceBookTab({ employeeId }: { employeeId: number }) {
  const queryClient = useQueryClient()
  const [modalOpen, setModalOpen] = useState(false)
  const [form, setForm] = useState(EMPTY_FORM)

  const { data, isLoading, isError } = useQuery({
    queryKey: ['employee-service-book', employeeId],
    queryFn: async () => (await apiClient.get<ServiceBookEventResponse[]>(`/v1/employees/${employeeId}/service-book`)).data,
  })

  const designationsQuery = useQuery({
    queryKey: ['designations-all'],
    queryFn: async () => (await apiClient.get<Page<DesignationResponse>>('/designations', { params: { size: 200 } })).data.content,
    enabled: modalOpen,
  })
  const regionalOfficesQuery = useQuery({
    queryKey: ['ro-masters-all'],
    queryFn: async () => (await apiClient.get<Page<RegionalOfficeResponse>>('/regional-offices', { params: { size: 200 } })).data.content,
    enabled: modalOpen,
  })
  const payScalesQuery = useQuery({
    queryKey: ['pay-scales-all'],
    queryFn: async () => (await apiClient.get<Page<PayScaleResponse>>('/pay-scales', { params: { size: 200 } })).data.content,
    enabled: modalOpen,
  })

  const recordMutation = useMutation({
    mutationFn: async () => {
      const payload: ServiceBookEventRequest = {
        eventDate: form.eventDate,
        eventType: form.eventType,
        orderNumber: form.orderNumber || null,
        orderDate: form.orderDate || null,
        departmentId: form.departmentId ? Number(form.departmentId) : null,
        designationId: form.designationId ? Number(form.designationId) : null,
        regionalOfficeId: form.regionalOfficeId ? Number(form.regionalOfficeId) : null,
        payScaleId: form.payScaleId ? Number(form.payScaleId) : null,
        basicPay: form.basicPay ? Number(form.basicPay) : null,
        remarks: form.remarks || null,
      }
      return (await apiClient.post<ServiceBookEventResponse>(`/v1/employees/${employeeId}/service-book`, payload)).data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['employee-service-book', employeeId] })
      setModalOpen(false)
      setForm(EMPTY_FORM)
    },
  })
  const recordFieldErrors = getFieldErrors(recordMutation.error)

  return (
    <div>
      <div className="mb-3 flex justify-end">
        <PrimaryButton onClick={() => setModalOpen(true)}>
          <Plus size={14} /> Record Service Event
        </PrimaryButton>
      </div>

      {isLoading && <LoadingState label="Loading service book..." />}
      {isError && <ErrorState message="Could not load the service book." />}
      {data && data.length === 0 && <EmptyState message="No service book events recorded yet." />}
      {data && data.length > 0 && (
        <ul className="space-y-2">
          {data.map((event) => (
            <li key={event.id} className="rounded-md border border-slate-200 p-3 text-sm">
              <div className="flex items-center justify-between">
                <span className="font-medium text-slate-700">{event.eventType.replace(/_/g, ' ')}</span>
                <span className="text-xs text-slate-400">{formatDate(event.eventDate)}</span>
              </div>
              <p className="mt-1 text-xs text-slate-500">
                {[event.designationTitle, event.departmentName, event.regionalOfficeName].filter(Boolean).join(' / ') || 'No org change recorded'}
                {event.basicPay != null && ` - Basic: ${event.basicPay.toLocaleString('en-IN')}`}
              </p>
              {event.orderNumber && (
                <p className="text-xs text-slate-400">
                  Order: {event.orderNumber} {event.orderDate && `dated ${formatDate(event.orderDate)}`}
                </p>
              )}
              {event.remarks && <p className="mt-1 text-xs text-slate-500">{event.remarks}</p>}
            </li>
          ))}
        </ul>
      )}

      {modalOpen && (
        <Modal title="Record Service Event" onClose={() => setModalOpen(false)}>
          <form
            className="space-y-3"
            onSubmit={(e) => {
              e.preventDefault()
              recordMutation.mutate()
            }}
          >
            {recordMutation.isError && (
              <FormErrorBanner title="Could not record event" errors={describeApiErrorList(recordMutation.error, 'Could not record event.')} />
            )}
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="mb-1 block text-xs font-medium text-slate-600">Event Type</label>
                <select
                  value={form.eventType}
                  onChange={(e) => setForm({ ...form, eventType: e.target.value as CareerEventType })}
                  className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(recordFieldErrors?.eventType))}`}
                >
                  {EVENT_TYPES.map((t) => (
                    <option key={t} value={t}>
                      {t.replace(/_/g, ' ')}
                    </option>
                  ))}
                </select>
                <FieldError message={recordFieldErrors?.eventType} />
              </div>
              <div>
                <label className="mb-1 block text-xs font-medium text-slate-600">Event Date</label>
                <input
                  required
                  type="date"
                  value={form.eventDate}
                  onChange={(e) => setForm({ ...form, eventDate: e.target.value })}
                  className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(recordFieldErrors?.eventDate))}`}
                />
                <FieldError message={recordFieldErrors?.eventDate} />
              </div>
              <div>
                <label className="mb-1 block text-xs font-medium text-slate-600">Order Number</label>
                <input
                  value={form.orderNumber}
                  onChange={(e) => setForm({ ...form, orderNumber: e.target.value })}
                  className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                />
              </div>
              <div>
                <label className="mb-1 block text-xs font-medium text-slate-600">Order Date</label>
                <input
                  type="date"
                  value={form.orderDate}
                  onChange={(e) => setForm({ ...form, orderDate: e.target.value })}
                  className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                />
              </div>
              <div>
                <label className="mb-1 block text-xs font-medium text-slate-600">Department</label>
                <DepartmentFilter value={form.departmentId} onChange={(v) => setForm({ ...form, departmentId: v })} />
              </div>
              <div>
                <label className="mb-1 block text-xs font-medium text-slate-600">Designation</label>
                <select
                  value={form.designationId}
                  onChange={(e) => setForm({ ...form, designationId: e.target.value })}
                  className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                >
                  <option value="">Unchanged</option>
                  {(designationsQuery.data ?? []).map((d) => (
                    <option key={d.id} value={d.id}>
                      {d.title}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="mb-1 block text-xs font-medium text-slate-600">Regional Office</label>
                <select
                  value={form.regionalOfficeId}
                  onChange={(e) => setForm({ ...form, regionalOfficeId: e.target.value })}
                  className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                >
                  <option value="">Unchanged</option>
                  {(regionalOfficesQuery.data ?? []).map((ro) => (
                    <option key={ro.id} value={ro.id}>
                      {ro.code} - {ro.name}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="mb-1 block text-xs font-medium text-slate-600">Pay Scale</label>
                <select
                  value={form.payScaleId}
                  onChange={(e) => setForm({ ...form, payScaleId: e.target.value })}
                  className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                >
                  <option value="">Unchanged</option>
                  {(payScalesQuery.data ?? []).map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.grade} ({p.scaleType})
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="mb-1 block text-xs font-medium text-slate-600">Basic Pay</label>
                <input
                  type="number"
                  step="0.01"
                  value={form.basicPay}
                  onChange={(e) => setForm({ ...form, basicPay: e.target.value })}
                  className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(recordFieldErrors?.basicPay))}`}
                />
                {(form.eventType === 'PROMOTION' || form.eventType === 'TRANSFER') && form.basicPay && (
                  <p className="mt-1 text-[11px] text-slate-400">Also updates the employee's current regular basic pay.</p>
                )}
                <FieldError message={recordFieldErrors?.basicPay} />
              </div>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Remarks</label>
              <textarea
                value={form.remarks}
                onChange={(e) => setForm({ ...form, remarks: e.target.value })}
                rows={2}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            <div className="flex gap-2">
              <SecondaryButton type="button" onClick={() => setModalOpen(false)}>
                Cancel
              </SecondaryButton>
              <PrimaryButton type="submit" disabled={recordMutation.isPending || !form.eventDate} className="flex-1 justify-center">
                Save Event
              </PrimaryButton>
            </div>
          </form>
        </Modal>
      )}
    </div>
  )
}
