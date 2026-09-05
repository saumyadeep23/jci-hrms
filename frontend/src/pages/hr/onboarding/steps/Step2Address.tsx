import { useEffect, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../../../api/client'
import { FieldError, errorInputClass } from '../../../../components/common/ui'
import type { PincodeLookupResponse } from '../../../../types/api'
import type { LocalAddress } from '../onboardingTypes'

const PINCODE_REGEX = /^[1-9][0-9]{5}$/
const DEBOUNCE_MS = 400

/** PIMS_SPEC.md Onboarding Step 2: Dynamic Address (India Post API on PIN blur -> State/District/Post Office). */
export function Step2Address({
  present,
  onPresentChange,
  permanentSameAsPresent,
  onPermanentSameAsPresentChange,
  permanent,
  onPermanentChange,
}: {
  present: LocalAddress
  onPresentChange: (next: LocalAddress) => void
  permanentSameAsPresent: boolean
  onPermanentSameAsPresentChange: (value: boolean) => void
  permanent: LocalAddress
  onPermanentChange: (next: LocalAddress) => void
}) {
  return (
    <div className="space-y-5">
      <div>
        <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Present Address</p>
        <AddressBlock value={present} onChange={onPresentChange} />
      </div>

      <label className="flex items-center gap-2 text-sm text-slate-700">
        <input
          type="checkbox"
          checked={permanentSameAsPresent}
          onChange={(e) => onPermanentSameAsPresentChange(e.target.checked)}
        />
        Permanent address same as present
      </label>

      {!permanentSameAsPresent && (
        <div>
          <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Permanent Address</p>
          <AddressBlock value={permanent} onChange={onPermanentChange} />
        </div>
      )}
    </div>
  )
}

function AddressBlock({ value, onChange }: { value: LocalAddress; onChange: (next: LocalAddress) => void }) {
  const [debouncedPincode, setDebouncedPincode] = useState(value.pinCode)
  const appliedKeyRef = useRef<string | null>(null)

  useEffect(() => {
    const handle = setTimeout(() => setDebouncedPincode(value.pinCode), DEBOUNCE_MS)
    return () => clearTimeout(handle)
  }, [value.pinCode])

  const formatValid = PINCODE_REGEX.test(debouncedPincode)

  const { data, isFetching } = useQuery({
    queryKey: ['onboarding-pincode-lookup', debouncedPincode],
    queryFn: async () => (await apiClient.get<PincodeLookupResponse>(`/v1/geo/pincode/${debouncedPincode}`)).data,
    enabled: formatValid,
  })

  useEffect(() => {
    if (!data) return
    const key = `${data.pincode}:${data.found}`
    if (appliedKeyRef.current === key) return
    appliedKeyRef.current = key

    if (data.found) {
      onChange({
        ...value,
        state: data.state ?? '',
        district: data.district ?? '',
        postOffice: data.postOffices.includes(value.postOffice) ? value.postOffice : (data.postOffices[0] ?? ''),
      })
    } else {
      onChange({ ...value, state: '', district: '', postOffice: '' })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [data])

  const typedSixDigits = value.pinCode.length === 6
  const showFormatError = typedSixDigits && !PINCODE_REGEX.test(value.pinCode)
  const showNotFoundError = formatValid && debouncedPincode === value.pinCode && data && !data.found
  const errorMessage = showFormatError ? 'Enter a valid 6-digit PIN code.' : showNotFoundError ? 'Invalid PIN Code' : null

  return (
    <div className="grid grid-cols-2 gap-3">
      <div className="col-span-2">
        <label className="mb-1 block text-xs font-medium text-slate-600">Address Line 1</label>
        <input
          required
          value={value.addressLine1}
          onChange={(e) => onChange({ ...value, addressLine1: e.target.value })}
          className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
        />
      </div>
      <div className="col-span-2">
        <label className="mb-1 block text-xs font-medium text-slate-600">Address Line 2</label>
        <input
          value={value.addressLine2}
          onChange={(e) => onChange({ ...value, addressLine2: e.target.value })}
          className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
        />
      </div>

      <div>
        <label className="mb-1 block text-xs font-medium text-slate-600">PIN Code</label>
        <input
          required
          value={value.pinCode}
          inputMode="numeric"
          maxLength={6}
          onChange={(e) => onChange({ ...value, pinCode: e.target.value.replace(/\D/g, '').slice(0, 6) })}
          onBlur={() => setDebouncedPincode(value.pinCode)}
          className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(errorMessage !== null)}`}
        />
        {isFetching && <p className="mt-1 text-[11px] text-slate-400">Looking up PIN code...</p>}
        <FieldError message={errorMessage} />
      </div>

      <div>
        <label className="mb-1 block text-xs font-medium text-slate-600">Post Office</label>
        {data?.found && data.postOffices.length > 0 ? (
          <select
            value={value.postOffice}
            onChange={(e) => onChange({ ...value, postOffice: e.target.value })}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          >
            {data.postOffices.map((name) => (
              <option key={name} value={name}>
                {name}
              </option>
            ))}
          </select>
        ) : (
          <input
            value={value.postOffice}
            disabled
            placeholder="Enter a valid PIN code first"
            className="w-full rounded-md border border-slate-300 bg-slate-50 px-3 py-2 text-sm text-slate-400"
          />
        )}
      </div>

      <div>
        <label className="mb-1 block text-xs font-medium text-slate-600">City</label>
        <input
          required
          value={value.city}
          onChange={(e) => onChange({ ...value, city: e.target.value })}
          className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
        />
      </div>
      <div>
        <label className="mb-1 block text-xs font-medium text-slate-600">Police Station</label>
        <input
          value={value.policeStation}
          onChange={(e) => onChange({ ...value, policeStation: e.target.value })}
          className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
        />
      </div>

      <div>
        <label className="mb-1 block text-xs font-medium text-slate-600">State</label>
        <input
          value={value.state}
          disabled
          placeholder="Auto-filled from PIN code"
          className="w-full rounded-md border border-slate-300 bg-slate-50 px-3 py-2 text-sm text-slate-500"
        />
      </div>
      <div>
        <label className="mb-1 block text-xs font-medium text-slate-600">District</label>
        <input
          value={value.district}
          disabled
          placeholder="Auto-filled from PIN code"
          className="w-full rounded-md border border-slate-300 bg-slate-50 px-3 py-2 text-sm text-slate-500"
        />
      </div>
    </div>
  )
}
