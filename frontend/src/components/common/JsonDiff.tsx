function prettyLines(raw: string | null): string[] {
  if (!raw) return []
  try {
    return JSON.stringify(JSON.parse(raw), null, 2).split('\n')
  } catch {
    return [raw]
  }
}

/** Minimal line-based JSON diff - no external dependency, good enough for audit before/after snapshots. */
export function JsonDiff({ before, after }: { before: string | null; after: string | null }) {
  const beforeLines = prettyLines(before)
  const afterLines = prettyLines(after)
  const maxLines = Math.max(beforeLines.length, afterLines.length)

  const rows = Array.from({ length: maxLines }, (_, i) => ({
    before: beforeLines[i] ?? '',
    after: afterLines[i] ?? '',
    changed: beforeLines[i] !== afterLines[i],
  }))

  if (maxLines === 0) {
    return <p className="text-xs text-slate-400">No before/after snapshot recorded for this entry.</p>
  }

  return (
    <div className="grid grid-cols-2 gap-px overflow-hidden rounded-md border border-slate-200 bg-slate-200 text-xs">
      <div className="bg-white">
        <p className="border-b border-slate-200 bg-slate-50 px-2 py-1 font-medium text-slate-500">Before</p>
        <pre className="max-h-64 overflow-auto p-2 font-mono">
          {rows.map((row, i) => (
            <div key={i} className={row.changed ? 'bg-red-50 text-red-700' : ''}>
              {row.before || ' '}
            </div>
          ))}
        </pre>
      </div>
      <div className="bg-white">
        <p className="border-b border-slate-200 bg-slate-50 px-2 py-1 font-medium text-slate-500">After</p>
        <pre className="max-h-64 overflow-auto p-2 font-mono">
          {rows.map((row, i) => (
            <div key={i} className={row.changed ? 'bg-emerald-50 text-emerald-700' : ''}>
              {row.after || ' '}
            </div>
          ))}
        </pre>
      </div>
    </div>
  )
}
