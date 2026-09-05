import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { LocateFixed, Pencil, Plus, Trash2 } from 'lucide-react'
import { apiClient } from '../../api/client'
import { describeApiError, describeApiErrorList } from '../../lib/apiError'
import { Modal } from '../../components/common/Modal'
import { FormErrorBanner } from '../../components/common/FormErrorBanner'
import { useToast } from '../../components/common/ToastProvider'
import { useGeolocation } from '../../components/attendance/useGeolocation'
import { Badge, Card, EmptyState, ErrorState, LoadingState, PageHeader, PrimaryButton, SecondaryButton } from '../../components/common/ui'
import type {
  CityClass,
  DistrictMasterResponse,
  OfficeType,
  Page,
  RegionalOfficeRequest,
  RegionalOfficeResponse,
  StateMasterResponse,
} from '../../types/api'

const TABS = ['Basic Info', 'Address Details', 'Geo & Attendance', 'Payroll Settings'] as const
type Tab = (typeof TABS)[number]

const EMPTY_FORM = {
  code: '',
  name: '',
  officeType: 'REGIONAL_OFFICE' as OfficeType,
  cityClass: 'Z' as CityClass,
  active: true,
  addressLine: '',
  city: '',
  district: '',
  districtCode: '',
  state: '',
  pinCode: '',
  latitude: '',
  longitude: '',
  geofenceRadiusMeters: '50',
  recreationClubDeduction: '0',
  procurementAllowanceApplicable: true,
}

/** SUPER_ADMIN-only (route-gated in App.tsx); backend also enforces this on every endpoint under /api/regional-offices. */
export function RoMasterPage() {
  const queryClient = useQueryClient()
  const { show } = useToast()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<RegionalOfficeResponse | null>(null)
  const [tab, setTab] = useState<Tab>('Basic Info')
  const [form, setForm] = useState(EMPTY_FORM)
  const [decommissioning, setDecommissioning] = useState<RegionalOfficeResponse | null>(null)
  const [decommissionReason, setDecommissionReason] = useState('')
  const geolocation = useGeolocation()

  const { data, isLoading, isError } = useQuery({
    queryKey: ['ro-masters'],
    queryFn: async () => (await apiClient.get<Page<RegionalOfficeResponse>>('/regional-offices', { params: { size: 200 } })).data,
  })

  const statesQuery = useQuery({
    queryKey: ['state-masters-all'],
    queryFn: async () => (await apiClient.get<Page<StateMasterResponse>>('/v1/admin/masters/states', { params: { size: 100 } })).data.content,
    enabled: modalOpen,
  })
  const states = statesQuery.data ?? []
  const selectedStateId = states.find((s) => s.stateName === form.state)?.id

  const districtsQuery = useQuery({
    queryKey: ['district-masters-for-state', selectedStateId],
    queryFn: async () =>
      (
        await apiClient.get<Page<DistrictMasterResponse>>('/v1/admin/masters/districts', {
          params: { size: 200, stateId: selectedStateId },
        })
      ).data.content,
    enabled: modalOpen && Boolean(selectedStateId),
  })
  const districts = districtsQuery.data ?? []

  const saveMutation = useMutation({
    mutationFn: async () => {
      const payload: RegionalOfficeRequest = {
        code: form.code,
        name: form.name,
        state: form.state,
        cityClass: form.cityClass,
        active: form.active,
        officeType: form.officeType,
        addressLine: form.addressLine || null,
        city: form.city || null,
        district: form.district || null,
        districtCode: form.districtCode || null,
        pinCode: form.pinCode || null,
        latitude: form.latitude ? Number(form.latitude) : null,
        longitude: form.longitude ? Number(form.longitude) : null,
        geofenceRadiusMeters: form.geofenceRadiusMeters ? Number(form.geofenceRadiusMeters) : null,
        recreationClubDeduction: form.recreationClubDeduction ? Number(form.recreationClubDeduction) : null,
        procurementAllowanceApplicable: form.procurementAllowanceApplicable,
      }
      if (editing) {
        return (await apiClient.put<RegionalOfficeResponse>(`/regional-offices/${editing.id}`, payload)).data
      }
      return (await apiClient.post<RegionalOfficeResponse>('/regional-offices', payload)).data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['ro-masters'] })
      show({ tone: 'success', message: editing ? 'Regional Office updated.' : 'Regional Office created.' })
      closeModal()
    },
  })

  function openCreate() {
    setEditing(null)
    setForm(EMPTY_FORM)
    setTab('Basic Info')
    setModalOpen(true)
  }

  function openEdit(ro: RegionalOfficeResponse) {
    setEditing(ro)
    setForm({
      code: ro.code,
      name: ro.name,
      officeType: ro.officeType,
      cityClass: ro.cityClass,
      active: ro.active,
      addressLine: ro.addressLine ?? '',
      city: ro.city ?? '',
      district: ro.district ?? '',
      districtCode: ro.districtCode ?? '',
      state: ro.state,
      pinCode: ro.pinCode ?? '',
      latitude: ro.latitude != null ? String(ro.latitude) : '',
      longitude: ro.longitude != null ? String(ro.longitude) : '',
      geofenceRadiusMeters: String(ro.geofenceRadiusMeters),
      recreationClubDeduction: String(ro.recreationClubDeduction),
      procurementAllowanceApplicable: ro.procurementAllowanceApplicable,
    })
    setTab('Basic Info')
    setModalOpen(true)
  }

  function closeModal() {
    setModalOpen(false)
    setEditing(null)
    setForm(EMPTY_FORM)
    setTab('Basic Info')
  }

  const decommissionMutation = useMutation({
    mutationFn: async () => {
      await apiClient.delete(`/regional-offices/${decommissioning!.id}`, { data: { reason: decommissionReason } })
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['ro-masters'] })
      show({ tone: 'success', message: 'Regional Office decommissioned.' })
      closeDecommission()
    },
  })

  function openDecommission(ro: RegionalOfficeResponse) {
    setDecommissioning(ro)
    setDecommissionReason('')
  }

  function closeDecommission() {
    setDecommissioning(null)
    setDecommissionReason('')
  }

  return (
    <div>
      <PageHeader
        title="Regional Office Master"
        description="RO / Head Office / Warehouse locations, geofencing, and payroll settings"
        actions={
          <PrimaryButton onClick={openCreate}>
            <Plus size={15} /> Add RO
          </PrimaryButton>
        }
      />

      <Card>
        {isLoading && <LoadingState label="Loading regional offices..." />}
        {isError && <ErrorState message="Could not load the RO master." />}
        {data && data.content.length === 0 && <EmptyState message="No regional offices recorded yet." />}

        {data && data.content.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[820px] border-collapse text-sm">
              <thead>
                <tr className="border-b border-slate-300 text-left text-slate-500">
                  <th className="py-2 pr-3">RO Code</th>
                  <th className="py-2 pr-3">Name</th>
                  <th className="py-2 pr-3">Type</th>
                  <th className="py-2 pr-3">State</th>
                  <th className="py-2 pr-3">City Class</th>
                  <th className="py-2 pr-3 text-right">Geofence (m)</th>
                  <th className="py-2 pr-3">Status</th>
                  <th className="py-2 pl-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((ro) => (
                  <tr key={ro.id} className="border-b border-slate-100">
                    <td className="py-2 pr-3 font-medium">{ro.code}</td>
                    <td className="py-2 pr-3">{ro.name}</td>
                    <td className="py-2 pr-3 text-xs text-slate-500">{ro.officeType.replace(/_/g, ' ')}</td>
                    <td className="py-2 pr-3 text-slate-500">{ro.state}</td>
                    <td className="py-2 pr-3">
                      <Badge tone="brand">{ro.cityClass}</Badge>
                    </td>
                    <td className="py-2 pr-3 text-right tabular-nums">{ro.geofenceRadiusMeters}</td>
                    <td className="py-2 pr-3">
                      <Badge tone={ro.active ? 'success' : 'neutral'}>{ro.active ? 'Active' : 'Inactive'}</Badge>
                    </td>
                    <td className="py-2 pl-3 text-right">
                      <div className="flex justify-end gap-1">
                        <button
                          type="button"
                          onClick={() => openEdit(ro)}
                          className="rounded-md p-1.5 text-slate-500 hover:bg-slate-100 hover:text-slate-700"
                          title="Edit"
                        >
                          <Pencil size={15} />
                        </button>
                        <button
                          type="button"
                          onClick={() => openDecommission(ro)}
                          className="rounded-md p-1.5 text-red-500 hover:bg-red-50 hover:text-red-700"
                          title="Decommission"
                        >
                          <Trash2 size={15} />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {modalOpen && (
        <Modal title={editing ? `Edit ${editing.name}` : 'Add Regional Office'} onClose={closeModal} maxWidthClassName="max-w-2xl">
          <div className="mb-4 flex flex-wrap gap-2">
            {TABS.map((t) => (
              <button
                key={t}
                type="button"
                onClick={() => setTab(t)}
                className={`rounded-full px-3 py-1 text-xs font-medium transition-colors ${
                  tab === t ? 'bg-brand-forest text-white' : 'bg-slate-100 text-slate-600'
                }`}
              >
                {t}
              </button>
            ))}
          </div>

          <form
            className="space-y-3"
            onSubmit={(e) => {
              e.preventDefault()
              saveMutation.mutate()
            }}
          >
            {tab === 'Basic Info' && (
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="mb-1 block text-xs font-medium text-slate-600">RO Code (2-digit)</label>
                  <input
                    required
                    disabled={editing !== null}
                    pattern="[0-9]{2}"
                    title="Two digits, e.g. 01"
                    maxLength={2}
                    value={form.code}
                    onChange={(e) => setForm({ ...form, code: e.target.value })}
                    className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm disabled:bg-slate-50 disabled:text-slate-500"
                    placeholder="01"
                  />
                </div>
                <div>
                  <label className="mb-1 block text-xs font-medium text-slate-600">RO Name</label>
                  <input
                    required
                    value={form.name}
                    onChange={(e) => setForm({ ...form, name: e.target.value })}
                    className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                  />
                </div>
                <div>
                  <label className="mb-1 block text-xs font-medium text-slate-600">Office Type</label>
                  <select
                    value={form.officeType}
                    onChange={(e) => setForm({ ...form, officeType: e.target.value as OfficeType })}
                    className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                  >
                    <option value="HEAD_OFFICE">Head Office</option>
                    <option value="REGIONAL_OFFICE">Regional Office</option>
                    <option value="WAREHOUSE">Warehouse</option>
                  </select>
                </div>
                <div>
                  <label className="mb-1 block text-xs font-medium text-slate-600">City Class</label>
                  <select
                    value={form.cityClass}
                    onChange={(e) => setForm({ ...form, cityClass: e.target.value as CityClass })}
                    className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                  >
                    <option value="X">X</option>
                    <option value="Y">Y</option>
                    <option value="Z">Z</option>
                  </select>
                </div>
              </div>
            )}

            {tab === 'Address Details' && (
              <div className="grid grid-cols-2 gap-3">
                <div className="col-span-2">
                  <label className="mb-1 block text-xs font-medium text-slate-600">Address Line</label>
                  <input
                    value={form.addressLine}
                    onChange={(e) => setForm({ ...form, addressLine: e.target.value })}
                    className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                  />
                </div>
                <div>
                  <label className="mb-1 block text-xs font-medium text-slate-600">City</label>
                  <input
                    value={form.city}
                    onChange={(e) => setForm({ ...form, city: e.target.value })}
                    className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                  />
                </div>
                <div>
                  <label className="mb-1 block text-xs font-medium text-slate-600">State</label>
                  <select
                    required
                    value={form.state}
                    onChange={(e) => setForm({ ...form, state: e.target.value, district: '', districtCode: '' })}
                    className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                  >
                    <option value="">Select state...</option>
                    {states.map((state) => (
                      <option key={state.id} value={state.stateName}>
                        {state.stateName}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="mb-1 block text-xs font-medium text-slate-600">District</label>
                  <select
                    value={form.district}
                    onChange={(e) => {
                      const district = districts.find((d) => d.districtName === e.target.value)
                      setForm({ ...form, district: e.target.value, districtCode: district?.districtCode ?? '' })
                    }}
                    disabled={!form.state}
                    className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm disabled:bg-slate-50"
                  >
                    <option value="">{form.state ? 'Select district...' : 'Select a state first'}</option>
                    {districts.map((district) => (
                      <option key={district.id} value={district.districtName}>
                        {district.districtName}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="mb-1 block text-xs font-medium text-slate-600">District Code</label>
                  <input
                    disabled
                    value={form.districtCode}
                    className="w-full rounded-md border border-slate-300 bg-slate-50 px-3 py-2 text-sm text-slate-500"
                  />
                </div>
                <div>
                  <label className="mb-1 block text-xs font-medium text-slate-600">PIN Code</label>
                  <input
                    value={form.pinCode}
                    onChange={(e) => setForm({ ...form, pinCode: e.target.value })}
                    className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                  />
                </div>
              </div>
            )}

            {tab === 'Geo & Attendance' && (
              <div className="space-y-3">
                <SecondaryButton type="button" onClick={geolocation.capture} disabled={geolocation.loading}>
                  <LocateFixed size={14} /> {geolocation.loading ? 'Locating...' : 'Use current location'}
                </SecondaryButton>
                {geolocation.error && <p className="text-xs text-red-600">{geolocation.error}</p>}
                {geolocation.position && (
                  <PrimaryButton
                    type="button"
                    onClick={() =>
                      setForm({
                        ...form,
                        latitude: String(geolocation.position!.latitude),
                        longitude: String(geolocation.position!.longitude),
                      })
                    }
                  >
                    Apply {geolocation.position.latitude.toFixed(6)}, {geolocation.position.longitude.toFixed(6)}
                  </PrimaryButton>
                )}
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="mb-1 block text-xs font-medium text-slate-600">Latitude</label>
                    <input
                      type="number"
                      step="0.0000001"
                      value={form.latitude}
                      onChange={(e) => setForm({ ...form, latitude: e.target.value })}
                      className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                    />
                  </div>
                  <div>
                    <label className="mb-1 block text-xs font-medium text-slate-600">Longitude</label>
                    <input
                      type="number"
                      step="0.0000001"
                      value={form.longitude}
                      onChange={(e) => setForm({ ...form, longitude: e.target.value })}
                      className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                    />
                  </div>
                  <div>
                    <label className="mb-1 block text-xs font-medium text-slate-600">Geofence Radius (meters)</label>
                    <input
                      type="number"
                      min="0"
                      value={form.geofenceRadiusMeters}
                      onChange={(e) => setForm({ ...form, geofenceRadiusMeters: e.target.value })}
                      className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                    />
                  </div>
                </div>
              </div>
            )}

            {tab === 'Payroll Settings' && (
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="mb-1 block text-xs font-medium text-slate-600">Recreation Club Deduction (₹)</label>
                  <input
                    type="number"
                    step="0.01"
                    min="0"
                    value={form.recreationClubDeduction}
                    onChange={(e) => setForm({ ...form, recreationClubDeduction: e.target.value })}
                    className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                  />
                </div>
                <label className="mt-6 flex items-center gap-2 text-sm text-slate-700">
                  <input
                    type="checkbox"
                    checked={form.procurementAllowanceApplicable}
                    onChange={(e) => setForm({ ...form, procurementAllowanceApplicable: e.target.checked })}
                  />
                  Procurement Allowance Applicable
                </label>
              </div>
            )}

            <PrimaryButton type="submit" disabled={saveMutation.isPending} className="w-full justify-center">
              Save Regional Office
            </PrimaryButton>
            {saveMutation.isError && (
              <ErrorState message={describeApiError(saveMutation.error, 'Could not save the regional office.')} />
            )}
          </form>
        </Modal>
      )}

      {decommissioning && (
        <Modal title={`Decommission ${decommissioning.name}?`} onClose={closeDecommission}>
          <div className="space-y-3">
            <p className="text-sm text-slate-600">
              This deactivates <strong>{decommissioning.code} - {decommissioning.name}</strong> and removes it from active
              use. It's rejected if active staff or active DPCs are still attached.
            </p>
            {decommissionMutation.isError && (
              <FormErrorBanner
                title="Could not decommission"
                errors={describeApiErrorList(decommissionMutation.error, 'Could not decommission the Regional Office.')}
              />
            )}
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Deletion Reason (required)</label>
              <textarea
                required
                rows={3}
                value={decommissionReason}
                onChange={(e) => setDecommissionReason(e.target.value)}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                placeholder="Why is this Regional Office being decommissioned?"
              />
            </div>
            <div className="flex gap-2">
              <SecondaryButton type="button" onClick={closeDecommission} className="flex-1 justify-center">
                Cancel
              </SecondaryButton>
              <SecondaryButton
                type="button"
                onClick={() => decommissionMutation.mutate()}
                disabled={decommissionMutation.isPending || !decommissionReason.trim()}
                className="flex-1 justify-center border-red-200 text-red-600 hover:bg-red-50"
              >
                Decommission
              </SecondaryButton>
            </div>
          </div>
        </Modal>
      )}
    </div>
  )
}
