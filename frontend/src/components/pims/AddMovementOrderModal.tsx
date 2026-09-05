import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { describeApiErrorList, getFieldErrors } from '../../lib/apiError'
import { DatePicker } from '../common/DatePicker'
import { Modal } from '../common/Modal'
import { FormErrorBanner } from '../common/FormErrorBanner'
import { FieldError, PrimaryButton, errorInputClass } from '../common/ui'
import type {
  DesignationResponse,
  DpcResponse,
  EmployeeMovementRecordResponse,
  EmployeeResponse,
  MovementOrderCreateRequest,
  MovementOrderType,
  Page,
  RegionalOfficeResponse,
  TransferNature,
} from '../../types/api'

/**
 * Replaces the movement-order create form previously inlined in MovementOrdersTab, implementing
 * the 5 movement rules: (i) auto-fetch the employee's current station/designation/pay scale on
 * selection, (ii) FROM fields read-only, (iii) no Department dropdown (this schema has no
 * designation->department link to derive one from - simply omitted, never sent), (iv) TO Station
 * groups Head Office/Regional Offices vs. DPCs, (v) Promotion/Transfer-cum-Promotion target
 * designations are filtered to strictly higher grades (grade_scale_master.hierarchyLevel, lower
 * number = higher rank) below Board level, with the target pay scale auto-derived from the grade.
 */
export function AddMovementOrderModal({ onClose }: { onClose: () => void }) {
  const queryClient = useQueryClient()

  const [orderType, setOrderType] = useState<MovementOrderType>('TRANSFER')
  const [orderRefNo, setOrderRefNo] = useState('')
  const [orderDate, setOrderDate] = useState('')
  const [effectiveDate, setEffectiveDate] = useState('')
  const [employeeId, setEmployeeId] = useState('')
  const [transferNature, setTransferNature] = useState<TransferNature>('ADMINISTRATIVE')
  const [transferBenefitAdmissible, setTransferBenefitAdmissible] = useState(true)
  const [requestApplicationRef, setRequestApplicationRef] = useState('')
  const [requestReason, setRequestReason] = useState('')
  const [toStation, setToStation] = useState(''); // "office:<id>" | "dpc:<id>"
  const [toDesignationId, setToDesignationId] = useState('')
  const [toPayScale, setToPayScale] = useState('')
  const [promotionalBasicPay, setPromotionalBasicPay] = useState('')
  const [probationPeriodMonths, setProbationPeriodMonths] = useState('6')

  const employees = useEmployeeOptions()
  const designations = useDesignationFullOptions()
  const offices = useRegionalOfficeFullOptions()
  const dpcs = useDpcOptions()

  const selectedEmployee = employees.find((e) => String(e.id) === employeeId) ?? null
  const currentDesignation = selectedEmployee ? designations.find((d) => d.id === selectedEmployee.designationId) ?? null : null
  const isPromotionType = orderType !== 'TRANSFER'

  const eligibleToDesignations =
    isPromotionType && currentDesignation?.hierarchyLevel != null
      ? designations.filter((d) => d.hierarchyLevel != null && d.hierarchyLevel < currentDesignation.hierarchyLevel! && !d.boardLevel)
      : designations

  // Rule (v): auto-derive the target pay scale from the selected designation's linked grade.
  useEffect(() => {
    if (!toDesignationId) return
    const target = designations.find((d) => String(d.id) === toDesignationId)
    if (target?.idaPayScale) {
      setToPayScale(target.idaPayScale)
    }
  }, [toDesignationId, designations])

  // If the order type changes away from Promotion/TCP, or the current designation's grade is
  // unknown, a previously-selected now-ineligible target designation is cleared rather than left
  // silently invalid.
  useEffect(() => {
    if (toDesignationId && !eligibleToDesignations.some((d) => String(d.id) === toDesignationId)) {
      setToDesignationId('')
      setToPayScale('')
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [orderType, employeeId])

  const createMutation = useMutation({
    mutationFn: async () => {
      const [toKind, toId] = toStation.split(':');
      const payload: MovementOrderCreateRequest = {
        orderType,
        orderRefNo,
        orderDate,
        effectiveDate: effectiveDate || null,
        employeeId: Number(employeeId),
        transferNature,
        transferBenefitAdmissible,
        requestApplicationRef: requestApplicationRef || null,
        requestReason: requestReason || null,
        fromOfficeId: selectedEmployee?.roId ?? null,
        fromDpcId: selectedEmployee?.dpcId ?? null,
        fromDesignationId: selectedEmployee?.designationId ?? 0,
        fromPayScale: selectedEmployee?.payScaleGrade ?? null,
        toOfficeId: toKind === 'office' ? Number(toId) : null,
        toDpcId: toKind === 'dpc' ? Number(toId) : null,
        toDesignationId: Number(toDesignationId),
        toPayScale: toPayScale || null,
        promotionalBasicPay: isPromotionType && promotionalBasicPay ? Number(promotionalBasicPay) : null,
        probationPeriodMonths: isPromotionType && probationPeriodMonths ? Number(probationPeriodMonths) : null,
      }
      return (await apiClient.post<EmployeeMovementRecordResponse>('/v1/pims/movements/orders', payload)).data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['movement-records'] })
      onClose()
    },
  })
  const fieldErrors = getFieldErrors(createMutation.error)

  const formValid =
    orderRefNo.trim() !== '' &&
    orderDate !== '' &&
    employeeId !== '' &&
    toStation !== '' &&
    toDesignationId !== ''

  return (
    <Modal title="Add Movement Order" onClose={onClose} maxWidthClassName="max-w-2xl">
      <form
        className="space-y-3"
        onSubmit={(e) => {
          e.preventDefault()
          createMutation.mutate()
        }}
      >
        {createMutation.isError && (
          <FormErrorBanner title="Could not create order" errors={describeApiErrorList(createMutation.error, 'Could not create order.')} />
        )}

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Order Type</label>
            <select
              value={orderType}
              onChange={(e) => setOrderType(e.target.value as MovementOrderType)}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            >
              <option value="TRANSFER">Transfer</option>
              <option value="PROMOTION">Promotion</option>
              <option value="TRANSFER_CUM_PROMOTION">Transfer-cum-Promotion</option>
            </select>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Order Ref No.</label>
            <input
              required
              value={orderRefNo}
              onChange={(e) => setOrderRefNo(e.target.value)}
              placeholder="JCI/Pers./HO/Transfer/2025-26/94"
              className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(fieldErrors?.orderRefNo))}`}
            />
            <FieldError message={fieldErrors?.orderRefNo} />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Order Date (dd-mm-yyyy)</label>
            <DatePicker value={orderDate} onChange={setOrderDate} />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Effective Date (dd-mm-yyyy)</label>
            <DatePicker value={effectiveDate} onChange={setEffectiveDate} min={orderDate || undefined} />
          </div>
          <div className="col-span-2">
            <label className="mb-1 block text-xs font-medium text-slate-600">Employee</label>
            <select
              required
              value={employeeId}
              onChange={(e) => {
                setEmployeeId(e.target.value)
                setToDesignationId('')
                setToPayScale('')
              }}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            >
              <option value="">Select...</option>
              {employees.map((emp) => (
                <option key={emp.id} value={emp.id}>
                  {emp.employeeCode} - {emp.fullName}
                </option>
              ))}
            </select>
          </div>
        </div>

        {/* Rule (i)/(ii): current station/designation/pay scale, auto-fetched and read-only. */}
        {selectedEmployee && (
          <div className="rounded-md border border-slate-200 bg-slate-50 p-3">
            <p className="mb-2 text-xs font-semibold uppercase text-slate-400">From (current, read-only)</p>
            <div className="grid grid-cols-3 gap-3 text-sm">
              <div>
                <label className="mb-1 block text-xs font-medium text-slate-500">Station</label>
                <input
                  disabled
                  readOnly
                  value={selectedEmployee.roName ?? selectedEmployee.dpcName ?? '—'}
                  className="w-full rounded-md border border-slate-200 bg-slate-100 px-3 py-2 text-sm text-slate-600"
                />
              </div>
              <div>
                <label className="mb-1 block text-xs font-medium text-slate-500">Designation</label>
                <input
                  disabled
                  readOnly
                  value={selectedEmployee.designationTitle}
                  className="w-full rounded-md border border-slate-200 bg-slate-100 px-3 py-2 text-sm text-slate-600"
                />
              </div>
              <div>
                <label className="mb-1 block text-xs font-medium text-slate-500">Pay Scale</label>
                <input
                  disabled
                  readOnly
                  value={selectedEmployee.payScaleGrade ?? '—'}
                  className="w-full rounded-md border border-slate-200 bg-slate-100 px-3 py-2 text-sm text-slate-600"
                />
              </div>
            </div>
          </div>
        )}

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Nature of Transfer</label>
          <div className="flex gap-2">
            {(['ADMINISTRATIVE', 'OWN_REQUEST', 'MUTUAL'] as TransferNature[]).map((nature) => (
              <button
                key={nature}
                type="button"
                onClick={() => {
                  setTransferNature(nature)
                  setTransferBenefitAdmissible(nature !== 'OWN_REQUEST')
                }}
                className={`flex-1 rounded-md border px-3 py-2 text-xs font-medium transition-colors ${
                  transferNature === nature ? 'border-brand-forest bg-brand-forest text-white' : 'border-slate-300 text-slate-600 hover:bg-slate-50'
                }`}
              >
                {nature === 'OWN_REQUEST' ? 'Own Request' : nature.charAt(0) + nature.slice(1).toLowerCase()}
              </button>
            ))}
          </div>
        </div>

        {transferNature !== 'OWN_REQUEST' && (
          <label className="flex items-center gap-2 text-sm text-slate-600">
            <input type="checkbox" checked={transferBenefitAdmissible} onChange={(e) => setTransferBenefitAdmissible(e.target.checked)} />
            Transfer benefit (unavailed JT -&gt; EL credit) admissible
          </label>
        )}

        {transferNature === 'OWN_REQUEST' && (
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Request Application Ref</label>
              <input
                value={requestApplicationRef}
                onChange={(e) => setRequestApplicationRef(e.target.value)}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Request Reason</label>
              <input
                value={requestReason}
                onChange={(e) => setRequestReason(e.target.value)}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
          </div>
        )}

        <div className="rounded-md border border-slate-200 p-3">
          <p className="mb-2 text-xs font-semibold uppercase text-slate-400">To</p>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Station</label>
              <select required value={toStation} onChange={(e) => setToStation(e.target.value)} className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm">
                <option value="">Select...</option>
                <optgroup label="Head Office & Regional Offices">
                  {offices.map((o) => (
                    <option key={`office-${o.id}`} value={`office:${o.id}`}>
                      {o.name}
                    </option>
                  ))}
                </optgroup>
                <optgroup label="Departmental Purchase Centres (DPCs)">
                  {dpcs.map((d) => (
                    <option key={`dpc-${d.id}`} value={`dpc:${d.id}`}>
                      {d.name}
                    </option>
                  ))}
                </optgroup>
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">
                Designation {isPromotionType && <span className="text-slate-400">(higher grade only)</span>}
              </label>
              <select
                required
                value={toDesignationId}
                onChange={(e) => setToDesignationId(e.target.value)}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              >
                <option value="">Select...</option>
                {eligibleToDesignations.map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.title}
                    {d.scaleCode ? ` (${d.scaleCode})` : ''}
                  </option>
                ))}
              </select>
              {isPromotionType && currentDesignation?.hierarchyLevel == null && (
                <p className="mt-1 text-xs text-amber-600">Current designation has no grade assigned - showing all designations.</p>
              )}
            </div>
            <div className="col-span-2">
              <label className="mb-1 block text-xs font-medium text-slate-600">Pay Scale</label>
              <input
                value={toPayScale}
                onChange={(e) => setToPayScale(e.target.value)}
                placeholder="Auto-derived from designation's grade, or enter manually"
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
          </div>
        </div>

        {isPromotionType && (
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Promotional Basic Pay</label>
              <input
                type="number"
                step="0.01"
                value={promotionalBasicPay}
                onChange={(e) => setPromotionalBasicPay(e.target.value)}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Probation Period (months)</label>
              <input
                type="number"
                value={probationPeriodMonths}
                onChange={(e) => setProbationPeriodMonths(e.target.value)}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
          </div>
        )}

        <PrimaryButton type="submit" disabled={createMutation.isPending || !formValid} className="w-full justify-center">
          Create Order
        </PrimaryButton>
      </form>
    </Modal>
  )
}

function useEmployeeOptions(): EmployeeResponse[] {
  const { data } = useQuery({
    queryKey: ['movement-employee-options'],
    queryFn: async () => (await apiClient.get<Page<EmployeeResponse>>('/employees', { params: { size: 500 } })).data.content,
  })
  return data ?? []
}

function useDesignationFullOptions(): DesignationResponse[] {
  const { data } = useQuery({
    queryKey: ['movement-designation-full-options'],
    queryFn: async () => (await apiClient.get<Page<DesignationResponse>>('/designations', { params: { size: 200 } })).data.content,
  })
  return data ?? []
}

function useRegionalOfficeFullOptions(): RegionalOfficeResponse[] {
  const { data } = useQuery({
    queryKey: ['movement-office-full-options'],
    queryFn: async () => (await apiClient.get<Page<RegionalOfficeResponse>>('/regional-offices', { params: { size: 500 } })).data.content,
  })
  return data ?? []
}

function useDpcOptions(): DpcResponse[] {
  const { data } = useQuery({
    queryKey: ['movement-dpc-options'],
    queryFn: async () => (await apiClient.get<Page<DpcResponse>>('/dpcs', { params: { size: 500 } })).data.content,
  })
  return data ?? []
}
