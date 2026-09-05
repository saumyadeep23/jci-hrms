import { isAxiosError } from 'axios'

/** Mirrors GlobalExceptionHandler's ErrorResponse (backend/.../exception/ErrorResponse.java). fieldErrors is only ever populated for a Bean Validation (400) failure. */
export interface ApiErrorResponse {
  timestamp: string
  status: number
  error: string
  message: string
  fieldErrors?: { field: string; message: string }[]
}

function apiErrorBody(error: unknown): ApiErrorResponse | null {
  if (!isAxiosError(error)) return null
  const body = error.response?.data
  if (body && typeof body === 'object' && 'message' in body) {
    return body as ApiErrorResponse
  }
  return null
}

/** Extracts GlobalExceptionHandler's ErrorResponse.message from a failed apiClient call, falling back to a generic message. */
export function describeApiError(error: unknown, fallback: string): string {
  const body = apiErrorBody(error)
  if (body) return body.message
  if (isAxiosError(error)) return error.message
  return fallback
}

/** Every line of ErrorResponse.message, split on the "; " GlobalExceptionHandler joins multiple failures with - for a FormErrorBanner's `errors: string[]`. */
export function describeApiErrorList(error: unknown, fallback: string): string[] {
  const message = describeApiError(error, fallback)
  return message.split('; ').filter(Boolean)
}

/**
 * Maps ErrorResponse.fieldErrors onto a `{ field: message }` record a form
 * can key its own local field-error state by, so a failed PUT/POST can
 * highlight the exact offending inputs. Returns null when the failure
 * wasn't a Bean Validation (400) error, or carried no field errors -
 * callers should fall back to displaying describeApiError()/
 * describeApiErrorList() as a form-level banner in that case.
 */
export function getFieldErrors(error: unknown): Record<string, string> | null {
  const body = apiErrorBody(error)
  if (!body?.fieldErrors || body.fieldErrors.length === 0) return null
  const map: Record<string, string> = {}
  for (const entry of body.fieldErrors) {
    map[entry.field] = entry.message
  }
  return map
}
