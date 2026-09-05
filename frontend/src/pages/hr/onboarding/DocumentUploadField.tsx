import { useRef, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { CheckCircle2, UploadCloud } from 'lucide-react'
import { apiClient } from '../../../api/client'
import type { DocumentUploadResponse, InvalidFilePayloadResponse, UploadCategory } from '../../../types/api'

/** PIMS_SPEC.md Section 3.B - POST /api/v1/documents/upload, multipart with category-driven MIME/size validation. */
export function DocumentUploadField({
  label,
  category,
  maxSizeLabel,
  acceptHint,
  currentFileName,
  onUploaded,
}: {
  label: string
  category: UploadCategory
  maxSizeLabel: string
  acceptHint: string
  currentFileName?: string | null
  onUploaded: (response: DocumentUploadResponse) => void
}) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const uploadMutation = useMutation({
    mutationFn: async (file: File) => {
      const formData = new FormData()
      formData.append('file', file)
      formData.append('category', category)
      return (
        await apiClient.post<DocumentUploadResponse>('/v1/documents/upload', formData, {
          headers: { 'Content-Type': 'multipart/form-data' },
        })
      ).data
    },
    onSuccess: (response) => {
      setErrorMessage(null)
      onUploaded(response)
    },
    onError: (error) => {
      if (isAxiosError<InvalidFilePayloadResponse>(error) && error.response?.data) {
        setErrorMessage(error.response.data.message)
      } else {
        setErrorMessage('Upload failed. Please try again.')
      }
    },
  })

  return (
    <div>
      <label className="mb-1 block text-xs font-medium text-slate-600">{label}</label>
      <div className="flex items-center gap-2">
        <input
          ref={inputRef}
          type="file"
          accept={acceptHint}
          className="hidden"
          onChange={(e) => {
            const file = e.target.files?.[0]
            if (file) uploadMutation.mutate(file)
            e.target.value = ''
          }}
        />
        <button
          type="button"
          onClick={() => inputRef.current?.click()}
          disabled={uploadMutation.isPending}
          className="inline-flex items-center gap-2 rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-700 shadow-sm hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
        >
          <UploadCloud size={15} /> {uploadMutation.isPending ? 'Uploading...' : currentFileName ? 'Replace file' : 'Choose file'}
        </button>
        {currentFileName && !uploadMutation.isPending && (
          <span className="flex items-center gap-1 text-xs text-emerald-700">
            <CheckCircle2 size={14} /> {currentFileName}
          </span>
        )}
      </div>
      <p className="mt-1 text-[11px] text-slate-400">Max {maxSizeLabel}</p>
      {errorMessage && <p className="mt-1 text-xs text-red-600">{errorMessage}</p>}
    </div>
  )
}
