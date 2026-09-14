import { ChevronLeft, ChevronRight } from 'lucide-react'

const PAGE_SIZE_OPTIONS = [10, 25, 50, 100]

/** Section 18/25 - "Showing 1-25 of 3,842 members" + rows-per-page + numbered pagination (‹ 1 2 3 4 5 … ›). */
export function CpfPagination({
  page,
  pageSize,
  totalElements,
  totalPages,
  onPageChange,
  onPageSizeChange,
}: {
  page: number
  pageSize: number
  totalElements: number
  totalPages: number
  onPageChange: (page: number) => void
  onPageSizeChange: (size: number) => void
}) {
  const from = totalElements === 0 ? 0 : page * pageSize + 1
  const to = Math.min(totalElements, (page + 1) * pageSize)

  const pageNumbers = visiblePageNumbers(page, totalPages)

  return (
    <div className="mt-4 flex flex-wrap items-center justify-between gap-3 text-xs text-slate-500">
      <span>
        Showing {from.toLocaleString('en-IN')}-{to.toLocaleString('en-IN')} of {totalElements.toLocaleString('en-IN')} member
        {totalElements === 1 ? '' : 's'}
      </span>

      <div className="flex items-center gap-4">
        <div className="flex items-center gap-2">
          <span>Rows per page</span>
          <select
            value={pageSize}
            onChange={(e) => onPageSizeChange(Number(e.target.value))}
            className="rounded-md border border-slate-300 px-2 py-1"
          >
            {PAGE_SIZE_OPTIONS.map((size) => (
              <option key={size} value={size}>{size}</option>
            ))}
          </select>
        </div>

        <nav className="flex items-center gap-1" aria-label="Pagination">
          <button
            type="button"
            disabled={page === 0}
            onClick={() => onPageChange(page - 1)}
            aria-label="Previous page"
            className="rounded-md border border-slate-300 p-1.5 disabled:opacity-40"
          >
            <ChevronLeft size={13} />
          </button>
          {pageNumbers.map((n, i) =>
            n === null ? (
              <span key={`ellipsis-${i}`} className="px-1.5">…</span>
            ) : (
              <button
                key={n}
                type="button"
                onClick={() => onPageChange(n)}
                aria-current={n === page ? 'page' : undefined}
                className={`min-w-[26px] rounded-md px-2 py-1 ${n === page ? 'bg-brand-forest text-white' : 'border border-slate-300 hover:bg-slate-50'}`}
              >
                {n + 1}
              </button>
            ),
          )}
          <button
            type="button"
            disabled={page >= totalPages - 1}
            onClick={() => onPageChange(page + 1)}
            aria-label="Next page"
            className="rounded-md border border-slate-300 p-1.5 disabled:opacity-40"
          >
            <ChevronRight size={13} />
          </button>
        </nav>
      </div>
    </div>
  )
}

/** 0-indexed page numbers to render, with `null` standing in for an ellipsis - always shows first, last, current, and one neighbour on each side. */
function visiblePageNumbers(page: number, totalPages: number): (number | null)[] {
  if (totalPages <= 1) return totalPages === 1 ? [0] : []
  const result: (number | null)[] = []
  const window = new Set([0, totalPages - 1, page, page - 1, page + 1].filter((n) => n >= 0 && n < totalPages))
  const sorted = Array.from(window).sort((a, b) => a - b)
  let previous: number | null = null
  for (const n of sorted) {
    if (previous !== null && n - previous > 1) result.push(null)
    result.push(n)
    previous = n
  }
  return result
}
