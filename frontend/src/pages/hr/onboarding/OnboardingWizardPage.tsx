import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { CheckCircle2 } from 'lucide-react'
import { apiClient } from '../../../api/client'
import { describeApiErrorList } from '../../../lib/apiError'
import { FormErrorBanner } from '../../../components/common/FormErrorBanner'
import { Card, ErrorState, PageHeader, PrimaryButton, SecondaryButton } from '../../../components/common/ui'
import type { OnboardingDraftResponse, OnboardingDraftUpsertRequest, OnboardingSubmitResponse } from '../../../types/api'
import {
  EMPTY_BANKING,
  EMPTY_EMPLOYMENT,
  EMPTY_FAMILY,
  EMPTY_PERSONAL,
  EMPTY_SOCIAL_PROFILE,
  emptyAddress,
} from './onboardingTypes'
import type {
  LocalAddress,
  LocalBanking,
  LocalDocument,
  LocalEmployment,
  LocalFamily,
  LocalPastService,
  LocalQualification,
  LocalSocialProfile,
} from './onboardingTypes'
import type { OnboardingPersonalDetailsRequest } from '../../../types/api'
import {
  addressFromResponse,
  addressToRequest,
  bankingFromResponse,
  bankingToRequest,
  documentToRequest,
  documentsFromResponse,
  employmentFromResponse,
  employmentToRequest,
  familyFromResponse,
  familyToRequest,
  pastServiceFromResponse,
  pastServiceToRequest,
  qualificationToRequest,
  qualificationsFromResponse,
  socialProfileFromResponse,
  socialProfileToRequest,
} from './mappers'
import { Step1Personal } from './steps/Step1Personal'
import { Step2Address } from './steps/Step2Address'
import { Step3Banking } from './steps/Step3Banking'
import { Step4Qualifications } from './steps/Step4Qualifications'
import { Step5PastService } from './steps/Step5PastService'
import { Step6Employment } from './steps/Step6Employment'
import { Step7Family } from './steps/Step7Family'
import { Step8Review } from './steps/Step8Review'

const STEP_LABELS = [
  'Personal & Bio-Data',
  'Address',
  'Banking',
  'Qualifications',
  'Past Service',
  'Employment & Post',
  'Family & Nominees',
  'Review & Submit',
]
const TOTAL_STEPS = STEP_LABELS.length

/** PIMS_SPEC.md Section 4/5: the 8-step onboarding wizard with draft save/resume/finalize. */
export function OnboardingWizardPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  const urlDraftId = searchParams.get('draftId')

  const [draftId, setDraftId] = useState<number | null>(urlDraftId ? Number(urlDraftId) : null)
  const [draftCode, setDraftCode] = useState<string | null>(null)
  const [step, setStep] = useState(1)
  const [banner, setBanner] = useState<string | null>(null)

  const [personal, setPersonal] = useState<OnboardingPersonalDetailsRequest>(EMPTY_PERSONAL)
  const [presentAddress, setPresentAddress] = useState<LocalAddress>(emptyAddress('PRESENT'))
  const [permanentAddress, setPermanentAddress] = useState<LocalAddress>(emptyAddress('PERMANENT'))
  const [permanentSameAsPresent, setPermanentSameAsPresent] = useState(true)
  const [banking, setBanking] = useState<LocalBanking>(EMPTY_BANKING)
  const [qualifications, setQualifications] = useState<LocalQualification[]>([])
  const [pastServiceRecords, setPastServiceRecords] = useState<LocalPastService[]>([])
  const [employment, setEmployment] = useState<LocalEmployment>(EMPTY_EMPLOYMENT)
  const [family, setFamily] = useState<LocalFamily>(EMPTY_FAMILY)
  const [documents, setDocuments] = useState<LocalDocument[]>([])
  const [socialProfile, setSocialProfile] = useState<LocalSocialProfile>(EMPTY_SOCIAL_PROFILE)

  const draftQuery = useQuery({
    queryKey: ['onboarding-draft', draftId],
    queryFn: async () => (await apiClient.get<OnboardingDraftResponse>(`/onboarding/drafts/${draftId}`)).data,
    enabled: draftId !== null,
  })

  useEffect(() => {
    const draft = draftQuery.data
    if (!draft) return
    setDraftCode(draft.draftCode)
    setStep(draft.currentStep && draft.currentStep >= 1 ? draft.currentStep : 1)
    if (draft.personal) setPersonal(draft.personal)
    setPresentAddress(addressFromResponse(draft.presentAddress, 'PRESENT'))
    setPermanentAddress(addressFromResponse(draft.permanentAddress, 'PERMANENT'))
    setPermanentSameAsPresent(draft.permanentSameAsPresent ?? true)
    setBanking(bankingFromResponse(draft.banking))
    setQualifications(qualificationsFromResponse(draft.qualifications))
    setPastServiceRecords(pastServiceFromResponse(draft.pastServiceRecords))
    setEmployment(employmentFromResponse(draft.employment))
    setFamily(familyFromResponse(draft.family))
    setDocuments(documentsFromResponse(draft.documents))
    setSocialProfile(socialProfileFromResponse(draft.socialProfile))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [draftQuery.data])

  function currentStepPayload(): Partial<OnboardingDraftUpsertRequest> {
    const socialProfilePayload = { socialProfile: socialProfileToRequest(socialProfile) }
    switch (step) {
      case 1:
        return { personal, ...socialProfilePayload }
      case 2:
        return { presentAddress: addressToRequest(presentAddress), permanentAddress: addressToRequest(permanentAddress), permanentSameAsPresent }
      case 3:
        return { banking: bankingToRequest(banking) }
      case 4:
        return { qualifications: qualifications.map(qualificationToRequest) }
      case 5:
        return { pastServiceRecords: pastServiceRecords.map(pastServiceToRequest) }
      case 6:
        return { employment: employmentToRequest(employment) }
      case 7:
        return { family: familyToRequest(family), ...socialProfilePayload }
      case 8:
        return { documents: documents.map(documentToRequest) }
      default:
        return {}
    }
  }

  const saveMutation = useMutation({
    mutationFn: async () => {
      const body: OnboardingDraftUpsertRequest = {
        draftId,
        currentStep: step,
        personal: null,
        presentAddress: null,
        permanentAddress: null,
        permanentSameAsPresent: null,
        banking: null,
        qualifications: null,
        pastServiceRecords: null,
        employment: null,
        family: null,
        documents: null,
        socialProfile: null,
        ...currentStepPayload(),
      }
      return (await apiClient.post<OnboardingDraftResponse>('/onboarding/drafts', body)).data
    },
    onSuccess: (draft) => {
      queryClient.invalidateQueries({ queryKey: ['onboarding-drafts'] })
      if (draftId === null) {
        setDraftId(draft.id)
        setSearchParams({ draftId: String(draft.id) }, { replace: true })
      }
      setDraftCode(draft.draftCode)
      setBanner(`Saved as draft ${draft.draftCode} (${draft.completionPercentage}% complete).`)
      window.setTimeout(() => setBanner(null), 5000)
    },
  })

  const finalizeMutation = useMutation({
    mutationFn: async () => {
      if (draftId === null) throw new Error('Save as draft at least once before finalizing.')
      return (await apiClient.post<OnboardingSubmitResponse>(`/onboarding/drafts/${draftId}/finalize`)).data
    },
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: ['onboarding-drafts'] })
      navigate('/employees', { state: { onboardedEmployeeCode: result.employeeCode } })
    },
  })

  const isLastStep = step === TOTAL_STEPS

  return (
    <div className="pb-24">
      <PageHeader
        title="Employee Onboarding"
        description={draftCode ? `Draft ${draftCode}` : 'New onboarding - not yet saved'}
      />

      <div className="mb-4 flex flex-wrap items-center gap-1 overflow-x-auto">
        {STEP_LABELS.map((label, i) => {
          const n = i + 1
          return (
            <button
              key={label}
              type="button"
              onClick={() => setStep(n)}
              className={`flex shrink-0 items-center gap-1.5 rounded-full px-3 py-1.5 text-xs font-medium transition-colors ${
                step === n ? 'bg-brand-forest text-white' : n < step ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-100 text-slate-500'
              }`}
            >
              <span className="flex h-4 w-4 items-center justify-center rounded-full bg-white/30 text-[10px]">{n}</span>
              {label}
            </button>
          )
        })}
      </div>

      {banner && (
        <div className="mb-4 flex items-center gap-2 rounded-md border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-700">
          <CheckCircle2 size={16} /> {banner}
        </div>
      )}
      {draftQuery.isError && <ErrorState message="Could not load this draft." />}

      <Card>
        {step === 1 && (
          <Step1Personal value={personal} onChange={setPersonal} socialProfile={socialProfile} onSocialProfileChange={setSocialProfile} />
        )}
        {step === 2 && (
          <Step2Address
            present={presentAddress}
            onPresentChange={setPresentAddress}
            permanentSameAsPresent={permanentSameAsPresent}
            onPermanentSameAsPresentChange={setPermanentSameAsPresent}
            permanent={permanentAddress}
            onPermanentChange={setPermanentAddress}
          />
        )}
        {step === 3 && <Step3Banking value={banking} onChange={setBanking} />}
        {step === 4 && <Step4Qualifications value={qualifications} onChange={setQualifications} />}
        {step === 5 && <Step5PastService value={pastServiceRecords} onChange={setPastServiceRecords} />}
        {step === 6 && <Step6Employment value={employment} onChange={setEmployment} />}
        {step === 7 && (
          <Step7Family value={family} onChange={setFamily} socialProfile={socialProfile} onSocialProfileChange={setSocialProfile} />
        )}
        {step === 8 && (
          <Step8Review
            personal={personal}
            presentAddress={presentAddress}
            permanentAddress={permanentAddress}
            permanentSameAsPresent={permanentSameAsPresent}
            banking={banking}
            qualifications={qualifications}
            pastServiceRecords={pastServiceRecords}
            employment={employment}
            family={family}
            documents={documents}
            onDocumentsChange={setDocuments}
          />
        )}

        {saveMutation.isError && (
          <FormErrorBanner title="Could not save draft" errors={describeApiErrorList(saveMutation.error, 'Could not save draft.')} />
        )}
        {finalizeMutation.isError && (
          <FormErrorBanner
            title="Could not finalize onboarding"
            errors={describeApiErrorList(finalizeMutation.error, 'Could not finalize onboarding.')}
          />
        )}
      </Card>

      <div className="fixed inset-x-0 bottom-0 z-40 border-t border-slate-200 bg-white/95 px-4 py-3 backdrop-blur">
        <div className="mx-auto flex max-w-5xl items-center justify-between gap-3">
          <SecondaryButton onClick={() => setStep((s) => Math.max(1, s - 1))} disabled={step === 1}>
            Previous
          </SecondaryButton>
          <div className="flex items-center gap-2">
            <SecondaryButton onClick={() => saveMutation.mutate()} disabled={saveMutation.isPending}>
              Save as Draft
            </SecondaryButton>
            {isLastStep ? (
              <PrimaryButton onClick={() => finalizeMutation.mutate()} disabled={finalizeMutation.isPending || draftId === null}>
                Submit & Complete Onboarding
              </PrimaryButton>
            ) : (
              <PrimaryButton onClick={() => setStep((s) => Math.min(TOTAL_STEPS, s + 1))}>Next</PrimaryButton>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
