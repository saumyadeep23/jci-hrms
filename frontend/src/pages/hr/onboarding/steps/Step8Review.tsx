import { CheckCircle2, XCircle } from 'lucide-react'
import { formatDate } from '../../../../lib/date'
import type { OnboardingPersonalDetailsRequest } from '../../../../types/api'
import {
  type LocalAddress,
  type LocalBanking,
  type LocalDocument,
  type LocalEmployment,
  type LocalFamily,
  type LocalPastService,
  type LocalQualification,
} from '../onboardingTypes'
import { DocumentUploadField } from '../DocumentUploadField'

/** PIMS_SPEC.md Onboarding Step 8: Document Verification & Final Review. */
export function Step8Review({
  personal,
  presentAddress,
  permanentAddress,
  permanentSameAsPresent,
  banking,
  qualifications,
  pastServiceRecords,
  employment,
  family,
  documents,
  onDocumentsChange,
}: {
  personal: OnboardingPersonalDetailsRequest
  presentAddress: LocalAddress
  permanentAddress: LocalAddress
  permanentSameAsPresent: boolean
  banking: LocalBanking
  qualifications: LocalQualification[]
  pastServiceRecords: LocalPastService[]
  employment: LocalEmployment
  family: LocalFamily
  documents: LocalDocument[]
  onDocumentsChange: (next: LocalDocument[]) => void
}) {
  const photo = documents.find((d) => d.documentCategory === 'PHOTO')
  const signature = documents.find((d) => d.documentCategory === 'SIGNATURE')

  function upsertDoc(category: 'PHOTO' | 'SIGNATURE', title: string, fileS3Key: string, mimeType: string, originalFileName: string) {
    const withoutThisCategory = documents.filter((d) => d.documentCategory !== category)
    onDocumentsChange([
      ...withoutThisCategory,
      { key: crypto.randomUUID(), documentCategory: category, documentTitle: title, fileS3Key, mimeType, originalFileName },
    ])
  }

  return (
    <div className="space-y-5">
      <div className="grid grid-cols-2 gap-3">
        <DocumentUploadField
          label="Photo"
          category="PHOTO"
          maxSizeLabel="1MB (JPG/PNG)"
          acceptHint=".jpg,.jpeg,.png"
          currentFileName={photo?.originalFileName}
          onUploaded={(res) => upsertDoc('PHOTO', 'Photo', res.fileS3Key, res.mimeType, res.originalFileName)}
        />
        <DocumentUploadField
          label="Signature"
          category="SIGNATURE"
          maxSizeLabel="500KB (JPG/PNG)"
          acceptHint=".jpg,.jpeg,.png"
          currentFileName={signature?.originalFileName}
          onUploaded={(res) => upsertDoc('SIGNATURE', 'Signature', res.fileS3Key, res.mimeType, res.originalFileName)}
        />
      </div>

      <ChecklistRow label="Photo uploaded" ok={Boolean(photo)} />
      <ChecklistRow label="Signature uploaded" ok={Boolean(signature)} />

      <div className="divide-y divide-slate-200 rounded-md border border-slate-200">
        <AccordionSection title="Personal & Bio-Data">
          <SummaryGrid
            rows={[
              ['Name', `${personal.salutation} ${personal.firstName} ${personal.middleName ?? ''} ${personal.lastName}`.replace(/\s+/g, ' ')],
              ['Gender', personal.gender],
              ['DOB', formatDate(personal.dateOfBirth)],
              ['Marital Status', personal.maritalStatus],
              ['PAN', personal.panNumber],
              ['CPF A/C No.', personal.cpfAcNo],
              ['Personal Email', personal.personalEmail],
              ['Phone', personal.phone],
            ]}
          />
        </AccordionSection>

        <AccordionSection title="Address">
          <p className="mb-1 text-xs font-semibold text-slate-500">Present</p>
          <SummaryGrid
            rows={[
              ['Address', `${presentAddress.addressLine1} ${presentAddress.addressLine2}`.trim()],
              ['City / District / State', `${presentAddress.city} / ${presentAddress.district} / ${presentAddress.state}`],
              ['PIN Code', presentAddress.pinCode],
            ]}
          />
          {!permanentSameAsPresent && (
            <>
              <p className="mb-1 mt-3 text-xs font-semibold text-slate-500">Permanent</p>
              <SummaryGrid
                rows={[
                  ['Address', `${permanentAddress.addressLine1} ${permanentAddress.addressLine2}`.trim()],
                  ['City / District / State', `${permanentAddress.city} / ${permanentAddress.district} / ${permanentAddress.state}`],
                  ['PIN Code', permanentAddress.pinCode],
                ]}
              />
            </>
          )}
        </AccordionSection>

        <AccordionSection title="Banking">
          <SummaryGrid
            rows={[
              ['Bank', `${banking.bankName} - ${banking.bankBranch}`],
              ['Account No.', banking.bankAccountNumber],
              ['IFSC', banking.bankIfsc],
              ['Bank Proof', banking.cancelledChequeS3Key ? 'Uploaded' : 'Not uploaded'],
            ]}
          />
        </AccordionSection>

        <AccordionSection title={`Qualifications (${qualifications.length})`}>
          {qualifications.length === 0 && <p className="text-sm text-slate-400">None added.</p>}
          {qualifications.map((q) => (
            <p key={q.key} className="text-sm text-slate-600">
              {q.degreeTitle} - {q.qualificationLevel.replace(/_/g, ' ')} ({q.passingYear}) {q.highestQualification && '★'}
            </p>
          ))}
        </AccordionSection>

        <AccordionSection title={`Past Service (${pastServiceRecords.length})`}>
          {pastServiceRecords.length === 0 && <p className="text-sm text-slate-400">None added.</p>}
          {pastServiceRecords.map((p) => (
            <p key={p.key} className="text-sm text-slate-600">
              {p.organizationName} - {p.designationHeld} ({formatDate(p.fromDate)} to {formatDate(p.toDate)})
            </p>
          ))}
        </AccordionSection>

        <AccordionSection title="Employment & Post">
          <SummaryGrid
            rows={[
              ['Category', employment.employmentCategory],
              ['Date of Joining', formatDate(employment.dateOfJoiningPsu)],
              ['Recruitment Mode', employment.recruitmentMode],
              ['Appointment Letter No.', employment.appointmentLetterNo],
            ]}
          />
        </AccordionSection>

        <AccordionSection title="Family & Nominees">
          <SummaryGrid
            rows={[
              ['Father', family.fatherName],
              ['Mother', family.motherName],
              ['Spouse', family.spouseName],
              ['Dependents', String(family.dependents.length)],
              ['Nominees', String(family.nominees.length)],
            ]}
          />
        </AccordionSection>
      </div>
    </div>
  )
}

function AccordionSection({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <details className="group p-3" open>
      <summary className="cursor-pointer text-sm font-semibold text-slate-700 group-open:text-brand-forest">{title}</summary>
      <div className="mt-2">{children}</div>
    </details>
  )
}

function SummaryGrid({ rows }: { rows: [string, string][] }) {
  return (
    <div className="grid grid-cols-2 gap-x-4 gap-y-1 text-sm">
      {rows.map(([label, val]) => (
        <div key={label} className="flex justify-between gap-2 border-b border-slate-50 py-1">
          <span className="text-slate-400">{label}</span>
          <span className="text-right text-slate-700">{val || '—'}</span>
        </div>
      ))}
    </div>
  )
}

function ChecklistRow({ label, ok }: { label: string; ok: boolean }) {
  return (
    <div className="flex items-center gap-2 text-sm">
      {ok ? <CheckCircle2 size={16} className="text-emerald-600" /> : <XCircle size={16} className="text-red-400" />}
      <span className={ok ? 'text-slate-700' : 'text-slate-400'}>{label}</span>
    </div>
  )
}
