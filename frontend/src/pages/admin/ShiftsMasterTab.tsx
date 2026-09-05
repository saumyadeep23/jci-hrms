import { Badge, errorInputClass, FieldError } from '../../components/common/ui'
import { MasterDataPanel } from '../../components/admin/MasterDataPanel'
import type { ShiftApplicableOfficeType, ShiftMasterRequest, ShiftMasterResponse } from '../../types/api'

type ShiftForm = {
  shiftCode: string
  shiftName: string
  startTime: string
  endTime: string
  gracePeriodMinutes: string
  crossesMidnight: boolean
  fullDayMinutes: string
  halfDayMinutes: string
  applicableOfficeType: ShiftApplicableOfficeType | ''
  active: boolean
}

const EMPTY_FORM: ShiftForm = {
  shiftCode: '',
  shiftName: '',
  startTime: '',
  endTime: '',
  gracePeriodMinutes: '0',
  crossesMidnight: false,
  fullDayMinutes: '',
  halfDayMinutes: '',
  applicableOfficeType: '',
  active: true,
}

const OFFICE_TYPE_LABELS: Record<ShiftApplicableOfficeType, string> = {
  HEAD_OFFICE: 'Head Office',
  REGIONAL_OFFICE: 'Regional Office',
  DPC: 'DPC (6-day week)',
}

/** LocalTime comes back from the backend as "HH:mm:ss" - a native <input type="time"> only wants "HH:mm". */
function toTimeInputValue(hhmmss: string): string {
  return hhmmss.slice(0, 5)
}

/** Master Data Console's Shift Master tab (Watchmen/Security/General rosters, plus HO/RO and DPC's own default shifts) - /api/v1/attendance/shifts. */
export function ShiftsMasterTab() {
  return (
    <MasterDataPanel<ShiftMasterResponse, ShiftForm>
      title="Shift Master"
      description="Named shifts (HO/RO, DPC 6-day week, Watchmen/Security rotations) with grace period, overnight, and full/half-day handling"
      basePath="/v1/attendance/shifts"
      queryKey="admin-masters-shifts"
      searchPlaceholder="Search by shift code or name..."
      matchesSearch={(row, q) => row.shiftCode.toLowerCase().includes(q) || row.shiftName.toLowerCase().includes(q)}
      idOf={(row) => row.id}
      getIsActive={(row) => row.active}
      emptyForm={EMPTY_FORM}
      formTitleFor={(editing) => (editing ? `Edit ${editing.shiftName}` : 'Add Shift')}
      fromRowToForm={(row) => ({
        shiftCode: row.shiftCode,
        shiftName: row.shiftName,
        startTime: toTimeInputValue(row.startTime),
        endTime: toTimeInputValue(row.endTime),
        gracePeriodMinutes: String(row.gracePeriodMinutes),
        crossesMidnight: row.crossesMidnight,
        fullDayMinutes: row.fullDayMinutes != null ? String(row.fullDayMinutes) : '',
        halfDayMinutes: row.halfDayMinutes != null ? String(row.halfDayMinutes) : '',
        applicableOfficeType: row.applicableOfficeType ?? '',
        active: row.active,
      })}
      toRequest={(form): ShiftMasterRequest => ({
        shiftCode: form.shiftCode.trim().toUpperCase(),
        shiftName: form.shiftName.trim(),
        startTime: form.startTime,
        endTime: form.endTime,
        gracePeriodMinutes: Number(form.gracePeriodMinutes),
        crossesMidnight: form.crossesMidnight,
        fullDayMinutes: form.fullDayMinutes === '' ? null : Number(form.fullDayMinutes),
        halfDayMinutes: form.halfDayMinutes === '' ? null : Number(form.halfDayMinutes),
        applicableOfficeType: form.applicableOfficeType === '' ? null : form.applicableOfficeType,
        active: form.active,
      })}
      formValid={(form) =>
        form.shiftCode.trim().length > 0 &&
        form.shiftName.trim().length > 0 &&
        form.startTime !== '' &&
        form.endTime !== '' &&
        form.gracePeriodMinutes !== '' &&
        Number(form.gracePeriodMinutes) >= 0 &&
        (form.crossesMidnight || form.endTime > form.startTime)
      }
      columns={[
        { key: 'shiftCode', label: 'Shift Code', render: (row) => <span className="font-medium">{row.shiftCode}</span> },
        { key: 'shiftName', label: 'Shift Name', render: (row) => row.shiftName },
        { key: 'startTime', label: 'Start Time', render: (row) => toTimeInputValue(row.startTime) },
        { key: 'endTime', label: 'End Time', render: (row) => toTimeInputValue(row.endTime) },
        { key: 'gracePeriodMinutes', label: 'Grace Period', align: 'right', render: (row) => `${row.gracePeriodMinutes} min` },
        {
          key: 'fullHalfDay',
          label: 'Full / Half Day',
          render: (row) => (row.fullDayMinutes != null ? `${row.fullDayMinutes} / ${row.halfDayMinutes ?? '—'} min` : '—'),
        },
        {
          key: 'crossesMidnight',
          label: 'Crosses Midnight',
          render: (row) => <Badge tone={row.crossesMidnight ? 'warning' : 'neutral'}>{row.crossesMidnight ? 'Yes' : 'No'}</Badge>,
        },
        {
          key: 'applicableOfficeType',
          label: 'Applicable Office',
          render: (row) =>
            row.applicableOfficeType ? (
              <Badge tone="brand">{OFFICE_TYPE_LABELS[row.applicableOfficeType]}</Badge>
            ) : (
              <span className="text-xs text-slate-400">Roster-assigned</span>
            ),
        },
      ]}
      renderForm={(form, setForm, fieldErrors) => (
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Shift Code</label>
            <input
              required
              maxLength={20}
              value={form.shiftCode}
              onChange={(e) => setForm({ ...form, shiftCode: e.target.value.toUpperCase() })}
              placeholder="e.g. SHIFT_A"
              className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm uppercase ${errorInputClass(Boolean(fieldErrors?.shiftCode))}`}
            />
            <FieldError message={fieldErrors?.shiftCode} />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Shift Name</label>
            <input
              required
              maxLength={100}
              value={form.shiftName}
              onChange={(e) => setForm({ ...form, shiftName: e.target.value })}
              placeholder="e.g. Shift A (Morning)"
              className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(fieldErrors?.shiftName))}`}
            />
            <FieldError message={fieldErrors?.shiftName} />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Start Time</label>
            <input
              required
              type="time"
              value={form.startTime}
              onChange={(e) => setForm({ ...form, startTime: e.target.value })}
              className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(fieldErrors?.startTime))}`}
            />
            <FieldError message={fieldErrors?.startTime} />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">End Time</label>
            <input
              required
              type="time"
              value={form.endTime}
              onChange={(e) => setForm({ ...form, endTime: e.target.value })}
              className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(fieldErrors?.endTime))}`}
            />
            {!form.crossesMidnight && form.startTime !== '' && form.endTime !== '' && form.endTime <= form.startTime && (
              <p className="mt-1 text-xs text-red-600">Must be after Start Time, unless this shift crosses midnight.</p>
            )}
            <FieldError message={fieldErrors?.endTime} />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Grace Period (minutes)</label>
            <input
              required
              type="number"
              min="0"
              step="1"
              value={form.gracePeriodMinutes}
              onChange={(e) => setForm({ ...form, gracePeriodMinutes: e.target.value })}
              className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(fieldErrors?.gracePeriodMinutes))}`}
            />
            <FieldError message={fieldErrors?.gracePeriodMinutes} />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Applicable Office Type</label>
            <select
              value={form.applicableOfficeType}
              onChange={(e) => setForm({ ...form, applicableOfficeType: e.target.value as ShiftApplicableOfficeType | '' })}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            >
              <option value="">Not office-specific (roster-assigned)</option>
              {(Object.keys(OFFICE_TYPE_LABELS) as ShiftApplicableOfficeType[]).map((type) => (
                <option key={type} value={type}>
                  {OFFICE_TYPE_LABELS[type]}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Full Day (minutes, optional)</label>
            <input
              type="number"
              min="0"
              step="1"
              value={form.fullDayMinutes}
              onChange={(e) => setForm({ ...form, fullDayMinutes: e.target.value })}
              placeholder="e.g. 510"
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Half Day (minutes, optional)</label>
            <input
              type="number"
              min="0"
              step="1"
              value={form.halfDayMinutes}
              onChange={(e) => setForm({ ...form, halfDayMinutes: e.target.value })}
              placeholder="e.g. 255"
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
          <label className="flex items-center gap-2 self-end pb-2 text-sm text-slate-600">
            <input
              type="checkbox"
              checked={form.crossesMidnight}
              onChange={(e) => setForm({ ...form, crossesMidnight: e.target.checked })}
            />
            Crosses midnight (overnight shift)
          </label>
          <label className="flex items-center gap-2 self-end pb-2 text-sm text-slate-600">
            <input type="checkbox" checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} />
            Active
          </label>
        </div>
      )}
    />
  )
}
