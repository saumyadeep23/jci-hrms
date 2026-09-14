import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../../../api/client'
import { useToast } from '../../../../components/common/ToastProvider'
import { Modal } from '../../../../components/common/Modal'
import { EmployeePickerInput } from '../../../../components/common/EmployeePickerInput'
import { Badge, ErrorState, PrimaryButton, SecondaryButton } from '../../../../components/common/ui'
import { describeApiError } from '../../../../lib/apiError'
import { DocumentUploadField } from '../../../hr/onboarding/DocumentUploadField'
import { inr, METRIC_LABELS, relevantInputMetrics, repaymentPhases } from './cpfEligibilityUi'
import type {
  CpfApplicationDocumentSubmission,
  CpfApplicationEligibilityResponse,
  CpfApplicationRequest,
  CpfApplicationResponse,
  CpfWithdrawalPurposeResponse,
  DocumentUploadResponse,
  EmployeeResponse,
} from '../../../../types/api'

const toNumberOrNull = (value: string): number | null => (value.trim() === '' ? null : Number(value))

/**
 * Section 4 - Apply for a CPF Trust loan/withdrawal via the DB-driven rule engine (CpfApplicationService).
 * Purpose selection, dynamic input fields, eligibility/ceiling, head allocation, repayment preview and the
 * required-document list all come from CpfWithdrawalRuleEngine/CpfApplicationService's single eligibility
 * endpoint - see CpfApplicationController's own javadoc for how this differs from the older, still-live
 * CpfLoanController flow (repayment/recovery machinery only, no longer reachable from this "Apply for Loan"
 * entry point). POSTs to /v1/payroll/trust/withdrawal-applications.
 *
 * Two ways this modal is opened (Task 4 Part 20): (1) a CPF Trust admin, via CpfLoansPage, picking any
 * employee through EmployeePickerInput; (2) an employee applying for themselves, via the ESS Simulator's
 * "Apply for this Loan" handoff, which passes lockedEmployee/initialPurposeCode and hides the picker - the
 * backend's own @cpfApplicationSec.isSelf check is what actually enforces "may only apply for themselves",
 * hiding the picker here is a UX nicety, not the security boundary.
 */
export function ApplyCpfLoanModal({
  onClose,
  lockedEmployee,
  initialPurposeCode,
}: {
  onClose: () => void
  lockedEmployee?: EmployeeResponse
  initialPurposeCode?: string
}) {
  const { show } = useToast()
  const queryClient = useQueryClient()
  const [employee, setEmployee] = useState<EmployeeResponse | null>(lockedEmployee ?? null)
  const [purposeCode, setPurposeCode] = useState(initialPurposeCode ?? '')
  const [appliedAmount, setAppliedAmount] = useState('')
  const [propertyCost, setPropertyCost] = useState('')
  const [payrollDeductionCapacity, setPayrollDeductionCapacity] = useState('')
  const [tenureMonths, setTenureMonths] = useState('')
  const [tenureTouched, setTenureTouched] = useState(false)
  const [uploadedDocuments, setUploadedDocuments] = useState<Record<string, DocumentUploadResponse>>({})
  const [eligibility, setEligibility] = useState<CpfApplicationEligibilityResponse | null>(null)
  const [declarationAccepted, setDeclarationAccepted] = useState(false)

  const purposesQuery = useQuery({
    queryKey: ['cpf-withdrawal-purposes'],
    queryFn: async () => (await apiClient.get<CpfWithdrawalPurposeResponse[]>('/v1/payroll/trust/withdrawal-masters/purposes')).data,
  })
  const purposes = (purposesQuery.data ?? []).filter((p) => p.active && p.typeCode !== 'FINAL_SETTLEMENT')

  const eligibilityMutation = useMutation({
    mutationFn: async () => {
      const response = await apiClient.get<CpfApplicationEligibilityResponse>('/v1/payroll/trust/withdrawal-applications/eligibility', {
        params: {
          employeeCode: employee!.employeeCode,
          purposeCode,
          propertyCost: toNumberOrNull(propertyCost) ?? undefined,
          payrollDeductionCapacity: toNumberOrNull(payrollDeductionCapacity) ?? undefined,
          requestedAmount: toNumberOrNull(appliedAmount) ?? undefined,
          tenureMonths: toNumberOrNull(tenureMonths) ?? undefined,
        },
      })
      return response.data
    },
    onSuccess: (data) => setEligibility(data),
  })

  const canCheckEligibility = employee !== null && purposeCode !== ''

  // Step 2 (dynamic fields) + discovery: as soon as a purpose is chosen, silently fetch eligibility once
  // (with whatever field values already exist, usually none) purely to learn which ceiling components this
  // rule actually configures - CpfApplicationEligibilityResponse.ceilingComponents is always populated, even
  // with zero-valued inputs (see its own javadoc), so this is never a wasted/fake call.
  useEffect(() => {
    if (employee && purposeCode) {
      setEligibility(null)
      setTenureTouched(false)
      eligibilityMutation.mutate()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [employee?.employeeCode, purposeCode])

  // Step 6 default tenure - prefill from the rule's own default_tenure_months once known, but only until the
  // applicant actually touches the field themselves.
  useEffect(() => {
    if (eligibility?.carriesRepaymentSchedule && eligibility.defaultTenureMonths != null && !tenureTouched) {
      setTenureMonths(String(eligibility.defaultTenureMonths))
    }
  }, [eligibility?.carriesRepaymentSchedule, eligibility?.defaultTenureMonths, tenureTouched])

  const requiredDocuments = eligibility?.requiredDocuments ?? []
  const missingMandatoryDocuments = requiredDocuments.filter((d) => d.mandatory && !uploadedDocuments[d.documentName])

  const amountExceedsCap = eligibility !== null && Number(appliedAmount) > Number(eligibility.eligibleAmount)
  const inputMetrics = eligibility ? relevantInputMetrics(eligibility.ceilingComponents) : []
  const tenureOutOfRange =
    eligibility?.carriesRepaymentSchedule &&
    tenureMonths !== '' &&
    ((eligibility.minTenureMonths != null && Number(tenureMonths) < eligibility.minTenureMonths) ||
      (eligibility.maxTenureMonths != null && Number(tenureMonths) > eligibility.maxTenureMonths))

  const canSubmit =
    employee !== null &&
    purposeCode !== '' &&
    eligibility !== null &&
    eligibility.eligible &&
    Number(appliedAmount) > 0 &&
    !amountExceedsCap &&
    !tenureOutOfRange &&
    (!eligibility.carriesRepaymentSchedule || tenureMonths !== '') &&
    missingMandatoryDocuments.length === 0 &&
    declarationAccepted

  const submitMutation = useMutation({
    mutationFn: async () => {
      const submittedDocuments: CpfApplicationDocumentSubmission[] = Object.entries(uploadedDocuments).map(([documentName, u]) => ({
        documentName,
        s3Key: u.fileS3Key,
        originalFilename: u.originalFileName,
      }))
      const payload: CpfApplicationRequest = {
        employeeCode: employee!.employeeCode,
        purposeCode,
        appliedAmount: Number(appliedAmount),
        // Current Basic + DA is always server-resolved from the employee's own latest DISBURSED payroll
        // record (CpfApplicationService.resolveCurrentBasicPlusDa) - never collected as an input here.
        basicPlusDa: null,
        propertyCost: toNumberOrNull(propertyCost),
        payrollDeductionCapacity: toNumberOrNull(payrollDeductionCapacity),
        submittedDocuments,
        // HOUSING_LOAN_REPAYMENT ignores this entirely server-side (resolved from the employee's own
        // linked loans) - never collected as an input here in the first place, see inputMetrics.
        outstandingLoan: null,
      }
      return (await apiClient.post<CpfApplicationResponse>('/v1/payroll/trust/withdrawal-applications', payload)).data
    },
    onSuccess: (data) => {
      show({ tone: 'success', message: `CPF withdrawal/loan application ${data.applicationNumber ?? ''} submitted.` })
      queryClient.invalidateQueries({ queryKey: ['cpf-loan-applications'] })
      onClose()
    },
  })

  const preview = eligibility?.repaymentPreview ?? null

  return (
    <Modal title="Apply for CPF Loan / Withdrawal" onClose={onClose} maxWidthClassName="max-w-2xl">
      <form
        className="space-y-4"
        onSubmit={(e) => {
          e.preventDefault()
          submitMutation.mutate()
        }}
      >
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Employee</label>
          {lockedEmployee ? (
            <p className="rounded-md border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700">
              {lockedEmployee.fullName} <span className="text-slate-400">({lockedEmployee.employeeCode})</span>
            </p>
          ) : (
            <EmployeePickerInput
              selected={employee}
              onSelect={(e) => {
                setEmployee(e)
                setEligibility(null)
              }}
            />
          )}
        </div>

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Purpose</label>
          <select
            value={purposeCode}
            onChange={(e) => {
              setPurposeCode(e.target.value)
              setEligibility(null)
              setUploadedDocuments({})
            }}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          >
            <option value="">Select a purpose...</option>
            {purposes.map((p) => (
              <option key={p.code} value={p.code}>
                {p.name} ({p.typeCode === 'REFUNDABLE' ? 'Loan' : 'Withdrawal'})
              </option>
            ))}
          </select>
        </div>

        {eligibilityMutation.isPending && !eligibility && <p className="text-xs text-slate-400">Loading eligibility rules for this purpose...</p>}

        {/* Step 2 - only the inputs this purpose's rule actually configures a ceiling component for. */}
        {eligibility && inputMetrics.length > 0 && (
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            {inputMetrics.includes('PROPERTY_COST') && (
              <div>
                <label className="mb-1 block text-xs font-medium text-slate-600">{METRIC_LABELS.PROPERTY_COST}</label>
                <input
                  type="number"
                  min={0}
                  step="0.01"
                  value={propertyCost}
                  onChange={(e) => setPropertyCost(e.target.value)}
                  className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                />
              </div>
            )}
            {inputMetrics.includes('PAYROLL_DEDUCTION_CAPACITY') && (
              <div>
                <label className="mb-1 block text-xs font-medium text-slate-600">{METRIC_LABELS.PAYROLL_DEDUCTION_CAPACITY}</label>
                <input
                  type="number"
                  min={0}
                  step="0.01"
                  value={payrollDeductionCapacity}
                  onChange={(e) => setPayrollDeductionCapacity(e.target.value)}
                  className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                />
              </div>
            )}
          </div>
        )}
        {eligibility && eligibility.ceilingComponents.some((c) => c.sourceMetric === 'BASIC_PLUS_DA') && (
          <p className="text-[11px] text-slate-400">
            Your current Basic + DA is retrieved automatically from your latest payroll record - you don't need to enter it.
          </p>
        )}
        {eligibility && purposeCode.toUpperCase() === 'HOUSING_LOAN_REPAYMENT' && (
          <p className="text-[11px] text-slate-400">
            Outstanding housing loan balance is retrieved automatically from your existing CPF Trust housing loan - you don't need to enter it.
          </p>
        )}

        <SecondaryButton
          type="button"
          disabled={!canCheckEligibility || eligibilityMutation.isPending}
          onClick={() => eligibilityMutation.mutate()}
        >
          {eligibilityMutation.isPending ? 'Checking...' : 'Recalculate Eligibility'}
        </SecondaryButton>

        {eligibilityMutation.isError && (
          <ErrorState message={describeApiError(eligibilityMutation.error, 'Could not check eligibility for this purpose.')} />
        )}

        {/* Steps 3+4 - eligibility + ceiling. */}
        {eligibility && (
          <div className="rounded-md border border-slate-200 bg-slate-50 p-3 text-sm">
            <div className="space-y-1">
              <p>
                <span className="text-slate-400">Total Eligible Balance: </span>
                <span className="font-medium text-slate-800">{inr(eligibility.totalEligibleBalance)}</span>
              </p>
              <p>
                <span className="text-slate-400">Maximum Eligible Amount: </span>
                <span className="font-medium text-slate-800">{inr(eligibility.eligibleAmount)}</span>
              </p>
              {eligibility.ceilingComponents.length > 0 && (
                <div className="mt-2 space-y-0.5 border-t border-slate-200 pt-2">
                  {eligibility.ceilingComponents.map((c) => (
                    <p key={c.componentName} className="flex justify-between text-[11px] text-slate-500">
                      <span>
                        {c.componentName} <span className="text-slate-400">({c.sourceMetric})</span>
                      </span>
                      <span className="font-medium text-slate-700">{inr(c.calculatedValue)}</span>
                    </p>
                  ))}
                </div>
              )}
              {!eligibility.eligible && <Badge tone="danger">{eligibility.eligibilityReason}</Badge>}
              {!eligibility.serviceEligible && <Badge tone="danger">{eligibility.serviceEligibilityReason}</Badge>}
              {!eligibility.frequencyEligible && <Badge tone="danger">{eligibility.frequencyReason}</Badge>}
              {eligibility.eligible && <p className="text-[11px] text-slate-400">{eligibility.eligibilityReason}</p>}
            </div>
          </div>
        )}

        {/* Step 5 - amount. */}
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Applied Amount</label>
          <input
            type="number"
            min={0}
            step="0.01"
            value={appliedAmount}
            onChange={(e) => setAppliedAmount(e.target.value)}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
          {amountExceedsCap && <p className="mt-1 text-[11px] text-red-600">Exceeds the maximum eligible amount.</p>}
        </div>

        {/* Step 6 - tenure, only for purposes that carry a repayment schedule. */}
        {eligibility?.carriesRepaymentSchedule && (
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">
              Tenure (months){eligibility.minTenureMonths != null && eligibility.maxTenureMonths != null && (
                <span className="text-slate-400">
                  {' '}
                  ({eligibility.minTenureMonths}-{eligibility.maxTenureMonths})
                </span>
              )}
            </label>
            <input
              type="number"
              min={eligibility.minTenureMonths ?? undefined}
              max={eligibility.maxTenureMonths ?? undefined}
              value={tenureMonths}
              onChange={(e) => {
                setTenureTouched(true)
                setTenureMonths(e.target.value)
              }}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
            {tenureOutOfRange && <p className="mt-1 text-[11px] text-red-600">Tenure is outside the rule's allowed range.</p>}
            <SecondaryButton
              type="button"
              className="mt-2"
              disabled={eligibilityMutation.isPending || !canCheckEligibility}
              onClick={() => eligibilityMutation.mutate()}
            >
              Preview Repayment
            </SecondaryButton>
          </div>
        )}

        {/* Steps 7+9 - repayment preview + schedule. */}
        {preview && (
          <div className="rounded-md border border-slate-200 bg-slate-50 p-3 text-sm">
            <p className="mb-2 text-xs font-medium text-slate-600">Repayment Preview</p>
            <div className="grid grid-cols-2 gap-x-4 gap-y-1 text-[12px] text-slate-600 sm:grid-cols-3">
              <p>
                Tenure: <span className="font-medium text-slate-800">{preview.tenureMonths} mo</span>
              </p>
              <p>
                Total Interest: <span className="font-medium text-slate-800">{inr(preview.totalInterest)}</span>
              </p>
              <p>
                Total Recovery: <span className="font-medium text-slate-800">{inr(preview.totalRecovery)}</span>
              </p>
            </div>
            <div className="mt-3 overflow-x-auto">
              <table className="w-full text-left text-[11px]">
                <thead>
                  <tr className="text-slate-400">
                    <th className="pb-1 pr-2 font-medium">Phase</th>
                    <th className="pb-1 pr-2 font-medium">Installments</th>
                    <th className="pb-1 pr-2 font-medium">Per Installment</th>
                    <th className="pb-1 font-medium">Phase Total</th>
                  </tr>
                </thead>
                <tbody>
                  {repaymentPhases(preview).map((phase) => (
                    <tr key={phase.label} className="border-t border-slate-200">
                      <td className="py-1 pr-2 text-slate-700">
                        {phase.label}
                        <span className="block text-slate-400">
                          Installment {phase.startInstallment}-{phase.endInstallment}
                        </span>
                      </td>
                      <td className="py-1 pr-2 text-slate-700">{phase.installmentCount}</td>
                      <td className="py-1 pr-2 text-slate-700">{inr(phase.perInstallmentAmount)}</td>
                      <td className="py-1 text-slate-700">{inr(phase.phaseTotal)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <p className="mt-2 text-[11px] text-slate-400">
              Recovery via payroll deduction begins the payroll cycle after disbursement - the disbursement month itself carries no recovery.
            </p>
          </div>
        )}

        {/* Step 8 - head allocation. */}
        {eligibility && eligibility.headAllocation.length > 0 && (
          <div className="rounded-md border border-slate-200 p-3 text-sm">
            <p className="mb-2 text-xs font-medium text-slate-600">Head-wise Debit Allocation</p>
            <table className="w-full text-left text-[11px]">
              <thead>
                <tr className="text-slate-400">
                  <th className="pb-1 pr-2 font-medium">Priority</th>
                  <th className="pb-1 pr-2 font-medium">Head</th>
                  <th className="pb-1 font-medium">Debit Amount</th>
                </tr>
              </thead>
              <tbody>
                {eligibility.headAllocation.map((h) => (
                  <tr key={h.headCode} className="border-t border-slate-100">
                    <td className="py-1 pr-2 text-slate-500">{h.debitPriority}</td>
                    <td className="py-1 pr-2 text-slate-700">{h.headName}</td>
                    <td className="py-1 text-slate-700">{inr(h.previewDebitAmount)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {eligibility && requiredDocuments.length > 0 && (
          <div className="space-y-3 rounded-md border border-slate-200 p-3">
            <p className="text-xs font-medium text-slate-600">Supporting Documents</p>
            {requiredDocuments.map((d) => (
              <DocumentUploadField
                key={d.documentName}
                label={`${d.documentName}${d.mandatory ? ' (required)' : ' (optional)'}`}
                category="CPF_WITHDRAWAL_SUPPORTING_DOC"
                maxSizeLabel={d.maxSizeKb ? `${Math.round(d.maxSizeKb / 1024)} MB` : '5 MB'}
                acceptHint={d.allowedMimeTypes ?? '.pdf,.jpg,.jpeg,.png'}
                currentFileName={uploadedDocuments[d.documentName]?.originalFileName}
                onUploaded={(response) => setUploadedDocuments((prev) => ({ ...prev, [d.documentName]: response }))}
              />
            ))}
          </div>
        )}

        {/* Step 10 - declaration/confirmation. */}
        {eligibility && (
          <label className="flex items-start gap-2 text-[12px] text-slate-600">
            <input
              type="checkbox"
              checked={declarationAccepted}
              onChange={(e) => setDeclarationAccepted(e.target.checked)}
              className="mt-0.5"
            />
            <span>
              I confirm the details entered above are accurate, and I authorize the CPF Trust to process this application, debit the
              eligible head(s) shown, and (for loans) recover the amount through payroll deduction as per the schedule shown.
            </span>
          </label>
        )}

        <div className="flex gap-2">
          <SecondaryButton type="button" onClick={onClose} className="flex-1 justify-center">
            Cancel
          </SecondaryButton>
          <PrimaryButton type="submit" disabled={!canSubmit || submitMutation.isPending} className="flex-1 justify-center">
            {submitMutation.isPending ? 'Submitting...' : 'Submit Application'}
          </PrimaryButton>
        </div>

        {submitMutation.isError && <ErrorState message={describeApiError(submitMutation.error, 'Could not submit the application.')} />}
      </form>
    </Modal>
  )
}
