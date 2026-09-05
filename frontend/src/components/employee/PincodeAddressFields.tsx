import { useEffect, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import type { PincodeLookupResponse } from '../../types/api'

const PINCODE_REGEX = /^[1-9][0-9]{5}$/
const DEBOUNCE_MS = 400

export interface AddressValue {
  addressLine: string
  addressLine2?: string
  policeStation?: string
  city: string
  state: string
  district: string
  pinCode: string
}

export const EMPTY_ADDRESS: AddressValue = {
  addressLine: '',
  addressLine2: '',
  policeStation: '',
  city: '',
  state: '',
  district: '',
  pinCode: '',
}

/**
 * FR-EMP.14: State/District are no longer manually selected - they're
 * resolved (and locked) from the India Post pincode API via the backend
 * proxy, and City becomes a picker over that pincode's post offices.
 */
export function PincodeAddressFields({ value, onChange }: { value: AddressValue; onChange: (patch: Partial<AddressValue>) => void }) {
  const [debouncedPincode, setDebouncedPincode] = useState(value.pinCode)
  const appliedKeyRef = useRef<string | null>(null)

  useEffect(() => {
    const handle = setTimeout(() => setDebouncedPincode(value.pinCode), DEBOUNCE_MS)
    return () => clearTimeout(handle)
  }, [value.pinCode])

  const formatValid = PINCODE_REGEX.test(debouncedPincode)

  const { data, isFetching } = useQuery({
    queryKey: ['pincode-lookup', debouncedPincode],
    queryFn: async () => (await apiClient.get<PincodeLookupResponse>(`/lookups/pincode/${debouncedPincode}`)).data,
    enabled: formatValid,
  })

  useEffect(() => {
    if (!data) return
    const key = `${data.pincode}:${data.found}`
    if (appliedKeyRef.current === key) return
    appliedKeyRef.current = key

    if (data.found) {
      onChange({
        state: data.state ?? '',
        district: data.district ?? '',
        city: data.postOffices.includes(value.city) ? value.city : (data.postOffices[0] ?? ''),
      })
    } else {
      onChange({ state: '', district: '', city: '' })
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
        <label className="mb-1 block text-xs font-medium text-slate-600">Address Line</label>
        <input
          value={value.addressLine}
          onChange={(e) => onChange({ addressLine: e.target.value })}
          className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
        />
      </div>
      <div className="col-span-2">
        <label className="mb-1 block text-xs font-medium text-slate-600">Address Line 2</label>
        <input
          value={value.addressLine2 ?? ''}
          onChange={(e) => onChange({ addressLine2: e.target.value })}
          className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
        />
      </div>

      <div>
        <label className="mb-1 block text-xs font-medium text-slate-600">PIN Code</label>
        <input
          value={value.pinCode}
          inputMode="numeric"
          maxLength={6}
          onChange={(e) => onChange({ pinCode: e.target.value.replace(/\D/g, '').slice(0, 6) })}
          onBlur={() => setDebouncedPincode(value.pinCode)}
          className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
        />
        {isFetching && <p className="mt-1 text-[11px] text-slate-400">Looking up PIN code...</p>}
        {errorMessage && <p className="mt-1 text-[11px] text-red-600">{errorMessage}</p>}
      </div>

      <div>
        <label className="mb-1 block text-xs font-medium text-slate-600">City / Post Office</label>
        {data?.found && data.postOffices.length > 0 ? (
          <select
            value={value.city}
            onChange={(e) => onChange({ city: e.target.value })}
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
            value={value.city}
            disabled
            placeholder="Enter a valid PIN code first"
            className="w-full rounded-md border border-slate-300 bg-slate-50 px-3 py-2 text-sm text-slate-400"
          />
        )}
      </div>

      <div>
        <label className="mb-1 block text-xs font-medium text-slate-600">Police Station</label>
        <input
          value={value.policeStation ?? ''}
          onChange={(e) => onChange({ policeStation: e.target.value })}
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
