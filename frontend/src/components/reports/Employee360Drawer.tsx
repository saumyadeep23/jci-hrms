import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Landmark, Pencil } from 'lucide-react'
import { apiClient } from '../../api/client'
import { formatDate } from '../../lib/date'
import { Modal } from '../common/Modal'
import { Badge, ErrorState, LoadingState, SecondaryButton } from '../common/ui'
import { ServiceBookTab } from './ServiceBookTab'
import { MinistryExtensionModal } from './MinistryExtensionModal'
import { EditEmployeeModal } from '../employee/EditEmployeeModal'
import type { EmployeeResponse, Employee360Response, SuperannuationCalculationPreviewResponse } from '../../types/api'

const TABS = ['Profile', 'Service Book'] as const
type Tab = (typeof TABS)[number]

/** Reporting hub drill-through: the Employee 360 profile drawer (PIMS_SPEC.md reports task, Section 4). */
export function Employee360Drawer({ employeeId, onClose }: { employeeId: number; onClose: () => void }) {
  const [tab, setTab] = useState<Tab>('Profile')
  const [ministryExtensionOpen, setMinistryExtensionOpen] = useState(false)
  const [editOpen, setEditOpen] = useState(false)

  const { data, isLoading, isError } = useQuery({
    queryKey: ['employee-360', employeeId],
    queryFn: async () => (await apiClient.get<Employee360Response>(`/employees/${employeeId}/360`)).data,
  })

  const previewQuery = useQuery({
    queryKey: ['superannuation-calculation-preview', employeeId],
    queryFn: async () =>
      (await apiClient.get<SuperannuationCalculationPreviewResponse>(`/v1/employees/${employeeId}/superannuation/calculation-preview`)).data,
  })

  /**
   * NPS/EPS eligibility (V55) isn't on vw_jci_employee_master_360 - that view
   * is one of pay_scale_master's protected dependents (see CLAUDE.md-adjacent
   * standing constraint), so this is deliberately a second, independent
   * fetch off the plain employee resource rather than a view/DTO change.
   */
  const employeeQuery = useQuery({
    queryKey: ['employee', employeeId],
    queryFn: async () => (await apiClient.get<EmployeeResponse>(`/employees/${employeeId}`)).data,
  })

  return (
    <Modal title={data ? `${data.fullName} (${data.employeeCode})` : 'Employee 360'} onClose={onClose} maxWidthClassName="max-w-2xl">
      <div className="mb-4 flex items-center justify-between gap-2">
        <div className="flex gap-2">
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
        <SecondaryButton onClick={() => setEditOpen(true)}>
          <Pencil size={14} /> Edit
        </SecondaryButton>
      </div>

      {isLoading && <LoadingState label="Loading employee profile..." />}
      {isError && <ErrorState message="Could not load this employee's 360 profile." />}

      {data && tab === 'Profile' && (
        <div className="space-y-4">
          <div className="flex flex-wrap gap-2">
            <Badge tone="brand">{data.employmentCategory ?? 'N/A'}</Badge>
            <Badge tone={data.employmentStatus === 'ACTIVE' ? 'success' : 'neutral'}>{data.employmentStatus}</Badge>
            {data.isBoardDirector && <Badge tone="warning">Board Director</Badge>}
            {data.isPwbd && <Badge tone="neutral">PwBD</Badge>}
          </div>

          <Section title="Identity">
            <Grid
              rows={[
                ['CPF A/C No.', data.cpfAcNo],
                ['Gender', data.gender],
                ['DOB', formatDate(data.dateOfBirth)],
                ['Marital Status', data.maritalStatus],
                ['Blood Group', data.bloodGroup],
                ['PAN', data.panNumber],
                ['Social Category', data.socialCategory],
              ]}
            />
          </Section>

          <Section title="Contact">
            <Grid
              rows={[
                ['Personal Email', data.personalEmail],
                ['Official Email', data.officialEmail],
                ['Personal Mobile', data.personalMobile],
                ['Official Mobile', data.officialMobile],
              ]}
            />
          </Section>

          <Section title="Posting">
            <Grid
              rows={[
                ['Post', data.currentPostTitle ? `${data.currentPostCode} - ${data.currentPostTitle}` : 'Unassigned'],
                ['Assignment Type', data.currentAssignmentType],
                ['Department', data.departmentName],
                ['Designation', data.designationTitle],
                ['Regional Office', data.roName],
                ['DPC', data.dpcName],
                ['Date of Joining', formatDate(data.dateOfJoining)],
              ]}
            />
          </Section>

          <Section title="Compensation">
            <Grid
              rows={[
                ['Tier', data.compensationTierSummary],
                ['Vendor (Outsourced)', data.outsourcedVendorName],
              ]}
            />
          </Section>

          <Section title="Banking">
            <Grid
              rows={[
                ['Bank', data.activeBankName ? `${data.activeBankName} - ${data.activeBankBranch}` : null],
                ['Account No.', data.activeBankAccountNo],
                ['IFSC', data.activeBankIfsc],
                ['Verification Status', data.bankVerificationStatus],
              ]}
            />
          </Section>

          <Section title="Superannuation">
            <Grid
              rows={[
                ['Superannuation Date', formatDate(data.superannuationDate)],
                ['Calculation Basis', data.superannuationCalculationBasis],
                ['Pension Settlement', data.pensionSettlementStatus],
              ]}
            />
            {previewQuery.data && (
              <div className="mt-2 rounded-md bg-slate-50 p-2 text-xs text-slate-500">
                <p>Regular (58y) projection: {formatDate(previewQuery.data.regularSuperannuationDate58)}</p>
                <p>Director (60y) projection: {formatDate(previewQuery.data.directorSuperannuationDate60)}</p>
                {previewQuery.data.director5YearTermDate && (
                  <p>Director 5-year term: {formatDate(previewQuery.data.director5YearTermDate)}</p>
                )}
                {previewQuery.data.isMinistryExtended && <p>Ministry extended to: {formatDate(previewQuery.data.ministryExtendedUpto)}</p>}
              </div>
            )}
            {data.isBoardDirector && (
              <SecondaryButton onClick={() => setMinistryExtensionOpen(true)} className="mt-2">
                <Landmark size={14} /> Ministry Extension
              </SecondaryButton>
            )}
          </Section>

          {employeeQuery.data && (
            <Section title="Pension Schemes">
              <Grid
                rows={[
                  [
                    'NPS',
                    employeeQuery.data.isNpsEligible
                      ? `Eligible${employeeQuery.data.pranNumber ? ` (PRAN ${employeeQuery.data.pranNumber})` : ' (PRAN not on record)'}`
                      : 'Not Enrolled',
                  ],
                  [
                    'EPS',
                    !employeeQuery.data.isEpsEligible
                      ? 'Not Enrolled'
                      : employeeQuery.data.isEpsHigherPensionEligible
                        ? 'Enrolled (Higher Pension on Actual Wages)'
                        : 'Enrolled (Statutory Ceiling ₹15k)',
                  ],
                ]}
              />
            </Section>
          )}
        </div>
      )}

      {tab === 'Service Book' && <ServiceBookTab employeeId={employeeId} />}

      {ministryExtensionOpen && <MinistryExtensionModal employeeId={employeeId} onClose={() => setMinistryExtensionOpen(false)} />}
      {editOpen && <EditEmployeeModal employeeId={employeeId} onClose={() => setEditOpen(false)} />}
    </Modal>
  )
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div>
      <p className="mb-1 text-xs font-semibold uppercase tracking-wide text-slate-500">{title}</p>
      {children}
    </div>
  )
}

function Grid({ rows }: { rows: [string, string | number | null | undefined][] }) {
  return (
    <div className="grid grid-cols-2 gap-x-4 gap-y-1 text-sm">
      {rows.map(([label, val]) => (
        <div key={label} className="flex justify-between gap-2 border-b border-slate-50 py-1">
          <span className="text-slate-400">{label}</span>
          <span className="text-right text-slate-700">{val ?? '—'}</span>
        </div>
      ))}
    </div>
  )
}
