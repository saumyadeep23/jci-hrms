import { useEffect, useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { CheckCircle2, Download, Upload, XCircle } from 'lucide-react'
import { apiClient } from '../../api/client'
import { describeApiError } from '../../lib/apiError'
import { DateField } from '../../components/common/DateField'
import { useToast } from '../../components/common/ToastProvider'
import { Badge, Card, ErrorState, PageHeader, PrimaryButton, SecondaryButton } from '../../components/common/ui'
import type { BaselineTakeOnRequest, BaselineTakeOnResponse, EmployeeResponse, LeaveTypeResponse, Page } from '../../types/api'

const EL_CODE = 'EL'
/** LWP/COMMUTED are system-generated markers, never something HR baselines an opening balance for. */
const NON_BASELINABLE_CODES = new Set(['LWP', 'COMMUTED'])
const CSV_HEADER = 'employeeCode,leaveTypeCode,asOnDate,openingBalance,openingEncashableEl,openingEnjoyableEl,physicalServiceBookFolio,verificationOrderRef'

type RowStatus = 'idle' | 'pending' | 'success' | 'error'

interface FormRow {
  leaveType: LeaveTypeResponse
  openingBalance: string
  openingEncashableEl: string
  openingEnjoyableEl: string
  status: RowStatus
  error: string | null
}

interface CsvRow {
  lineNumber: number
  employeeCode: string
  leaveTypeCode: string
  asOnDate: string
  openingBalance: string
  openingEncashableEl: string
  openingEnjoyableEl: string
  physicalServiceBookFolio: string
  verificationOrderRef: string
  problems: string[]
  status: RowStatus
  error: string | null
}

function isTwoDecimal(value: string): boolean {
  return /^\d+(\.\d{1,2})?$/.test(value.trim())
}

function downloadTemplate() {
  const sample = '1001,EL,2026-01-01,100.00,50.00,50.00,SB-FOLIO-12,HR/ORD/2026/001\n1001,CL,2026-01-01,8.00,,,SB-FOLIO-12,HR/ORD/2026/001'
  const blob = new Blob([CSV_HEADER + '\n' + sample], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = 'baseline-takeon-template.csv'
  link.click()
  URL.revokeObjectURL(url)
}

function parseCsv(text: string): CsvRow[] {
  const lines = text.split(/\r?\n/).filter((line) => line.trim().length > 0)
  const dataLines = lines[0]?.toLowerCase().startsWith('employeecode') ? lines.slice(1) : lines
  return dataLines.map((line, index) => {
    const cols = line.split(',').map((c) => c.trim())
    const [employeeCode = '', leaveTypeCode = '', asOnDate = '', openingBalance = '', openingEncashableEl = '',
      openingEnjoyableEl = '', physicalServiceBookFolio = '', verificationOrderRef = ''] = cols

    const problems: string[] = []
    if (!employeeCode) problems.push('Missing employeeCode')
    if (!leaveTypeCode) problems.push('Missing leaveTypeCode')
    if (!/^\d{4}-\d{2}-\d{2}$/.test(asOnDate)) problems.push('asOnDate must be yyyy-MM-dd')
    if (!openingBalance || !isTwoDecimal(openingBalance)) problems.push('openingBalance must be a non-negative number with <=2 decimals')
    if (!verificationOrderRef) problems.push('Missing verificationOrderRef')
    if (!physicalServiceBookFolio) problems.push('Missing physicalServiceBookFolio')

    if (leaveTypeCode === EL_CODE) {
      if (!openingEncashableEl || !isTwoDecimal(openingEncashableEl)) problems.push('openingEncashableEl required for EL')
      if (!openingEnjoyableEl || !isTwoDecimal(openingEnjoyableEl)) problems.push('openingEnjoyableEl required for EL')
      const encashable = Number(openingEncashableEl)
      const enjoyable = Number(openingEnjoyableEl)
      const total = Number(openingBalance)
      if (!Number.isNaN(encashable) && !Number.isNaN(enjoyable) && !Number.isNaN(total)) {
        if (encashable < 0 || enjoyable < 0) problems.push('Encashable/Enjoyable must be non-negative')
        if (Math.abs(encashable + enjoyable - total) > 0.01) problems.push('Encashable + Enjoyable != openingBalance')
        if (encashable > 300) problems.push('Encashable EL exceeds the 300-day statutory encashment cap')
      }
      if (!Number.isNaN(total) && total > 300) problems.push('EL opening balance exceeds 300-day cap')
    }

    return {
      lineNumber: index + 2,
      employeeCode,
      leaveTypeCode,
      asOnDate,
      openingBalance,
      openingEncashableEl,
      openingEnjoyableEl,
      physicalServiceBookFolio,
      verificationOrderRef,
      problems,
      status: 'idle',
      error: null,
    }
  })
}

/** ALMS Phase 3, Section 1 - restricted to hasAnyRole('HR_ADMIN', 'SUPER_ADMIN') at the route level (see App.tsx). */
export function LeaveBaselineTakeOnPage() {
  const { show } = useToast()
  const [tab, setTab] = useState<'single' | 'bulk'>('single')

  // ---- shared lookups ----
  const employeesQuery = useQuery({
    queryKey: ['employees-all-for-baseline'],
    queryFn: async () => (await apiClient.get<Page<EmployeeResponse>>('/employees', { params: { size: 500 } })).data.content,
  })
  const leaveTypesQuery = useQuery({
    queryKey: ['leave-types-all-for-baseline'],
    queryFn: async () => (await apiClient.get<Page<LeaveTypeResponse>>('/leave-types', { params: { size: 50 } })).data.content,
  })
  const baselinableTypes = (leaveTypesQuery.data ?? []).filter((lt) => lt.active && !NON_BASELINABLE_CODES.has(lt.code))

  // ---- single-employee form ----
  const [employeeSearch, setEmployeeSearch] = useState('')
  const [employeeId, setEmployeeId] = useState('')
  const [asOnDate, setAsOnDate] = useState('')
  const [verificationOrderRef, setVerificationOrderRef] = useState('')
  const [physicalServiceBookFolio, setPhysicalServiceBookFolio] = useState('')
  const [rows, setRows] = useState<FormRow[] | null>(null)
  const [locked, setLocked] = useState(false)

  const selectedEmployee = (employeesQuery.data ?? []).find((e) => String(e.id) === employeeId)
  const employeeMatches = (employeesQuery.data ?? []).filter((e) => {
    const q = employeeSearch.trim().toLowerCase()
    if (!q) return false
    return e.employeeCode.toLowerCase().includes(q) || e.fullName.toLowerCase().includes(q)
  })

  // Initializes `rows` once the leave-types lookup actually loads, rather
  // than eagerly on first render (when leaveTypesQuery.data is still
  // undefined) - the old lazy-init-on-first-call pattern here computed an
  // empty row set before the query resolved and then never recomputed it,
  // since a `[]` guard value reads as truthy and permanently short-circuited
  // re-initialization, leaving the table blank even after real leave types
  // arrived.
  useEffect(() => {
    if (rows === null && leaveTypesQuery.data) {
      setRows(
        baselinableTypes.map<FormRow>((lt) => ({
          leaveType: lt, openingBalance: '', openingEncashableEl: '', openingEnjoyableEl: '', status: 'idle', error: null,
        })),
      )
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [leaveTypesQuery.data, rows])

  function updateRow(leaveTypeId: number, patch: Partial<FormRow>) {
    setRows((prev) => (prev ?? []).map((r) => (r.leaveType.id === leaveTypeId ? { ...r, ...patch } : r)))
  }

  const submitMutation = useMutation({
    mutationFn: async () => {
      const activeRows = (rows ?? []).filter((r) => r.openingBalance.trim() !== '')
      const results: FormRow[] = []
      for (const row of activeRows) {
        updateRow(row.leaveType.id, { status: 'pending', error: null })
        try {
          const payload: BaselineTakeOnRequest = {
            employeeId: Number(employeeId),
            leaveTypeId: row.leaveType.id,
            asOnDate,
            openingBalance: Number(row.openingBalance),
            openingEncashableEl: row.leaveType.code === EL_CODE ? Number(row.openingEncashableEl || 0) : undefined,
            openingEnjoyableEl: row.leaveType.code === EL_CODE ? Number(row.openingEnjoyableEl || 0) : undefined,
            physicalServiceBookFolio,
            verificationOrderRef,
          }
          await apiClient.post<BaselineTakeOnResponse>('/v1/admin/leave/baseline-takeon', payload)
          updateRow(row.leaveType.id, { status: 'success' })
          results.push({ ...row, status: 'success' })
        } catch (error) {
          const message = describeApiError(error, 'Could not submit this baseline.')
          updateRow(row.leaveType.id, { status: 'error', error: message })
          results.push({ ...row, status: 'error', error: message })
        }
      }
      return results
    },
    onSuccess: (results) => {
      const failed = results.filter((r) => r.status === 'error').length
      if (failed === 0) {
        show({ tone: 'success', message: `Baseline take-on recorded for ${results.length} leave type(s).` })
        setLocked(true)
      } else {
        show({ tone: 'error', message: `${failed} of ${results.length} rows failed - see the table for details.` })
      }
    },
  })

  const elRow = rows?.find((r) => r.leaveType.code === EL_CODE)
  const elSplitValid =
    !elRow ||
    elRow.openingBalance.trim() === '' ||
    (isTwoDecimal(elRow.openingEncashableEl || '0') &&
      isTwoDecimal(elRow.openingEnjoyableEl || '0') &&
      Number(elRow.openingEncashableEl || 0) >= 0 &&
      Number(elRow.openingEnjoyableEl || 0) >= 0 &&
      Math.abs(Number(elRow.openingEncashableEl || 0) + Number(elRow.openingEnjoyableEl || 0) - Number(elRow.openingBalance || 0)) < 0.01 &&
      Number(elRow.openingEncashableEl || 0) <= 300 &&
      Number(elRow.openingBalance || 0) <= 300)

  const anyRowFilled = (rows ?? []).some((r) => r.openingBalance.trim() !== '')
  const canSubmit =
    Boolean(employeeId) && Boolean(asOnDate) && verificationOrderRef.trim() !== '' && physicalServiceBookFolio.trim() !== '' &&
    anyRowFilled && elSplitValid && !locked && !submitMutation.isPending

  function resetForm() {
    setEmployeeSearch('')
    setEmployeeId('')
    setAsOnDate('')
    setVerificationOrderRef('')
    setPhysicalServiceBookFolio('')
    setRows(null)
    setLocked(false)
  }

  // ---- CSV bulk upload ----
  const [csvRows, setCsvRows] = useState<CsvRow[] | null>(null)
  const [bulkSubmitting, setBulkSubmitting] = useState(false)

  async function handleCsvFile(file: File) {
    const text = await file.text()
    setCsvRows(parseCsv(text))
  }

  async function submitBulk() {
    if (!csvRows) return
    setBulkSubmitting(true)
    const employees = employeesQuery.data ?? []
    const leaveTypes = leaveTypesQuery.data ?? []
    const updated = [...csvRows]
    for (let i = 0; i < updated.length; i++) {
      const row = updated[i]
      if (row.problems.length > 0) continue
      const employee = employees.find((e) => e.employeeCode === row.employeeCode)
      const leaveType = leaveTypes.find((lt) => lt.code === row.leaveTypeCode)
      if (!employee || !leaveType) {
        updated[i] = { ...row, status: 'error', error: !employee ? 'Unknown employeeCode' : 'Unknown leaveTypeCode' }
        setCsvRows([...updated])
        continue
      }
      updated[i] = { ...row, status: 'pending' }
      setCsvRows([...updated])
      try {
        const payload: BaselineTakeOnRequest = {
          employeeId: employee.id,
          leaveTypeId: leaveType.id,
          asOnDate: row.asOnDate,
          openingBalance: Number(row.openingBalance),
          openingEncashableEl: row.openingEncashableEl ? Number(row.openingEncashableEl) : undefined,
          openingEnjoyableEl: row.openingEnjoyableEl ? Number(row.openingEnjoyableEl) : undefined,
          physicalServiceBookFolio: row.physicalServiceBookFolio,
          verificationOrderRef: row.verificationOrderRef,
        }
        await apiClient.post('/v1/admin/leave/baseline-takeon', payload)
        updated[i] = { ...row, status: 'success' }
      } catch (error) {
        updated[i] = { ...row, status: 'error', error: describeApiError(error, 'Submission failed') }
      }
      setCsvRows([...updated])
    }
    setBulkSubmitting(false)
    const failed = updated.filter((r) => r.status === 'error' || r.problems.length > 0).length
    show({
      tone: failed === 0 ? 'success' : 'error',
      message: failed === 0 ? `All ${updated.length} rows submitted.` : `${failed} of ${updated.length} rows failed or are invalid.`,
    })
  }

  const csvValidCount = csvRows?.filter((r) => r.problems.length === 0).length ?? 0

  return (
    <div>
      <PageHeader
        title="Leave Baseline Take-On"
        description="One-time opening-balance verification against the physical service book (ALMS)"
      />

      <div className="mb-4 flex gap-2">
        <button
          type="button"
          onClick={() => setTab('single')}
          className={`rounded-full px-3 py-1.5 text-xs font-medium transition-colors ${tab === 'single' ? 'bg-brand-forest text-white' : 'bg-slate-100 text-slate-600'}`}
        >
          Single Employee
        </button>
        <button
          type="button"
          onClick={() => setTab('bulk')}
          className={`rounded-full px-3 py-1.5 text-xs font-medium transition-colors ${tab === 'bulk' ? 'bg-brand-forest text-white' : 'bg-slate-100 text-slate-600'}`}
        >
          CSV Bulk Upload
        </button>
      </div>

      {tab === 'single' && (
        <Card>
          {locked && (
            <div className="mb-4 flex items-center gap-2 rounded-md border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-700">
              <CheckCircle2 size={16} /> Baseline submitted and locked. Start a new take-on to record another employee.
              <SecondaryButton type="button" onClick={resetForm} className="ml-auto">
                New Take-On
              </SecondaryButton>
            </div>
          )}

          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div className="relative">
              <label className="mb-1 block text-xs font-medium text-slate-600">Employee</label>
              <input
                disabled={locked}
                value={employeeId ? `${selectedEmployee?.employeeCode} - ${selectedEmployee?.fullName}` : employeeSearch}
                onChange={(e) => {
                  setEmployeeSearch(e.target.value)
                  setEmployeeId('')
                }}
                placeholder="Search by employee code or name..."
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm disabled:bg-slate-50"
              />
              {employeeSearch && !employeeId && (
                <div className="absolute z-10 mt-1 max-h-48 w-full overflow-y-auto rounded-md border border-slate-200 bg-white shadow-lg">
                  {employeeMatches.length === 0 && <p className="p-2 text-xs text-slate-400">No matches.</p>}
                  {employeeMatches.slice(0, 20).map((emp) => (
                    <button
                      key={emp.id}
                      type="button"
                      onClick={() => {
                        setEmployeeId(String(emp.id))
                        setEmployeeSearch('')
                      }}
                      className="block w-full px-3 py-2 text-left text-sm hover:bg-slate-50"
                    >
                      <span className="font-medium">{emp.employeeCode}</span> - {emp.fullName}
                      <span className="block text-xs text-slate-400">{emp.designationTitle}</span>
                    </button>
                  ))}
                </div>
              )}
              {selectedEmployee && (
                <p className="mt-1 text-xs text-slate-500">
                  {selectedEmployee.designationTitle} · {selectedEmployee.departmentName}
                </p>
              )}
            </div>

            <DateField label="As-On Date" required disabled={locked} value={asOnDate} onChange={setAsOnDate} />

            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Verification Order Reference *</label>
              <input
                required
                disabled={locked}
                value={verificationOrderRef}
                onChange={(e) => setVerificationOrderRef(e.target.value)}
                placeholder="HR/ORD/2026/001"
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm disabled:bg-slate-50"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Physical Service Book Folio *</label>
              <input
                required
                disabled={locked}
                value={physicalServiceBookFolio}
                onChange={(e) => setPhysicalServiceBookFolio(e.target.value)}
                placeholder="SB-FOLIO-12"
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm disabled:bg-slate-50"
              />
            </div>
          </div>

          {leaveTypesQuery.isError && (
            <div className="mt-5">
              <ErrorState message="Could not load leave types - the opening-balance table cannot be built without them. Try reloading the page." />
            </div>
          )}
          {leaveTypesQuery.isSuccess && baselinableTypes.length === 0 && (
            <div className="mt-5">
              <ErrorState message="No active leave types are configured. Add leave types via the Leave Type Master before recording a baseline take-on." />
            </div>
          )}

          {baselinableTypes.length > 0 && (
          <div className="mt-5 overflow-x-auto">
            <table className="w-full min-w-[720px] border-collapse text-sm">
              <thead>
                <tr className="border-b border-slate-300 text-left text-slate-500">
                  <th className="py-2 pr-3">Leave Type</th>
                  <th className="py-2 pr-3">Opening Encashable EL</th>
                  <th className="py-2 pr-3">Opening Enjoyable EL</th>
                  <th className="py-2 pr-3">Opening Balance</th>
                  <th className="py-2 pl-3">Status</th>
                </tr>
              </thead>
              <tbody>
                {(rows ?? []).map((row) => {
                  const isEl = row.leaveType.code === EL_CODE
                  const total = isEl
                    ? (Number(row.openingEncashableEl || 0) + Number(row.openingEnjoyableEl || 0)).toFixed(2)
                    : row.openingBalance
                  return (
                    <tr key={row.leaveType.id} className="border-b border-slate-100">
                      <td className="py-2 pr-3 font-medium">{row.leaveType.code} - {row.leaveType.name}</td>
                      <td className="py-2 pr-3">
                        {isEl ? (
                          <input
                            type="number"
                            min="0"
                            step="0.01"
                            disabled={locked}
                            value={row.openingEncashableEl}
                            onChange={(e) => {
                              const encashableEl = e.target.value
                              updateRow(row.leaveType.id, {
                                openingEncashableEl: encashableEl,
                                openingBalance: (Number(encashableEl || 0) + Number(row.openingEnjoyableEl || 0)).toFixed(2),
                              })
                            }}
                            className="w-24 rounded-md border border-slate-300 px-2 py-1 text-sm disabled:bg-slate-50"
                          />
                        ) : (
                          <span className="text-slate-300">—</span>
                        )}
                      </td>
                      <td className="py-2 pr-3">
                        {isEl ? (
                          <input
                            type="number"
                            min="0"
                            step="0.01"
                            disabled={locked}
                            value={row.openingEnjoyableEl}
                            onChange={(e) => {
                              const enjoyableEl = e.target.value
                              updateRow(row.leaveType.id, {
                                openingEnjoyableEl: enjoyableEl,
                                openingBalance: (Number(row.openingEncashableEl || 0) + Number(enjoyableEl || 0)).toFixed(2),
                              })
                            }}
                            className="w-24 rounded-md border border-slate-300 px-2 py-1 text-sm disabled:bg-slate-50"
                          />
                        ) : (
                          <span className="text-slate-300">—</span>
                        )}
                      </td>
                      <td className="py-2 pr-3">
                        {isEl ? (
                          <span className="font-medium tabular-nums">{total}</span>
                        ) : (
                          <input
                            type="number"
                            min="0"
                            step="0.01"
                            disabled={locked}
                            value={row.openingBalance}
                            onChange={(e) => updateRow(row.leaveType.id, { openingBalance: e.target.value })}
                            className="w-24 rounded-md border border-slate-300 px-2 py-1 text-sm disabled:bg-slate-50"
                          />
                        )}
                      </td>
                      <td className="py-2 pl-3">
                        {row.status === 'success' && <Badge tone="success">Recorded</Badge>}
                        {row.status === 'error' && <Badge tone="danger">{row.error ?? 'Failed'}</Badge>}
                        {row.status === 'pending' && <Badge tone="warning">Submitting...</Badge>}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
            {elRow && elRow.openingBalance.trim() !== '' && !elSplitValid && (
              <p className="mt-2 text-xs font-medium text-red-600">
                EL: Encashable and Enjoyable must each be non-negative, must sum to the total, Encashable must not
                exceed 300.00 days (statutory cap), and the total must not exceed 300.00 days.
              </p>
            )}
          </div>
          )}

          <PrimaryButton onClick={() => submitMutation.mutate()} disabled={!canSubmit} className="mt-4 w-full justify-center">
            Submit Baseline Take-On
          </PrimaryButton>
        </Card>
      )}

      {tab === 'bulk' && (
        <Card>
          <div className="mb-4 flex flex-wrap items-center gap-2">
            <SecondaryButton type="button" onClick={downloadTemplate}>
              <Download size={14} /> Download CSV Template
            </SecondaryButton>
            <label className="inline-flex cursor-pointer items-center gap-1.5 rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 shadow-sm hover:bg-slate-50">
              <Upload size={14} /> Choose CSV File
              <input
                type="file"
                accept=".csv"
                className="hidden"
                onChange={(e) => {
                  const file = e.target.files?.[0]
                  if (file) handleCsvFile(file)
                }}
              />
            </label>
            {csvRows && (
              <span className="text-xs text-slate-500">
                {csvValidCount} of {csvRows.length} rows valid
              </span>
            )}
          </div>

          {csvRows && (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[900px] border-collapse text-xs">
                <thead>
                  <tr className="border-b border-slate-300 text-left text-slate-500">
                    <th className="py-2 pr-2">Line</th>
                    <th className="py-2 pr-2">Employee</th>
                    <th className="py-2 pr-2">Type</th>
                    <th className="py-2 pr-2">As-On</th>
                    <th className="py-2 pr-2">Opening</th>
                    <th className="py-2 pr-2">Encashable/Enjoyable</th>
                    <th className="py-2 pr-2">Issues</th>
                    <th className="py-2 pl-2">Status</th>
                  </tr>
                </thead>
                <tbody>
                  {csvRows.map((row) => (
                    <tr key={row.lineNumber} className={`border-b border-slate-100 ${row.problems.length > 0 ? 'bg-red-50/50' : ''}`}>
                      <td className="py-1.5 pr-2 tabular-nums">{row.lineNumber}</td>
                      <td className="py-1.5 pr-2">{row.employeeCode}</td>
                      <td className="py-1.5 pr-2">{row.leaveTypeCode}</td>
                      <td className="py-1.5 pr-2">{row.asOnDate}</td>
                      <td className="py-1.5 pr-2 tabular-nums">{row.openingBalance}</td>
                      <td className="py-1.5 pr-2 tabular-nums">
                        {row.openingEncashableEl || row.openingEnjoyableEl ? `${row.openingEncashableEl} / ${row.openingEnjoyableEl}` : '—'}
                      </td>
                      <td className="py-1.5 pr-2 text-red-600">{row.problems.join('; ')}</td>
                      <td className="py-1.5 pl-2">
                        {row.status === 'success' && <CheckCircle2 size={14} className="text-emerald-500" />}
                        {row.status === 'error' && (
                          <span title={row.error ?? ''}>
                            <XCircle size={14} className="text-red-500" />
                          </span>
                        )}
                        {row.status === 'pending' && <span className="text-slate-400">…</span>}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>

              <PrimaryButton
                onClick={submitBulk}
                disabled={bulkSubmitting || csvValidCount === 0}
                className="mt-4 w-full justify-center"
              >
                {bulkSubmitting ? 'Submitting...' : `Submit ${csvValidCount} Valid Row(s)`}
              </PrimaryButton>
            </div>
          )}
        </Card>
      )}
    </div>
  )
}
