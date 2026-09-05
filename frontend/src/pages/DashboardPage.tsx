import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { CalendarDays, Clock, ScrollText, ShieldCheck, Users } from 'lucide-react'
import { apiClient } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { useMyEmploymentCategory } from '../auth/useMyEmploymentCategory'
import { Card, PageHeader } from '../components/common/ui'
import { HolidayCalendarCard } from '../components/dashboard/HolidayCalendarCard'
import { flatNavItemsForRoles } from '../components/layout/nav-config'
import type { Page } from '../types/api'

function LiveClock() {
  const [now, setNow] = useState(new Date())
  useEffect(() => {
    const id = setInterval(() => setNow(new Date()), 1000)
    return () => clearInterval(id)
  }, [])
  return (
    <Card className="flex items-center gap-3">
      <Clock className="text-brand-forest" size={22} />
      <div>
        <p className="text-lg font-semibold tabular-nums text-slate-800">{now.toLocaleTimeString('en-IN')}</p>
        <p className="text-xs text-slate-400">{now.toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })}</p>
      </div>
    </Card>
  )
}

function MetricCard({
  icon: Icon,
  label,
  queryKey,
  path,
}: {
  icon: typeof Users
  label: string
  queryKey: string
  path: string
}) {
  const { data, isLoading, isError } = useQuery({
    queryKey: [queryKey],
    queryFn: async () => (await apiClient.get<Page<unknown>>(path, { params: { size: 1 } })).data,
  })

  return (
    <Card className="flex items-center gap-3">
      <Icon className="text-brand-forest" size={22} />
      <div>
        <p className="text-2xl font-semibold text-slate-800">
          {isLoading ? '···' : isError ? '—' : data?.totalElements ?? 0}
        </p>
        <p className="text-xs text-slate-400">{label}</p>
      </div>
    </Card>
  )
}

export function DashboardPage() {
  const { roles, subject, hasRole } = useAuth()
  const employmentCategory = useMyEmploymentCategory()
  // Some routes now appear twice in the flattened nav (e.g. /attendance and /leaves are
  // both a flat ESS link and the first child of an HR-admin sidebar group covering the
  // same page) - de-duplicated by destination so quick-action tiles aren't shown twice.
  const seenPaths = new Set<string>()
  const items = flatNavItemsForRoles(roles, employmentCategory).filter((item) => {
    if (item.to === '/' || seenPaths.has(item.to)) return false
    seenPaths.add(item.to)
    return true
  })

  return (
    <div>
      <PageHeader title={`Welcome, ${subject ?? 'colleague'}`} description="The Jute Corporation of India Limited - HRMS Portal" />

      {/* Metric widgets */}
      <div className="mb-6 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <LiveClock />
        {hasRole('HR_ADMIN', 'SUPER_ADMIN') && (
          <MetricCard icon={Users} label="Employees on record" queryKey="metric-employees" path="/employees" />
        )}
        {hasRole('FINANCE_ADMIN', 'SUPER_ADMIN') && (
          <MetricCard icon={ScrollText} label="Payroll runs" queryKey="metric-payroll-runs" path="/payroll/runs" />
        )}
        {hasRole('SUPER_ADMIN') && (
          <MetricCard icon={ShieldCheck} label="Audit log entries" queryKey="metric-audit-logs" path="/audit-logs" />
        )}
        {!hasRole('HR_ADMIN', 'FINANCE_ADMIN', 'SUPER_ADMIN') && (
          <Card className="flex items-center gap-3">
            <CalendarDays className="text-brand-forest" size={22} />
            <div>
              <p className="text-sm font-medium text-slate-700">Have you punched in today?</p>
              <Link to="/attendance" className="text-xs text-brand-forest hover:underline">
                Go to Attendance →
              </Link>
            </div>
          </Card>
        )}
      </div>

      <div className="mb-6">
        <HolidayCalendarCard />
      </div>

      {/* Quick-action cards */}
      <h2 className="mb-3 text-sm font-semibold text-slate-500">Quick Actions</h2>
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
        {items.map((item) => (
          <Link key={item.to} to={item.to}>
            <Card className="flex h-full flex-col items-center gap-2 text-center transition-shadow hover:shadow-md">
              <item.icon className="text-brand-forest" size={26} />
              <span className="text-sm font-medium text-slate-700">{item.label}</span>
            </Card>
          </Link>
        ))}
      </div>
    </div>
  )
}
