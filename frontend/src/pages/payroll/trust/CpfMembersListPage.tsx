import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Download, Plus, RefreshCw } from 'lucide-react'
import { apiClient } from '../../../api/client'
import { formatDate } from '../../../lib/date'
import { useDebouncedValue } from '../../../lib/useDebouncedValue'
import { PageHeader, PrimaryButton, SecondaryButton } from '../../../components/common/ui'
import type { CpfTrustMemberResponse, CpfTrustMemberSummaryResponse, Page } from '../../../types/api'
import { CpfMemberKpiCards } from './members/CpfMemberKpiCards'
import { CpfMemberFilterBar } from './members/CpfMemberFilterBar'
import { CpfMemberExceptionBanner } from './members/CpfMemberExceptionBanner'
import { CpfMembersTable } from './members/CpfMembersTable'
import type { CpfMemberSort } from './members/CpfMembersTable'
import { CpfMemberDrawer } from './members/CpfMemberDrawer'
import type { CpfMemberDrawerTab } from './members/CpfMemberDrawer'
import { CpfLedgerDrawer } from './members/CpfLedgerDrawer'
import { UpdateUanDialog } from './members/UpdateUanDialog'
import { EpsEligibilityDialog } from './members/EpsEligibilityDialog'
import { DEFAULT_FILTERS, resolveSeparationPeriod } from './members/cpfMemberTypes'
import type { CpfMemberFilters, CpfMemberQuickFilter } from './members/cpfMemberTypes'
import { exportMembersCsv } from './members/cpfMemberUtils'

const PAGE_SIZE_DEFAULT = 25

function quickFilterFor(filters: CpfMemberFilters): CpfMemberQuickFilter {
  if (filters.uanMissing) return 'uanMissing'
  if (filters.settlementStatus === 'OVERDUE') return 'overdue'
  if (filters.settlementStatus === 'PENDING') return 'pending'
  if (filters.status === 'ACTIVE') return 'active'
  if (filters.status === 'SEPARATED') return 'separated'
  return 'all'
}

function filtersForQuickFilter(filter: CpfMemberQuickFilter): CpfMemberFilters {
  switch (filter) {
    case 'active':
      return { ...DEFAULT_FILTERS, status: 'ACTIVE' }
    case 'separated':
      return { ...DEFAULT_FILTERS, status: 'SEPARATED' }
    case 'pending':
      return { ...DEFAULT_FILTERS, status: 'SEPARATED', settlementStatus: 'PENDING' }
    case 'overdue':
      return { ...DEFAULT_FILTERS, status: 'SEPARATED', settlementStatus: 'OVERDUE' }
    case 'uanMissing':
      return { ...DEFAULT_FILTERS, uanMissing: true }
    default:
      return DEFAULT_FILTERS
  }
}

/**
 * Route: /payroll/trust/members - redesigned CPF administration dashboard (not an employee directory). CPF
 * A/C No. is the primary identifier and default sort key throughout; Employee Code/Department are supporting
 * context only, never standalone columns - see CpfTrustMemberDirectoryService's own javadoc for why.
 */
export function CpfMembersListPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const [filters, setFilters] = useState<CpfMemberFilters>(DEFAULT_FILTERS)
  const debouncedSearch = useDebouncedValue(filters.search, 300)
  const [sort, setSort] = useState<CpfMemberSort>({ column: 'cpfAcNo', direction: 'asc' })
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(PAGE_SIZE_DEFAULT)
  const [exporting, setExporting] = useState(false)

  const [drawerMember, setDrawerMember] = useState<CpfTrustMemberResponse | null>(null)
  const [drawerTab, setDrawerTab] = useState<CpfMemberDrawerTab>('overview')
  const [ledgerMember, setLedgerMember] = useState<CpfTrustMemberResponse | null>(null)
  const [uanMember, setUanMember] = useState<CpfTrustMemberResponse | null>(null)
  const [epsMember, setEpsMember] = useState<CpfTrustMemberResponse | null>(null)

  const activeQuickFilter = quickFilterFor(filters)

  const separationRange = useMemo(() => {
    if (filters.separationPeriod === 'custom') return { from: filters.separationFrom || undefined, to: filters.separationTo || undefined }
    return resolveSeparationPeriod(filters.separationPeriod)
  }, [filters.separationPeriod, filters.separationFrom, filters.separationTo])

  const queryParams = {
    search: debouncedSearch || undefined,
    status: filters.status || undefined,
    settlementStatus: filters.settlementStatus || undefined,
    uanMissing: filters.uanMissing,
    separationFrom: separationRange.from,
    separationTo: separationRange.to,
    sort: `${sort.column},${sort.direction}`,
    page,
    size: pageSize,
  }

  const summaryQuery = useQuery({
    queryKey: ['cpf-trust-members-summary'],
    queryFn: async () => (await apiClient.get<CpfTrustMemberSummaryResponse>('/v1/payroll/trust/members/summary')).data,
  })

  const listQuery = useQuery({
    queryKey: ['cpf-trust-members', queryParams],
    queryFn: async () => (await apiClient.get<Page<CpfTrustMemberResponse>>('/v1/payroll/trust/members', { params: queryParams })).data,
    placeholderData: (previous) => previous,
  })

  function updateFilters(next: CpfMemberFilters) {
    setFilters(next)
    setPage(0)
  }

  function selectQuickFilter(filter: CpfMemberQuickFilter) {
    updateFilters(filtersForQuickFilter(filter))
  }

  function refreshAll() {
    queryClient.invalidateQueries({ queryKey: ['cpf-trust-members'] })
    queryClient.invalidateQueries({ queryKey: ['cpf-trust-members-summary'] })
  }

  async function handleExport() {
    setExporting(true)
    try {
      const response = await apiClient.get<Page<CpfTrustMemberResponse>>('/v1/payroll/trust/members', {
        params: { ...queryParams, page: 0, size: 5000 },
      })
      exportMembersCsv(response.data.content)
    } finally {
      setExporting(false)
    }
  }

  function openDrawer(member: CpfTrustMemberResponse, tab: CpfMemberDrawerTab = 'overview') {
    setDrawerMember(member)
    setDrawerTab(tab)
  }

  return (
    <div>
      <PageHeader
        title="CPF Trust Members"
        description="CPF account, balance, interest & settlement monitoring"
        actions={
          <>
            <PrimaryButton onClick={() => navigate('/onboarding/new')}>
              <Plus size={15} /> Add Member
            </PrimaryButton>
            <SecondaryButton onClick={handleExport} disabled={exporting}>
              <Download size={14} /> {exporting ? 'Exporting...' : 'Export'}
            </SecondaryButton>
            <SecondaryButton onClick={refreshAll}>
              <RefreshCw size={14} /> Refresh
            </SecondaryButton>
          </>
        }
      />

      {listQuery.dataUpdatedAt > 0 && (
        <p className="-mt-4 mb-4 text-xs text-slate-400">
          Last updated: {formatDate(new Date(listQuery.dataUpdatedAt), 'dd-MM-yyyy hh:mm a')}
        </p>
      )}

      <CpfMemberKpiCards
        summary={summaryQuery.data}
        isLoading={summaryQuery.isLoading}
        active={activeQuickFilter}
        onSelect={selectQuickFilter}
      />

      <CpfMemberFilterBar filters={filters} onChange={updateFilters} activeQuickFilter={activeQuickFilter} onQuickFilterSelect={selectQuickFilter} />

      <CpfMemberExceptionBanner summary={summaryQuery.data} onViewExceptions={() => selectQuickFilter('overdue')} />

      <CpfMembersTable
        page={listQuery.data}
        isLoading={listQuery.isLoading}
        isError={listQuery.isError}
        sort={sort}
        onSortChange={(next) => {
          setSort(next)
          setPage(0)
        }}
        onRowClick={openDrawer}
        onViewLedger={setLedgerMember}
        onUpdateUan={setUanMember}
        onCheckEpsEligibility={setEpsMember}
        onPageChange={setPage}
        onPageSizeChange={(size) => {
          setPageSize(size)
          setPage(0)
        }}
      />

      {drawerMember && (
        <CpfMemberDrawer
          member={drawerMember}
          initialTab={drawerTab}
          onClose={() => setDrawerMember(null)}
          onOpenLedger={() => setLedgerMember(drawerMember)}
          onUpdateUan={() => setUanMember(drawerMember)}
        />
      )}
      {ledgerMember && <CpfLedgerDrawer member={ledgerMember} onClose={() => setLedgerMember(null)} />}
      {uanMember && <UpdateUanDialog member={uanMember} onClose={() => setUanMember(null)} />}
      {epsMember && <EpsEligibilityDialog member={epsMember} onClose={() => setEpsMember(null)} />}
    </div>
  )
}
