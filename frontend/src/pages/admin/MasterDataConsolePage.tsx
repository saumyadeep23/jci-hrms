import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router-dom'
import { isAxiosError } from 'axios'
import { CalendarCheck, CalendarDays, ShieldCheck } from 'lucide-react'
import { useAuth } from '../../auth/AuthContext'
import { apiClient } from '../../api/client'
import { formatDate } from '../../lib/date'
import { MasterDataPanel } from '../../components/admin/MasterDataPanel'
import { DatePicker } from '../../components/common/DatePicker'
import { FieldError, errorInputClass } from '../../components/common/ui'
import { StateMasterPage } from './StateMasterPage'
import { DistrictMasterPage } from './DistrictMasterPage'
import { RoMasterPage } from './RoMasterPage'
import { DpcMasterPage } from './DpcMasterPage'
import { ShiftsMasterTab } from './ShiftsMasterTab'
import { GradeScalesTab } from './master/GradeScalesTab'
import type {
  DepartmentRequest,
  DepartmentResponse,
  DesignationRequest,
  DesignationResponse,
  GradeScaleMasterResponse,
  VendorMasterRequest,
  VendorMasterResponse,
} from '../../types/api'

type DepartmentForm = { code: string; name: string; description: string }

function DepartmentsPanel() {
  return (
    <MasterDataPanel<DepartmentResponse, DepartmentForm>
      title="Departments"
      description="Functional departments a post, designation, or employee can belong to"
      basePath="/v1/admin/masters/departments"
      queryKey="admin-masters-departments"
      searchPlaceholder="Search by code or name..."
      matchesSearch={(row, q) => row.code.toLowerCase().includes(q) || row.name.toLowerCase().includes(q)}
      idOf={(row) => row.id}
      getIsActive={(row) => row.deletedAt === null}
      emptyForm={{ code: '', name: '', description: '' }}
      formTitleFor={(editing) => (editing ? `Edit ${editing.name}` : 'Add Department')}
      fromRowToForm={(row) => ({ code: row.code, name: row.name, description: row.description ?? '' })}
      toRequest={(form): DepartmentRequest => ({ code: form.code, name: form.name, description: form.description || null })}
      formValid={(form) => form.code.trim().length > 0 && form.name.trim().length > 0}
      columns={[
        { key: 'code', label: 'Code', render: (row) => <span className="font-medium">{row.code}</span> },
        { key: 'name', label: 'Name', render: (row) => row.name },
        { key: 'description', label: 'Description', render: (row) => row.description ?? '—' },
      ]}
      renderForm={(form, setForm, fieldErrors) => (
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Code</label>
            <input
              required
              maxLength={20}
              value={form.code}
              onChange={(e) => setForm({ ...form, code: e.target.value })}
              className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(fieldErrors?.code))}`}
            />
            <FieldError message={fieldErrors?.code} />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Name</label>
            <input
              required
              maxLength={100}
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
              className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(fieldErrors?.name))}`}
            />
            <FieldError message={fieldErrors?.name} />
          </div>
          <div className="col-span-2">
            <label className="mb-1 block text-xs font-medium text-slate-600">Description</label>
            <textarea
              maxLength={255}
              value={form.description}
              onChange={(e) => setForm({ ...form, description: e.target.value })}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              rows={2}
            />
          </div>
        </div>
      )}
    />
  )
}

type DesignationForm = { title: string; description: string; gradeScaleId: string }

function useGradeScaleOptions(): GradeScaleMasterResponse[] {
  const { data } = useQuery({
    queryKey: ['grade-scales'],
    queryFn: async () => (await apiClient.get<GradeScaleMasterResponse[]>('/v1/masters/grade-scales')).data,
  })
  return data ?? []
}

function DesignationsPanel() {
  const gradeScales = useGradeScaleOptions()

  return (
    <MasterDataPanel<DesignationResponse, DesignationForm>
      title="Designations"
      description="Job titles assignable to a sanctioned post or employee"
      basePath="/v1/admin/masters/designations"
      queryKey="admin-masters-designations"
      searchPlaceholder="Search by title..."
      matchesSearch={(row, q) => row.title.toLowerCase().includes(q)}
      idOf={(row) => row.id}
      getIsActive={(row) => row.deletedAt === null}
      emptyForm={{ title: '', description: '', gradeScaleId: '' }}
      formTitleFor={(editing) => (editing ? `Edit ${editing.title}` : 'Add Designation')}
      fromRowToForm={(row) => ({ title: row.title, description: row.description ?? '', gradeScaleId: row.gradeScaleId != null ? String(row.gradeScaleId) : '' })}
      toRequest={(form): DesignationRequest => ({
        title: form.title,
        description: form.description || null,
        gradeScaleId: form.gradeScaleId ? Number(form.gradeScaleId) : null,
      })}
      formValid={(form) => form.title.trim().length > 0}
      columns={[
        { key: 'title', label: 'Title', render: (row) => <span className="font-medium">{row.title}</span> },
        { key: 'description', label: 'Description', render: (row) => row.description ?? '—' },
        { key: 'grade', label: 'Grade Scale', render: (row) => (row.scaleCode ? `${row.scaleCode} (L${row.hierarchyLevel})` : '—') },
      ]}
      renderForm={(form, setForm, fieldErrors) => (
        <div className="space-y-3">
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Title</label>
            <input
              required
              maxLength={100}
              value={form.title}
              onChange={(e) => setForm({ ...form, title: e.target.value })}
              className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(fieldErrors?.title))}`}
            />
            <FieldError message={fieldErrors?.title} />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Description</label>
            <textarea
              maxLength={255}
              value={form.description}
              onChange={(e) => setForm({ ...form, description: e.target.value })}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              rows={2}
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Grade Scale (optional)</label>
            <select
              value={form.gradeScaleId}
              onChange={(e) => setForm({ ...form, gradeScaleId: e.target.value })}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            >
              <option value="">Not assigned</option>
              {gradeScales.map((g) => (
                <option key={g.id} value={g.id}>
                  {g.scaleCode} - {g.cadre} (L{g.hierarchyLevel})
                </option>
              ))}
            </select>
            <p className="mt-1 text-xs text-slate-400">Drives promotion-eligibility hierarchy comparisons in the movement order workflow.</p>
          </div>
        </div>
      )}
    />
  )
}

type VendorForm = {
  vendorCode: string
  vendorName: string
  tradeName: string
  gstin: string
  panNumber: string
  epfRegistrationNo: string
  esicRegistrationNo: string
  contractStartDate: string
  contractEndDate: string
  contactPerson: string
  contactPhone: string
  contactEmail: string
  officeAddress: string
  serviceChargePercentage: string
  active: boolean
}

const GSTIN_PATTERN = /^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$/
const PAN_PATTERN = /^[A-Z]{5}[0-9]{4}[A-Z]$/

function VendorsPanel() {
  return (
    <MasterDataPanel<VendorMasterResponse, VendorForm>
      title="Manpower Vendors"
      description="Outsourcing agencies supplying contract/outsourced manpower"
      basePath="/v1/admin/masters/vendors"
      queryKey="admin-masters-vendors"
      searchPlaceholder="Search by code, name, or GSTIN..."
      matchesSearch={(row, q) =>
        row.vendorCode.toLowerCase().includes(q) ||
        row.vendorName.toLowerCase().includes(q) ||
        (row.gstin ?? '').toLowerCase().includes(q)
      }
      idOf={(row) => row.id}
      getIsActive={(row) => row.active}
      emptyForm={{
        vendorCode: '',
        vendorName: '',
        tradeName: '',
        gstin: '',
        panNumber: '',
        epfRegistrationNo: '',
        esicRegistrationNo: '',
        contractStartDate: '',
        contractEndDate: '',
        contactPerson: '',
        contactPhone: '',
        contactEmail: '',
        officeAddress: '',
        serviceChargePercentage: '',
        active: true,
      }}
      formTitleFor={(editing) => (editing ? `Edit ${editing.vendorName}` : 'Add Vendor')}
      fromRowToForm={(row) => ({
        vendorCode: row.vendorCode,
        vendorName: row.vendorName,
        tradeName: row.tradeName ?? '',
        gstin: row.gstin ?? '',
        panNumber: row.panNumber ?? '',
        epfRegistrationNo: row.epfRegistrationNo ?? '',
        esicRegistrationNo: row.esicRegistrationNo ?? '',
        contractStartDate: row.contractStartDate,
        contractEndDate: row.contractEndDate,
        contactPerson: row.contactPerson ?? '',
        contactPhone: row.contactPhone ?? '',
        contactEmail: row.contactEmail ?? '',
        officeAddress: row.officeAddress ?? '',
        serviceChargePercentage: row.serviceChargePercentage != null ? String(row.serviceChargePercentage) : '',
        active: row.active,
      })}
      toRequest={(form): VendorMasterRequest => ({
        vendorName: form.vendorName,
        tradeName: form.tradeName || null,
        gstin: form.gstin || null,
        panNumber: form.panNumber || null,
        epfRegistrationNo: form.epfRegistrationNo || null,
        esicRegistrationNo: form.esicRegistrationNo || null,
        contractStartDate: form.contractStartDate,
        contractEndDate: form.contractEndDate,
        contactPerson: form.contactPerson || null,
        contactPhone: form.contactPhone || null,
        contactEmail: form.contactEmail || null,
        officeAddress: form.officeAddress || null,
        serviceChargePercentage: form.serviceChargePercentage ? Number(form.serviceChargePercentage) : null,
        active: form.active,
      })}
      formValid={(form) =>
        form.vendorName.trim().length > 0 &&
        form.contractStartDate !== '' &&
        form.contractEndDate !== '' &&
        form.contractEndDate >= form.contractStartDate &&
        (form.gstin === '' || GSTIN_PATTERN.test(form.gstin)) &&
        (form.panNumber === '' || PAN_PATTERN.test(form.panNumber))
      }
      columns={[
        { key: 'vendorCode', label: 'Code', render: (row) => <span className="font-medium">{row.vendorCode}</span> },
        { key: 'vendorName', label: 'Agency', render: (row) => row.vendorName },
        { key: 'gstin', label: 'GSTIN', render: (row) => row.gstin ?? '—' },
        {
          key: 'contract',
          label: 'Contract Validity',
          render: (row) => `${formatDate(row.contractStartDate)} → ${formatDate(row.contractEndDate)}`,
        },
      ]}
      renderForm={(form, setForm, fieldErrors, saveError) => {
        const gstinConflict =
          isAxiosError(saveError) &&
          saveError.response?.status === 409 &&
          typeof saveError.response.data === 'object' &&
          saveError.response.data !== null &&
          String((saveError.response.data as { message?: unknown }).message ?? '')
            .toLowerCase()
            .includes('gstin')
        return (
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Vendor Code</label>
            <input
              disabled
              readOnly
              value={form.vendorCode}
              placeholder="Auto-generated (e.g., VND-0001)"
              className="w-full rounded-md border border-slate-300 bg-slate-50 px-3 py-2 text-sm text-slate-500"
            />
            <p className="mt-1 text-xs text-slate-400">System will assign sequence upon submission</p>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Agency Name</label>
            <input
              required
              value={form.vendorName}
              onChange={(e) => setForm({ ...form, vendorName: e.target.value })}
              className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(fieldErrors?.vendorName))}`}
            />
            <FieldError message={fieldErrors?.vendorName} />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Trade Name</label>
            <input
              value={form.tradeName}
              onChange={(e) => setForm({ ...form, tradeName: e.target.value })}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Service Charge %</label>
            <input
              type="number"
              step="0.01"
              value={form.serviceChargePercentage}
              onChange={(e) => setForm({ ...form, serviceChargePercentage: e.target.value })}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">GSTIN</label>
            <input
              value={form.gstin}
              onChange={(e) => setForm({ ...form, gstin: e.target.value.toUpperCase() })}
              className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm uppercase ${errorInputClass(Boolean(fieldErrors?.gstin))}`}
              placeholder="22AAAAA0000A1Z5"
            />
            {form.gstin !== '' && !GSTIN_PATTERN.test(form.gstin) && (
              <p className="mt-1 text-xs text-red-600">Invalid GSTIN format.</p>
            )}
            {gstinConflict && <p className="mt-1 text-xs text-red-600">This GSTIN is already registered.</p>}
            <FieldError message={fieldErrors?.gstin} />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">PAN</label>
            <input
              value={form.panNumber}
              onChange={(e) => setForm({ ...form, panNumber: e.target.value.toUpperCase() })}
              className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm uppercase ${errorInputClass(Boolean(fieldErrors?.panNumber))}`}
              placeholder="ABCDE1234F"
            />
            {form.panNumber !== '' && !PAN_PATTERN.test(form.panNumber) && (
              <p className="mt-1 text-xs text-red-600">Invalid PAN format.</p>
            )}
            <FieldError message={fieldErrors?.panNumber} />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">EPF Registration No.</label>
            <input
              value={form.epfRegistrationNo}
              onChange={(e) => setForm({ ...form, epfRegistrationNo: e.target.value })}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">ESIC Registration No.</label>
            <input
              value={form.esicRegistrationNo}
              onChange={(e) => setForm({ ...form, esicRegistrationNo: e.target.value })}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Contract Start</label>
            <DatePicker
              value={form.contractStartDate}
              onChange={(v) => setForm({ ...form, contractStartDate: v })}
              hasError={Boolean(fieldErrors?.contractStartDate)}
            />
            <FieldError message={fieldErrors?.contractStartDate} />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Contract End</label>
            <DatePicker
              value={form.contractEndDate}
              onChange={(v) => setForm({ ...form, contractEndDate: v })}
              min={form.contractStartDate || undefined}
              hasError={Boolean(fieldErrors?.contractEndDate)}
            />
            {form.contractEndDate !== '' && form.contractStartDate !== '' && form.contractEndDate < form.contractStartDate && (
              <p className="mt-1 text-xs text-red-600">Must not be before contract start.</p>
            )}
            <FieldError message={fieldErrors?.contractEndDate} />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Contact Person</label>
            <input
              value={form.contactPerson}
              onChange={(e) => setForm({ ...form, contactPerson: e.target.value })}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Contact Phone</label>
            <input
              value={form.contactPhone}
              onChange={(e) => setForm({ ...form, contactPhone: e.target.value })}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
          <div className="col-span-2">
            <label className="mb-1 block text-xs font-medium text-slate-600">Contact Email</label>
            <input
              type="email"
              value={form.contactEmail}
              onChange={(e) => setForm({ ...form, contactEmail: e.target.value })}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
          <div className="col-span-2">
            <label className="mb-1 block text-xs font-medium text-slate-600">Office Address</label>
            <textarea
              value={form.officeAddress}
              onChange={(e) => setForm({ ...form, officeAddress: e.target.value })}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              rows={2}
            />
          </div>
          <label className="col-span-2 flex items-center gap-2 text-sm text-slate-600">
            <input type="checkbox" checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} />
            Active
          </label>
        </div>
        )
      }}
    />
  )
}

const TABS = [
  { key: 'departments', label: 'Departments' },
  { key: 'designations', label: 'Designations' },
  { key: 'grade-scales', label: 'Grade & Pay Scales' },
  { key: 'regional-offices', label: 'Regional Offices' },
  { key: 'dpcs', label: 'DPCs' },
  // States/Districts are SUPER_ADMIN-only at the API (StateMasterController/
  // DistrictMasterController), unlike every other tab here - hidden from
  // HR_ADMIN below rather than shown and then 403ing.
  { key: 'states', label: 'States', superAdminOnly: true },
  { key: 'districts', label: 'Districts', superAdminOnly: true },
  { key: 'shifts', label: 'Shift Master' },
  { key: 'vendors', label: 'Manpower Vendors' },
] as const
type TabKey = (typeof TABS)[number]['key']
const TAB_KEYS: readonly string[] = TABS.map((t) => t.key)

function isTabKey(value: string | null): value is TabKey {
  return value !== null && TAB_KEYS.includes(value)
}

/**
 * Link-out tiles for admin pages that are their own standalone workflow (own
 * sub-tabs, wider than the generic CRUD panel below) rather than an
 * embeddable panel - UAT issue #3 moved Leave Types and Holiday Calendars'
 * standalone sidebar links here, alongside Functional Roles.
 */
const LINK_OUT_TILES = [
  { to: '/admin/master/functional-roles', label: 'Functional & Statutory Roles', icon: ShieldCheck },
  { to: '/admin/master/leave-types', label: 'Leave Types & Cadre Eligibility', icon: CalendarCheck },
  { to: '/admin/master/holidays', label: 'Holiday & RH Calendars', icon: CalendarDays },
]

/** PIMS_SPEC.md Section 1: unified Master Data Administration Console. Initial tab can be deep-linked via ?tab=. */
export function MasterDataConsolePage() {
  const { hasRole } = useAuth()
  const [searchParams, setSearchParams] = useSearchParams()
  const requestedTab = searchParams.get('tab')
  const [tab, setTab] = useState<TabKey>(isTabKey(requestedTab) ? requestedTab : 'departments')

  const visibleTabs = TABS.filter((t) => !('superAdminOnly' in t) || hasRole('SUPER_ADMIN'))

  function selectTab(next: TabKey) {
    setTab(next)
    setSearchParams({ tab: next }, { replace: true })
  }

  return (
    <div className="flex flex-col gap-6 lg:flex-row">
      <nav className="flex shrink-0 gap-1 overflow-x-auto lg:w-56 lg:flex-col lg:overflow-visible">
        {visibleTabs.map((t) => (
          <button
            key={t.key}
            type="button"
            onClick={() => selectTab(t.key)}
            className={`whitespace-nowrap rounded-md px-3 py-2 text-left text-sm font-medium transition-colors ${
              tab === t.key ? 'bg-brand-forest text-white' : 'text-slate-600 hover:bg-slate-100'
            }`}
          >
            {t.label}
          </button>
        ))}

        <div className="mt-2 space-y-1 border-t border-slate-200 pt-3">
          {LINK_OUT_TILES.map((tile) => (
            <Link
              key={tile.to}
              to={tile.to}
              className="flex items-center gap-2 whitespace-nowrap rounded-md px-3 py-2 text-left text-sm font-medium text-slate-600 hover:bg-slate-100"
            >
              <tile.icon size={16} /> {tile.label}
            </Link>
          ))}
        </div>
      </nav>

      <div className="min-w-0 flex-1">
        {tab === 'departments' && <DepartmentsPanel />}
        {tab === 'designations' && <DesignationsPanel />}
        {tab === 'grade-scales' && <GradeScalesTab />}
        {tab === 'regional-offices' && <RoMasterPage />}
        {tab === 'dpcs' && <DpcMasterPage />}
        {tab === 'states' && hasRole('SUPER_ADMIN') && <StateMasterPage />}
        {tab === 'districts' && hasRole('SUPER_ADMIN') && <DistrictMasterPage />}
        {tab === 'shifts' && <ShiftsMasterTab />}
        {tab === 'vendors' && <VendorsPanel />}
      </div>
    </div>
  )
}
