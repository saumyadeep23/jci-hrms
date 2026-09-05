import { useEffect, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../api/client'
import type { IfscLookupResponse } from '../types/api'

export const IFSC_PATTERN = /^[A-Z]{4}0[A-Z0-9]{6}$/
const DEBOUNCE_MS = 400

/**
 * Shared IFSC auto-fetch behaviour (debounce -> /v1/finance/ifsc/{ifsc} ->
 * one-shot bank name/branch auto-fill), extracted from the onboarding
 * wizard's Step3Banking/AddBankAccountModal duplication so Edit Employee's
 * Banking tab gets the same UX. onFound is only ever invoked once per
 * distinct successful lookup result (see appliedKeyRef) so it's safe to
 * pass an inline callback without memoizing it.
 */
export function useIfscLookup(ifscValue: string, onFound: (bankName: string | null, branch: string | null) => void) {
  const [debouncedIfsc, setDebouncedIfsc] = useState(ifscValue)
  const appliedKeyRef = useRef<string | null>(null)

  useEffect(() => {
    const handle = setTimeout(() => setDebouncedIfsc(ifscValue), DEBOUNCE_MS)
    return () => clearTimeout(handle)
  }, [ifscValue])

  const formatValid = IFSC_PATTERN.test(debouncedIfsc)

  const { data, isFetching } = useQuery({
    queryKey: ['ifsc-lookup', debouncedIfsc],
    queryFn: async () => (await apiClient.get<IfscLookupResponse>(`/v1/finance/ifsc/${debouncedIfsc}`)).data,
    enabled: formatValid,
  })

  useEffect(() => {
    if (!data) return
    const key = `${data.ifsc}:${data.found}`
    if (appliedKeyRef.current === key) return
    appliedKeyRef.current = key
    if (data.found) {
      onFound(data.bankName, data.branch)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [data])

  const showFormatError = ifscValue.length === 11 && !IFSC_PATTERN.test(ifscValue)
  const showNotFoundError = formatValid && debouncedIfsc === ifscValue && data && !data.found
  const error = showFormatError ? 'Invalid IFSC format (e.g. SBIN0001234).' : showNotFoundError ? 'IFSC not found.' : null

  return { isFetching, error, setDebouncedIfsc }
}
