import { apiClient } from '../api/client'

/**
 * Fetches a PDF through the authenticated apiClient (a plain `<a href>`/window.open to the API
 * URL directly would miss the Authorization header the axios interceptor adds) and opens it in a
 * new tab for inline preview, mirroring ReportExportButtons' blob-URL pattern. The object URL is
 * revoked after a delay rather than immediately - revoking right away can race the new tab's own
 * load of it on some browsers.
 */
export async function openPdf(path: string): Promise<void> {
  const response = await apiClient.get(path, { responseType: 'blob' })
  const blob = new Blob([response.data], { type: 'application/pdf' })
  const url = URL.createObjectURL(blob)
  window.open(url, '_blank')
  setTimeout(() => URL.revokeObjectURL(url), 60_000)
}
