import { useMemo, useState, type ReactNode } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { Pencil, Plus, Power } from 'lucide-react'
import { apiClient } from '../../api/client'
import { describeApiErrorList, getFieldErrors } from '../../lib/apiError'
import { Modal } from '../common/Modal'
import { FormErrorBanner } from '../common/FormErrorBanner'
import { Badge, Card, EmptyState, ErrorState, LoadingState, PageHeader, PrimaryButton, SecondaryButton } from '../common/ui'
import type { DependencyCheckResponse, Page } from '../../types/api'

export interface MasterDataColumn<TRow> {
  key: string
  label: string
  render: (row: TRow) => ReactNode
  align?: 'left' | 'right'
}

/**
 * Config-driven CRUD panel shared by every simple master dataset in
 * PIMS_SPEC.md Section 1 (search, sort-free column table, page-size
 * pagination, drawer create/edit, deactivate with the Referential
 * Integrity & Dependency Guard). Entities whose forms are too bespoke
 * (Regional Office, DPC - geolocation capture; State/District - cascading
 * dropdowns) keep their own dedicated pages instead of fitting this mold.
 */
export function MasterDataPanel<TRow extends object, TForm extends object>({
  title,
  description,
  basePath,
  queryKey,
  columns,
  searchPlaceholder = 'Search...',
  matchesSearch,
  idOf,
  getIsActive,
  emptyForm,
  formTitleFor,
  renderForm,
  toRequest,
  fromRowToForm,
  formValid,
}: {
  title: string
  description?: string
  basePath: string
  queryKey: string
  columns: MasterDataColumn<TRow>[]
  searchPlaceholder?: string
  matchesSearch: (row: TRow, query: string) => boolean
  idOf: (row: TRow) => number
  getIsActive: (row: TRow) => boolean
  emptyForm: TForm
  formTitleFor: (editing: TRow | null) => string
  renderForm: (
    form: TForm,
    setForm: (form: TForm) => void,
    fieldErrors: Record<string, string> | null,
    saveError?: unknown,
  ) => ReactNode
  toRequest: (form: TForm) => unknown
  fromRowToForm: (row: TRow) => TForm
  formValid: (form: TForm) => boolean
}) {
  const queryClient = useQueryClient()
  const [search, setSearch] = useState('')
  const [pageSize, setPageSize] = useState(10)
  const [pageIndex, setPageIndex] = useState(0)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [editing, setEditing] = useState<TRow | null>(null)
  const [form, setForm] = useState<TForm>(emptyForm)
  const [dependencyAlert, setDependencyAlert] = useState<{ row: TRow; check: DependencyCheckResponse } | null>(null)

  const { data, isLoading, isError } = useQuery({
    queryKey: [queryKey],
    queryFn: async () => (await apiClient.get<Page<TRow>>(basePath, { params: { size: 500 } })).data,
  })

  const allRows = useMemo(() => data?.content ?? [], [data])
  const filtered = useMemo(() => {
    if (!search.trim()) return allRows
    return allRows.filter((row) => matchesSearch(row, search.trim().toLowerCase()))
  }, [allRows, search, matchesSearch])

  const pageCount = Math.max(1, Math.ceil(filtered.length / pageSize))
  const clampedPageIndex = Math.min(pageIndex, pageCount - 1)
  const paged = filtered.slice(clampedPageIndex * pageSize, clampedPageIndex * pageSize + pageSize)

  const saveMutation = useMutation({
    mutationFn: async () => {
      const payload = toRequest(form)
      if (editing) {
        return (await apiClient.put(`${basePath}/${idOf(editing)}`, payload)).data
      }
      return (await apiClient.post(basePath, payload)).data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [queryKey] })
      closeDrawer()
    },
  })
  const saveFieldErrors = getFieldErrors(saveMutation.error)

  const statusMutation = useMutation({
    mutationFn: async (row: TRow) =>
      (await apiClient.patch(`${basePath}/${idOf(row)}/status`, { active: !getIsActive(row) })).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: [queryKey] }),
  })

  async function handleDeactivate(row: TRow) {
    try {
      const check = (await apiClient.get<DependencyCheckResponse>(`${basePath}/${idOf(row)}/dependencies`)).data
      if (check.hasActiveDependencies) {
        setDependencyAlert({ row, check })
        return
      }
      statusMutation.mutate(row)
    } catch (error) {
      if (isAxiosError(error)) {
        setDependencyAlert(null)
      }
    }
  }

  function openCreate() {
    setEditing(null)
    setForm(emptyForm)
    setDrawerOpen(true)
  }

  function openEdit(row: TRow) {
    setEditing(row)
    setForm(fromRowToForm(row))
    setDrawerOpen(true)
  }

  function closeDrawer() {
    setDrawerOpen(false)
    setEditing(null)
    setForm(emptyForm)
  }

  return (
    <div>
      <PageHeader
        title={title}
        description={description}
        actions={
          <PrimaryButton onClick={openCreate}>
            <Plus size={15} /> Add {title.replace(/s$/, '')}
          </PrimaryButton>
        }
      />

      <Card>
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <input
            value={search}
            onChange={(e) => {
              setSearch(e.target.value)
              setPageIndex(0)
            }}
            placeholder={searchPlaceholder}
            className="w-full max-w-xs rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
          <div className="flex items-center gap-2 text-xs text-slate-500">
            <span>Rows per page</span>
            <select
              value={pageSize}
              onChange={(e) => {
                setPageSize(Number(e.target.value))
                setPageIndex(0)
              }}
              className="rounded-md border border-slate-300 px-2 py-1"
            >
              <option value={10}>10</option>
              <option value={25}>25</option>
              <option value={50}>50</option>
            </select>
          </div>
        </div>

        {isLoading && <LoadingState label={`Loading ${title.toLowerCase()}...`} />}
        {isError && <ErrorState message={`Could not load ${title.toLowerCase()}.`} />}
        {data && filtered.length === 0 && <EmptyState message={`No ${title.toLowerCase()} found.`} />}

        {data && filtered.length > 0 && (
          <>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[640px] border-collapse text-sm">
                <thead>
                  <tr className="border-b border-slate-300 text-left text-slate-500">
                    {columns.map((col) => (
                      <th key={col.key} className={`py-2 pr-3 ${col.align === 'right' ? 'text-right' : ''}`}>
                        {col.label}
                      </th>
                    ))}
                    <th className="py-2 pr-3">Status</th>
                    <th className="py-2 pl-3 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {paged.map((row) => (
                    <tr key={idOf(row)} className="border-b border-slate-100">
                      {columns.map((col) => (
                        <td key={col.key} className={`py-2 pr-3 ${col.align === 'right' ? 'text-right tabular-nums' : ''}`}>
                          {col.render(row)}
                        </td>
                      ))}
                      <td className="py-2 pr-3">
                        <Badge tone={getIsActive(row) ? 'success' : 'neutral'}>
                          {getIsActive(row) ? 'Active' : 'Inactive'}
                        </Badge>
                      </td>
                      <td className="py-2 pl-3">
                        <div className="flex justify-end gap-2">
                          <button
                            type="button"
                            onClick={() => openEdit(row)}
                            className="rounded-md p-1.5 text-slate-500 hover:bg-slate-100 hover:text-slate-700"
                            title="Edit"
                          >
                            <Pencil size={15} />
                          </button>
                          {getIsActive(row) && (
                            <button
                              type="button"
                              onClick={() => handleDeactivate(row)}
                              disabled={statusMutation.isPending}
                              className="rounded-md p-1.5 text-slate-500 hover:bg-red-50 hover:text-red-600"
                              title="Deactivate"
                            >
                              <Power size={15} />
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="mt-4 flex items-center justify-between text-xs text-slate-500">
              <span>
                Showing {clampedPageIndex * pageSize + 1}-{Math.min(filtered.length, (clampedPageIndex + 1) * pageSize)} of{' '}
                {filtered.length}
              </span>
              <div className="flex gap-2">
                <SecondaryButton onClick={() => setPageIndex((p) => Math.max(0, p - 1))} disabled={clampedPageIndex === 0}>
                  Previous
                </SecondaryButton>
                <SecondaryButton
                  onClick={() => setPageIndex((p) => Math.min(pageCount - 1, p + 1))}
                  disabled={clampedPageIndex >= pageCount - 1}
                >
                  Next
                </SecondaryButton>
              </div>
            </div>
          </>
        )}
      </Card>

      {drawerOpen && (
        <Modal title={formTitleFor(editing)} onClose={closeDrawer}>
          <form
            className="space-y-3"
            onSubmit={(e) => {
              e.preventDefault()
              saveMutation.mutate()
            }}
          >
            {saveMutation.isError && (
              <FormErrorBanner title="Could not save" errors={describeApiErrorList(saveMutation.error, 'Could not save.')} />
            )}
            {renderForm(form, setForm, saveFieldErrors, saveMutation.error)}
            <PrimaryButton type="submit" disabled={saveMutation.isPending || !formValid(form)} className="w-full justify-center">
              Save
            </PrimaryButton>
          </form>
        </Modal>
      )}

      {dependencyAlert && (
        <Modal title="Cannot deactivate" onClose={() => setDependencyAlert(null)}>
          <p className="mb-3 text-sm text-slate-600">
            This record is still actively referenced and cannot be deactivated until those references are removed or
            reassigned:
          </p>
          <ul className="mb-4 space-y-1 text-sm">
            {dependencyAlert.check.usages.map((usage) => (
              <li key={usage.table} className="flex justify-between rounded-md bg-red-50 px-3 py-2 text-red-700">
                <span>{usage.label}</span>
                <span className="font-medium">{usage.activeCount}</span>
              </li>
            ))}
          </ul>
          <SecondaryButton onClick={() => setDependencyAlert(null)} className="w-full justify-center">
            Close
          </SecondaryButton>
        </Modal>
      )}
    </div>
  )
}
