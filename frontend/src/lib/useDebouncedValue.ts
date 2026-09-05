import { useEffect, useState } from 'react'

/** Delays reflecting `value` until it's stayed unchanged for `delayMs` - used to avoid firing an API call on every keystroke/selection change. */
export function useDebouncedValue<T>(value: T, delayMs: number): T {
  const [debounced, setDebounced] = useState(value)

  useEffect(() => {
    const id = setTimeout(() => setDebounced(value), delayMs)
    return () => clearTimeout(id)
  }, [value, delayMs])

  return debounced
}
