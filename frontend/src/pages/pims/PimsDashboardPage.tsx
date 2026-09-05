import { useNavigate } from 'react-router-dom'
import {
  Award,
  Banknote,
  Building2,
  CalendarClock,
  ClipboardList,
  FileSearch,
  IdCard,
  LayoutGrid,
  ListChecks,
  Search,
  Sparkles,
  UserPlus,
} from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import { Card, PageHeader, PrimaryButton, SecondaryButton } from '../../components/common/ui'

interface ModuleButton {
  label: string
  to?: string
  /** Buttons without a real destination yet are shown, clearly marked "Soon", rather than silently dropped or faked. */
  soon?: boolean
}

interface ModuleCard {
  title: string
  icon: LucideIcon
  operational: ModuleButton[]
  reports: ModuleButton[]
}

const QUICK_ACTIONS: (ModuleButton & { icon: LucideIcon })[] = [
  { label: 'New Onboarding Wizard', to: '/onboarding/new', icon: UserPlus },
  { label: 'Resume Drafts', to: '/onboarding/drafts', icon: ClipboardList },
  { label: 'Create Post', to: '/admin/posts/new', icon: Building2 },
  { label: 'Run Increment', to: '/reports/pims?tab=increment-due-list', icon: Banknote },
  { label: 'Search Directory', to: '/employees', icon: Search },
]

const MODULES: ModuleCard[] = [
  {
    title: 'Master Data Administration',
    icon: LayoutGrid,
    operational: [
      { label: 'Departments', to: '/admin/masters?tab=departments' },
      { label: 'Designations', to: '/admin/masters?tab=designations' },
      { label: 'Grade & Pay Scales', to: '/admin/masters?tab=grade-scales' },
      { label: 'Regional Offices / DPCs', to: '/admin/masters?tab=regional-offices' },
      { label: 'Vendors', to: '/admin/masters?tab=vendors' },
    ],
    reports: [
      { label: 'Master Audit', to: '/audit-logs' },
      { label: 'Location Directory', to: '/admin/regional-offices' },
    ],
  },
  {
    title: 'Sanctioned Post & Cadre Master',
    icon: Building2,
    operational: [
      { label: 'Create Post', to: '/admin/posts/new' },
      { label: 'Post Inventory', to: '/admin/posts' },
      { label: 'Dual-Charge Assignment', to: '/admin/posts' },
      { label: 'Hierarchy Builder', to: '/admin/posts' },
    ],
    reports: [
      { label: 'Cadre Strength Statement', to: '/reports/pims?tab=cadre-strength' },
      { label: 'Incumbency Ledger', to: '/admin/posts' },
    ],
  },
  {
    title: 'Onboarding & Registration',
    icon: UserPlus,
    operational: [
      { label: '8-Step Wizard', to: '/onboarding/new' },
      { label: 'Draft Queue', to: '/onboarding/drafts' },
      { label: 'Document Verification', to: '/onboarding/drafts' },
    ],
    reports: [
      { label: 'Joinee Register', to: '/reports/pims?tab=ad-hoc-builder' },
      { label: 'Draft Aging', to: '/onboarding/drafts' },
    ],
  },
  {
    title: 'Employee 360 & Lifecycle',
    icon: IdCard,
    operational: [
      { label: 'Directory', to: '/employees' },
      { label: 'Qualifications', to: '/onboarding/new' },
      { label: 'Past Service', to: '/onboarding/new' },
      { label: 'SCD-2 Bank Change', to: '/onboarding/new' },
    ],
    reports: [
      { label: '360 Master Export', to: '/reports/pims?tab=ad-hoc-builder' },
      { label: '100-Point Roster', to: '/reports/pims?tab=reservation-roster' },
      { label: '4-Tier Manpower', to: '/reports/pims?tab=manpower-4tier' },
    ],
  },
  {
    title: 'Career & Service Book',
    icon: Award,
    operational: [
      { label: 'Monthly Increment (3% IDA)', to: '/reports/pims?tab=increment-due-list' },
      { label: 'Service Book Logger', to: '/employees' },
      { label: 'Promotion / Transfer', to: '/admin/pims/movements' },
    ],
    reports: [
      { label: 'Increment Due List', to: '/reports/pims?tab=increment-due-list' },
      { label: 'APAR Matrix', to: '/reports/pims?tab=apar-matrix' },
    ],
  },
  {
    title: 'Superannuation & Separation',
    icon: CalendarClock,
    operational: [
      { label: 'Retirement Calculator', to: '/employees' },
      { label: 'Ministry Extension', to: '/employees' },
      { label: 'Resignation / VRS', soon: true },
    ],
    reports: [
      { label: '58/60-Yr Forecast', to: '/reports/pims?tab=superannuation' },
      { label: 'Pension Ledger', to: '/reports/pims?tab=superannuation' },
    ],
  },
]

/** PIMS_SPEC.md dashboard task: module-driven navigation dashboard at /pims. */
export function PimsDashboardPage() {
  const navigate = useNavigate()

  return (
    <div>
      <PageHeader
        title="PIMS Workspace"
        description="Personnel Information Management System - master data, sanctioned posts, onboarding, and lifecycle reporting"
      />

      <Card className="mb-6">
        <p className="mb-3 flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-slate-500">
          <Sparkles size={14} /> Quick Actions
        </p>
        <div className="flex flex-wrap gap-2">
          {QUICK_ACTIONS.map((action) => (
            <PrimaryButton key={action.label} onClick={() => navigate(action.to!)}>
              <action.icon size={15} /> {action.label}
            </PrimaryButton>
          ))}
        </div>
      </Card>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
        {MODULES.map((module) => (
          <Card key={module.title}>
            <div className="mb-3 flex items-center gap-2">
              <span className="flex h-8 w-8 items-center justify-center rounded-md bg-brand-forest/10 text-brand-forest">
                <module.icon size={17} />
              </span>
              <h2 className="text-sm font-semibold text-slate-800">{module.title}</h2>
            </div>

            <p className="mb-1 text-[11px] font-semibold uppercase tracking-wide text-slate-400">Operational</p>
            <div className="mb-3 flex flex-wrap gap-1.5">
              {module.operational.map((btn) => (
                <ModuleButtonView key={btn.label} button={btn} />
              ))}
            </div>

            <p className="mb-1 flex items-center gap-1 text-[11px] font-semibold uppercase tracking-wide text-slate-400">
              <FileSearch size={11} /> Reports
            </p>
            <div className="flex flex-wrap gap-1.5">
              {module.reports.map((btn) => (
                <ModuleButtonView key={btn.label} button={btn} tone="report" />
              ))}
            </div>
          </Card>
        ))}
      </div>
    </div>
  )
}

function ModuleButtonView({ button, tone = 'operational' }: { button: ModuleButton; tone?: 'operational' | 'report' }) {
  const navigate = useNavigate()
  if (button.soon || !button.to) {
    return (
      <span className="inline-flex cursor-not-allowed items-center gap-1 rounded-md border border-dashed border-slate-200 px-2.5 py-1 text-xs text-slate-400">
        {button.label} <span className="rounded-full bg-slate-100 px-1.5 py-0.5 text-[10px]">Soon</span>
      </span>
    )
  }
  return (
    <SecondaryButton
      onClick={() => navigate(button.to!)}
      className={`px-2.5 py-1 text-xs ${tone === 'report' ? 'border-brand-forest/30 text-brand-forest' : ''}`}
    >
      {button.label}
      {tone === 'report' && <ListChecks size={11} />}
    </SecondaryButton>
  )
}
