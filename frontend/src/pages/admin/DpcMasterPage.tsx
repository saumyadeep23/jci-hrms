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
  DpcRequest,
  DpcResponse,
  DpcType,
  Page,
  RegionalOfficeResponse,
  StateMasterResponse,
} from '../../types/api'

const TABS = ['Basic Info', 'Address & Operational Details', 'Geo & Attendance'] as const
type Tab = (typeof TABS)[number]

const EMPTY_FORM = {
  roId: '',
  code: '',
  name: '',
  shortName: '',
  dpcType: 'DPC' as DpcType,
  state: '',
  district: '',
  districtCode: '',
  cityClass: 'Z' as CityClass,
  latitude: '',
  longitude: '',
  geofenceRadiusMeters: '100',
  active: true,
}

/** SUPER_ADMIN-only (route-gated in App.tsx); backend also enforces this on every endpoint under /api/dpcs. */
export function DpcMasterPage() {
  const queryClient = useQueryClient()
  const { show } = useToast()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<DpcResponse | null>(null)
  const [tab, setTab] = useState<Tab>('Basic Info')
  const [form, setForm] = useState(EMPTY_FORM)
  const [decommissioning, setDecommissioning] = useState<DpcResponse | null>(null)
  const [decommissionReason, setDecommissionReason] = useState('')
  const geolocation = useGeolocation()

  const roQuery = useQuery({
    queryKey: ['ro-masters-all'],
    queryFn: async () => (await apiClient.get<Page<RegionalOfficeResponse>>('/regional-offices', { params: { size: 200 } })).data.content,
  })
  const regionalOffices = roQuery.data ?? []

  const statesQuery = useQuery({
    queryKey: ['state-masters-all'],
    queryFn: async () => (await apiClient.get<Page<StateMasterResponse>>('/v1/admin/masters/states', { params: { size: 100 } })).data.content,
    enabled: modalOpen,
  })
  // The DPC's geographical State/District is independent of its supervising
  // RO's location - a DPC can be geographically located in a different state
  // from the RO that administratively supervises it, so this is driven
  // purely by the DPC's own selected State (form.state), never by the RO.
  const states = (statesQuery.data ?? []).filter((s) => s.active)
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
  const districts = (districtsQuery.data ?? []).filter((d) => d.active)

  const { data, isLoading, isError } = useQuery({
    queryKey: ['dpc-masters'],
    queryFn: async () => (await apiClient.get<Page<DpcResponse>>('/dpcs', { params: { size: 200 } })).data,
  })

  const saveMutation = useMutation({
    mutationFn: async () => {
      const payload: DpcRequest = {
        roId: Number(form.roId),
        code: form.code,
        name: form.name,
        district: form.district,
        state: form.state,
        latitude: form.latitude ? Number(form.latitude) : null,
        longitude: form.longitude ? Number(form.longitude) : null,
        geofenceRadiusMeters: form.geofenceRadiusMeters ? Number(form.geofenceRadiusMeters) : null,
        active: form.active,
        shortName: form.shortName || null,
        dpcType: form.dpcType,
        districtCode: form.districtCode || null,
        cityClass: form.cityClass,
      }
      if (editing) {
        return (await apiClient.put<DpcResponse>(`/dpcs/${editing.id}`, payload)).data
      }
      return (await apiClient.post<DpcResponse>('/dpcs', payload)).data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['dpc-masters'] })
      show({ tone: 'success', message: editing ? 'DPC updated.' : 'DPC created.' })
      closeModal()
    },
  })

  function openCreate() {
    setEditing(null)
    setForm(EMPTY_FORM)
    setTab('Basic Info')
    setModalOpen(true)
  }

  function openEdit(dpc: DpcResponse) {
    setEditing(dpc)
    setForm({
      roId: String(dpc.roId),
      code: dpc.code,
      name: dpc.name,
      shortName: dpc.shortName ?? '',
      dpcType: dpc.dpcType,
      state: dpc.state,
      district: dpc.district,
      districtCode: dpc.districtCode ?? '',
      cityClass: dpc.cityClass,
      latitude: dpc.latitude != null ? String(dpc.latitude) : '',
      longitude: dpc.longitude != null ? String(dpc.longitude) : '',
      geofenceRadiusMeters: dpc.geofenceRadiusMeters != null ? String(dpc.geofenceRadiusMeters) : '',
      active: dpc.active,
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
      await apiClient.delete(`/dpcs/${decommissioning!.id}`, { data: { reason: decommissionReason } })
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['dpc-masters'] })
      show({ tone: 'success', message: 'DPC decommissioned.' })
      closeDecommission()
    },
  })

  function openDecommission(dpc: DpcResponse) {
    setDecommissioning(dpc)
    setDecommissionReason('')
  }

  function closeDecommission() {
    setDecommissioning(null)
    setDecommissionReason('')
  }

  return (
    <div>
      <PageHeader
        title="DPC Master"
        description="Departmental Purchase Centres and Sub-DPCs, linked to a Regional Office"
        actions={
          <PrimaryButton onClick={openCreate} disabled={regionalOffices.length === 0}>
            <Plus size={15} /> Add DPC
          </PrimaryButton>
        }
      />

      <Card>
        {isLoading && <LoadingState label="Loading DPCs..." />}
        {isError && <ErrorState message="Could not load the DPC master." />}
        {data && data.content.length === 0 && <EmptyState message="No DPCs recorded yet." />}

        {data && data.content.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[820px] border-collapse text-sm">
              <thead>
                <tr className="border-b border-slate-300 text-left text-slate-500">
                  <th className="py-2 pr-3">DPC Code</th>
                  <th className="py-2 pr-3">Name</th>
                  <th className="py-2 pr-3">Type</th>
                  <th className="py-2 pr-3">Parent RO</th>
                  <th className="py-2 pr-3">State</th>
                  <th className="py-2 pr-3 text-right">Geofence (m)</th>
                  <th className="py-2 pr-3">Status</th>
                  <th className="py-2 pl-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((dpc) => (
                  <tr key={dpc.id} className="border-b border-slate-100">
                    <td className="py-2 pr-3 font-medium">{dpc.code}</td>
                    <td className="py-2 pr-3">{dpc.name}</td>
                    <td className="py-2 pr-3">
                      <Badge tone={dpc.dpcType === 'DPC' ? 'brand' : 'neutral'}>{dpc.dpcType.replace('_', ' ')}</Badge>
                    </td>
                    <td className="py-2 pr-3 text-xs text-slate-500">
                      {dpc.roCode} - {dpc.roName}
                    </td>
                    <td className="py-2 pr-3 text-slate-500">{dpc.state}</td>
                    <td className="py-2 pr-3 text-right tabular-nums">{dpc.geofenceRadiusMeters ?? '—'}</td>
                    <td className="py-2 pr-3">
                      <Badge tone={dpc.active ? 'success' : 'neutral'}>{dpc.active ? 'Active' : 'Inactive'}</Badge>
                    </td>
                    <td className="py-2 pl-3 text-right">
                      <div className="flex justify-end gap-1">
                        <button
                          type="button"
                          onClick={() => openEdit(dpc)}
                          className="rounded-md p-1.5 text-slate-500 hover:bg-slate-100 hover:text-slate-700"
                          title="Edit"
                        >
                          <Pencil size={15} />
                        </button>
                        <button
                          type="button"
                          onClick={() => openDecommission(dpc)}
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
        <Modal title={editing ? `Edit ${editing.name}` : 'Add DPC'} onClose={closeModal} maxWidthClassName="max-w-2xl">
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
                <div className="col-span-2">
                  <label className="mb-1 block text-xs font-medium text-slate-600">Regional Office</label>
                  <select
                    required
                    value={form.roId}
                    onChange={(e) => setForm({ ...form, roId: e.target.value })}
                    className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                  >
                    <option value="">Select RO...</option>
                    {regionalOffices.map((ro) => (
                      <option key={ro.id} value={ro.id}>
                        {ro.code} - {ro.name}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="mb-1 block text-xs font-medium text-slate-600">DPC Code (4-digit)</label>
                  <input
                    required
                    disabled={editing !== null}
                    pattern="[0-9]{4}"
                    title="Four digits, e.g. 0001"
                    maxLength={4}
                    value={form.code}
                    onChange={(e) => setForm({ ...form, code: e.target.value })}
                    className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm disabled:bg-slate-50 disabled:text-slate-500"
                    placeholder="0001"
                  />
                </div>
                <div>
                  <label className="mb-1 block text-xs font-medium text-slate-600">DPC Name</label>
                  <input
                    required
                    value={form.name}
                    onChange={(e) => setForm({ ...form, name: e.target.value })}
                    className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                  />
                </div>
                <div>
                  <label className="mb-1 block text-xs font-medium text-slate-600">Short Name</label>
                  <input
                    maxLength={20}
                    value={form.shortName}
                    onChange={(e) => setForm({ ...form, shortName: e.target.value })}
                    className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                  />
                </div>
                <div>
                  <label className="mb-1 block text-xs font-medium text-slate-600">DPC Type</label>
                  <select
                    value={form.dpcType}
                    onChange={(e) => setForm({ ...form, dpcType: e.target.value as DpcType })}
                    className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                  >
                    <option value="DPC">DPC</option>
                    <option value="SUB_DPC">Sub-DPC</option>
                  </select>
                </div>
              </div>
            )}

            {tab === 'Address & Operational Details' && (
              <div className="grid grid-cols-2 gap-3">
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
                    required
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

            <PrimaryButton type="submit" disabled={saveMutation.isPending} className="w-full justify-center">
              Save DPC
            </PrimaryButton>
            {saveMutation.isError && (
              <ErrorState message={describeApiError(saveMutation.error, 'Could not save the DPC.')} />
            )}
          </form>
        </Modal>
      )}

      {decommissioning && (
        <Modal title={`Decommission ${decommissioning.name}?`} onClose={closeDecommission}>
          <div className="space-y-3">
            <p className="text-sm text-slate-600">
              This deactivates <strong>{decommissioning.code} - {decommissioning.name}</strong> and removes it from active
              use. It's rejected if active staff are still posted here.
            </p>
            {decommissionMutation.isError && (
              <FormErrorBanner
                title="Could not decommission"
                errors={describeApiErrorList(decommissionMutation.error, 'Could not decommission the DPC.')}
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
                placeholder="Why is this DPC being decommissioned?"
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
