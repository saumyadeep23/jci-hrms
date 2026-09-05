import { Plus, Trash2 } from 'lucide-react'
import { DatePicker } from '../../../../components/common/DatePicker'
import { SecondaryButton } from '../../../../components/common/ui'
import type { PastServiceOrganizationType, PastServicePayScalePattern } from '../../../../types/api'
import { emptyPastService, type LocalPastService } from '../onboardingTypes'
import { DocumentUploadField } from '../DocumentUploadField'

const ORG_TYPES: PastServiceOrganizationType[] = [
  'CENTRAL_GOVT',
  'STATE_GOVT',
  'CENTRAL_PSU',
  'STATE_PSU',
  'AUTONOMOUS_BODY',
  'DEFENCE_ARMY_NAVY_AIRFORCE',
  'PRIVATE_SECTOR',
  'OTHER',
]
const PAY_SCALE_PATTERNS: PastServicePayScalePattern[] = ['IDA', 'CDA', 'CONSOLIDATED', 'OTHER']

/** PIMS_SPEC.md Onboarding Step 5: Past Service Records (repeater, qualifying-service flag, relieving NOC upload). */
export function Step5PastService({ value, onChange }: { value: LocalPastService[]; onChange: (next: LocalPastService[]) => void }) {
  function updateRow(key: string, patch: Partial<LocalPastService>) {
    onChange(value.map((row) => (row.key === key ? { ...row, ...patch } : row)))
  }
  function removeRow(key: string) {
    onChange(value.filter((row) => row.key !== key))
  }

  return (
    <div className="space-y-4">
      {value.length === 0 && <p className="text-sm text-slate-500">No past service records. Add one if the candidate has prior employment.</p>}

      {value.map((row, index) => (
        <div key={row.key} className="rounded-md border border-slate-200 p-3">
          <div className="mb-2 flex items-center justify-between">
            <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Past Service {index + 1}</p>
            <button type="button" onClick={() => removeRow(row.key)} className="rounded-md p-1 text-red-500 hover:bg-red-50" title="Remove">
              <Trash2 size={14} />
            </button>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Organization Name</label>
              <input
                required
                value={row.organizationName}
                onChange={(e) => updateRow(row.key, { organizationName: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Organization Type</label>
              <select
                value={row.organizationType}
                onChange={(e) => updateRow(row.key, { organizationType: e.target.value as PastServiceOrganizationType })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              >
                {ORG_TYPES.map((t) => (
                  <option key={t} value={t}>
                    {t.replace(/_/g, ' ')}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Designation Held</label>
              <input
                required
                value={row.designationHeld}
                onChange={(e) => updateRow(row.key, { designationHeld: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Pay Scale Pattern</label>
              <select
                value={row.lastPayScalePattern}
                onChange={(e) => updateRow(row.key, { lastPayScalePattern: e.target.value as PastServicePayScalePattern })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              >
                <option value="">Unknown</option>
                {PAY_SCALE_PATTERNS.map((p) => (
                  <option key={p} value={p}>
                    {p}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">From Date</label>
              <DatePicker value={row.fromDate} onChange={(v) => updateRow(row.key, { fromDate: v })} />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">To Date</label>
              <DatePicker value={row.toDate} onChange={(v) => updateRow(row.key, { toDate: v })} />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Last Drawn Basic</label>
              <input
                type="number"
                step="0.01"
                value={row.lastDrawnBasic}
                onChange={(e) => updateRow(row.key, { lastDrawnBasic: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Last Drawn Gross</label>
              <input
                type="number"
                step="0.01"
                value={row.lastDrawnGross}
                onChange={(e) => updateRow(row.key, { lastDrawnGross: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            <div className="col-span-2">
              <label className="mb-1 block text-xs font-medium text-slate-600">Reason for Leaving</label>
              <input
                value={row.reasonForLeaving}
                onChange={(e) => updateRow(row.key, { reasonForLeaving: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>

            <label className="flex items-center gap-2 text-sm text-slate-700">
              <input
                type="checkbox"
                checked={row.qualifyingForPensionGratuity}
                onChange={(e) => updateRow(row.key, { qualifyingForPensionGratuity: e.target.checked })}
              />
              Qualifying service (pension/gratuity)
            </label>
            {row.qualifyingForPensionGratuity && (
              <div>
                <label className="mb-1 block text-xs font-medium text-slate-600">Qualifying Service Order Ref</label>
                <input
                  value={row.qualifyingServiceOrderRef}
                  onChange={(e) => updateRow(row.key, { qualifyingServiceOrderRef: e.target.value })}
                  className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                />
              </div>
            )}

            <div>
              <DocumentUploadField
                label="Experience Certificate"
                category="PAST_SERVICE_NOC"
                maxSizeLabel="5MB (PDF)"
                acceptHint=".pdf"
                currentFileName={row.experienceCertificateS3Key ? row.experienceCertificateS3Key.split('/').pop() : null}
                onUploaded={(res) => updateRow(row.key, { experienceCertificateS3Key: res.fileS3Key })}
              />
            </div>
            <div>
              <DocumentUploadField
                label="Relieving / NOC Document"
                category="PAST_SERVICE_NOC"
                maxSizeLabel="5MB (PDF)"
                acceptHint=".pdf"
                currentFileName={row.relievingNocDocumentS3Key ? row.relievingNocDocumentS3Key.split('/').pop() : null}
                onUploaded={(res) => updateRow(row.key, { relievingNocDocumentS3Key: res.fileS3Key })}
              />
            </div>
          </div>
        </div>
      ))}

      <SecondaryButton onClick={() => onChange([...value, emptyPastService()])}>
        <Plus size={14} /> Add Past Service Record
      </SecondaryButton>
    </div>
  )
}
