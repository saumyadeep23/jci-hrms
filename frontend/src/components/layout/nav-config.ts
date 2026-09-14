import type { LucideIcon } from 'lucide-react'
import {
  BarChart3,
  Banknote,
  Building2,
  CalendarCheck,
  CalendarClock,
  Camera,
  CircleDollarSign,
  ClipboardCheck,
  Coins,
  FileSignature,
  FileText,
  Gavel,
  GraduationCap,
  HandCoins,
  History,
  Landmark,
  LayoutDashboard,
  LayoutGrid,
  ListChecks,
  MoveRight,
  PiggyBank,
  ReceiptText,
  Scale,
  ScrollText,
  ShieldAlert,
  ShieldCheck,
  TrendingUp,
  UploadCloud,
  User,
  Users,
  Wallet,
  Wrench,
} from 'lucide-react'
import type { Role } from '../../types/auth'
import type { EmploymentCategory } from '../../types/api'

export interface NavItem {
  to: string
  label: string
  icon: LucideIcon
  roles: Role[]
  /** Shown in the mobile bottom nav (kept short - max ~5 items fit comfortably). */
  primary?: boolean
  /** Baseline ESS item shown even when a token's roles couldn't be parsed at all (see navEntriesForRoles). */
  essDefault?: boolean
  /** Shown only when the signed-in user's own EmploymentCategory (useMyEmploymentCategory) strictly equals this - e.g. e-Service Book, REGULAR only. Unset means no gating. */
  requiresEmploymentCategory?: EmploymentCategory
}

/**
 * A collapsible sidebar section (UAT issue #3 - "sidebar hierarchy cleanup",
 * replacing ~15 flat HR/admin links with grouped PIMS / ALMS sections).
 * `to`, if set, makes the group header itself a link (e.g. to that module's
 * own dashboard) in addition to the expand/collapse toggle - only
 * "PIMS Workspace" uses this, since it has a real landing page at /pims.
 */
export interface NavGroup {
  label: string
  icon: LucideIcon
  roles: Role[]
  to?: string
  children: NavItem[]
}

export type NavEntry = NavItem | NavGroup

export function isNavGroup(entry: NavEntry): entry is NavGroup {
  return 'children' in entry
}

const ALL_STAFF: Role[] = ['EMPLOYEE', 'HR_ADMIN', 'FINANCE_ADMIN', 'CPF_ADMIN', 'COOP_ADMIN', 'SUPER_ADMIN']
const HR_ADMIN: Role[] = ['HR_ADMIN', 'SUPER_ADMIN']
const FINANCE_ADMIN: Role[] = ['FINANCE_ADMIN', 'SUPER_ADMIN']
const ALMS_REPORT_ROLES: Role[] = ['HR_ADMIN', 'FINANCE_ADMIN', 'SUPER_ADMIN']
const SUPER_ADMIN: Role[] = ['SUPER_ADMIN']
/** Matches PayrollMasterController's own @PreAuthorize exactly - no SUPER_ADMIN bypass there, so none here either. */
const PAYROLL_MASTER_ROLES: Role[] = ['HR_ADMIN', 'BILL_SUPERVISOR', 'FINANCE_ADMIN']
/** Matches CpfTrustController/CpfLoanController's own @PreAuthorize exactly. */
const CPF_TRUST_ROLES: Role[] = ['FINANCE_ADMIN', 'CPF_ADMIN', 'SUPER_ADMIN']
/** Matches JciEccsLoanController/JciEccsMemberController/JciEccsPayrollBatchController's own @PreAuthorize exactly. */
const JCIECCS_ROLES: Role[] = ['COOP_ADMIN', 'FINANCE_ADMIN', 'SUPER_ADMIN']
/** Matches JciEccsMigrationController's own @PreAuthorize exactly (narrower than JCIECCS_ROLES - no FINANCE_ADMIN). */
const JCIECCS_MIGRATION_ROLES: Role[] = ['COOP_ADMIN', 'SUPER_ADMIN']
/** Separation/no-dues clearance is an HR-owned process as much as a co-op one - widened beyond JCIECCS_ROLES. Backed by JciEccsSettlementController, which itself only accepts JCIECCS_ROLES - HR_ADMIN can view/reach the page but the underlying API calls still enforce the narrower set. */
const JCIECCS_SETTLEMENT_ROLES: Role[] = ['HR_ADMIN', 'COOP_ADMIN', 'FINANCE_ADMIN', 'SUPER_ADMIN']

/**
 * The full sidebar, top to bottom: flat ESS self-service items first
 * (unaffected by the regrouping below - primary/essDefault only ever apply
 * here, so the mobile bottom nav and the no-roles-parsed fallback are both
 * untouched), then the regrouped HR/PIMS/ALMS admin section, then Finance
 * and Super Admin.
 *
 * Routes are this app's real registered routes (see App.tsx). The UAT
 * ticket's own shorthand paths (/directory, /onboarding, /leave,
 * /admin/sanctioned-posts, /leave/sanctions, /regularization-approvals,
 * /admin/master-data, /admin/apar-cycles) don't exist in this app and were
 * not created as new dead routes - the real, already-registered routes for
 * the same pages were reused instead.
 *
 * Three previously-flat links (Leave Types & Cadre Eligibility, Holiday &
 * RH Calendars) were removed entirely from this sidebar per the UAT ticket
 * and are now reachable only as tiles inside the Master Data Console (see
 * MasterDataConsolePage.tsx); Functional & Statutory Roles is both a
 * PIMS Workspace sub-item here and a console tile there, satisfying "no
 * duplicate top-level flat link" (its only sidebar appearance is nested,
 * not flat) while staying discoverable from the console too.
 */
export const NAV_ENTRIES: NavEntry[] = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard, roles: ALL_STAFF, primary: true, essDefault: true },
  { to: '/attendance', label: 'Attendance & Facial Punch', icon: Camera, roles: ALL_STAFF, primary: true, essDefault: true },
  { to: '/leaves', label: 'Leave Management', icon: CalendarCheck, roles: ALL_STAFF, primary: true, essDefault: true },
  { to: '/payslips', label: 'My Payslips', icon: Wallet, roles: ALL_STAFF, primary: true, essDefault: true },
  { to: '/ess/salary-slips', label: 'Salary Slip History', icon: ScrollText, roles: ALL_STAFF, essDefault: true },
  { to: '/service-book', label: 'e-Service Book & Career Timeline', icon: History, roles: ALL_STAFF, essDefault: true, requiresEmploymentCategory: 'REGULAR' },
  { to: '/my-transfers', label: 'My Transfers & Promotions', icon: MoveRight, roles: ALL_STAFF, essDefault: true },
  { to: '/apar', label: 'APAR Evaluation', icon: ClipboardCheck, roles: ALL_STAFF, essDefault: true },
  { to: '/loans', label: 'Loans & Advances', icon: FileText, roles: ALL_STAFF, essDefault: true },
  { to: '/self-service/leave/encashment', label: 'EL Encashment', icon: CircleDollarSign, roles: ALL_STAFF, essDefault: true },
  { to: '/self-service/cea-claims', label: 'CEA / Hostel Subsidy', icon: GraduationCap, roles: ALL_STAFF, essDefault: true },

  { to: '/profile', label: 'Profile', icon: User, roles: ALL_STAFF, primary: true, essDefault: true },
  { to: '/pf-statement', label: 'CPF Passbook', icon: Coins, roles: ALL_STAFF },
  // Task 4 - member-facing, read-only eligibility/ceiling/repayment simulator plus the "Apply for this
  // Loan" handoff into ApplyCpfLoanModal (lockedEmployee = self). Deliberately a flat ALL_STAFF entry here
  // rather than nested under the "CPF Trust" admin group below (whose roles gate every sibling item to
  // CPF_TRUST_ROLES) - CPF_TRUST_ROLES admins can still reach it directly by URL, same as any ALL_STAFF page.
  { to: '/ess/cpf/loan-simulator', label: 'CPF Loans & Advances Simulator', icon: HandCoins, roles: ALL_STAFF },
  { to: '/form16', label: 'Form-16', icon: ReceiptText, roles: ALL_STAFF },

  {
    label: 'PIMS Workspace',
    icon: LayoutGrid,
    roles: HR_ADMIN,
    to: '/pims',
    children: [
      { to: '/admin/posts', label: 'Sanctioned Post Master', icon: ClipboardCheck, roles: HR_ADMIN },
      { to: '/admin/master/functional-roles', label: 'Functional & Statutory Roles', icon: ShieldCheck, roles: HR_ADMIN },
      { to: '/admin/pims/movements', label: 'Transfers, Promotions & Joining Reports', icon: MoveRight, roles: HR_ADMIN },
      { to: '/employees', label: 'Employee Directory & Onboarding', icon: Users, roles: HR_ADMIN },
      { to: '/onboarding/drafts', label: 'Onboarding Drafts', icon: UploadCloud, roles: HR_ADMIN },
      { to: '/apar-cycles', label: 'APAR Cycles', icon: ClipboardCheck, roles: HR_ADMIN },
      { to: '/reports/pims', label: 'PIMS Reporting Hub', icon: BarChart3, roles: HR_ADMIN },
    ],
  },
  {
    label: 'Attendance & Shifts',
    icon: Camera,
    roles: HR_ADMIN,
    children: [
      { to: '/attendance', label: 'Facial & Web Punch', icon: Camera, roles: HR_ADMIN },
      { to: '/attendance/regularization-approvals', label: 'Regularization Approvals', icon: Wrench, roles: HR_ADMIN },
      { to: '/admin/attendance/devices', label: 'Device Management & Approvals', icon: Wrench, roles: HR_ADMIN },
      { to: '/admin/attendance/rosters', label: 'Duty Rosters & Shifts', icon: CalendarClock, roles: HR_ADMIN },
    ],
  },
  {
    label: 'Leave Management',
    icon: CalendarCheck,
    roles: HR_ADMIN,
    children: [
      { to: '/leaves', label: 'Apply & My Leaves', icon: CalendarCheck, roles: HR_ADMIN },
      { to: '/leave-approvals', label: 'Leave Sanctions', icon: CalendarCheck, roles: HR_ADMIN },
      { to: '/self-service/leave/encashment', label: 'In-Service EL Encashment', icon: CircleDollarSign, roles: HR_ADMIN },
      { to: '/admin/leave/baseline-takeon', label: 'Leave Baseline Take-On', icon: History, roles: HR_ADMIN },
    ],
  },
  { to: '/admin/reports/alms', label: 'ALMS Reports & Muster', icon: BarChart3, roles: ALMS_REPORT_ROLES },
  // Widened from SUPER_ADMIN-only: every master this console edits (departments, pay
  // scales, leave types, holidays, functional roles, ...) already accepts HR_ADMIN at
  // the API level (each controller's own @PreAuthorize) - the old SUPER_ADMIN-only
  // route guard was stricter than the backend and would have made the Leave Types/
  // Holiday/Functional Roles tiles below unreachable for HR_ADMIN once their old
  // standalone links were removed.
  { to: '/admin/masters', label: 'Master Data Console', icon: Building2, roles: HR_ADMIN },

  { to: '/migration', label: 'Legacy Migration Workbench', icon: UploadCloud, roles: HR_ADMIN },
  { to: '/disciplinary', label: 'Disciplinary Cases', icon: Gavel, roles: HR_ADMIN },

  { to: '/payroll', label: 'Payroll & Remittance Processing', icon: ScrollText, roles: FINANCE_ADMIN },
  {
    label: 'Payroll Masters',
    icon: Banknote,
    roles: PAYROLL_MASTER_ROLES,
    to: '/admin/payroll/masters',
    children: [
      { to: '/admin/payroll/masters', label: 'Master Console', icon: Banknote, roles: PAYROLL_MASTER_ROLES },
      // Same role set as the pre-existing flat /da-rates link this replaces - DaRateHistoryController's
      // own @PreAuthorize doesn't grant BILL_SUPERVISOR, so this stays FINANCE_ADMIN-only rather than
      // the wider PAYROLL_MASTER_ROLES used by its Payroll Masters siblings.
      { to: '/admin/payroll/masters/da-rates', label: 'DA Rate Manager', icon: TrendingUp, roles: FINANCE_ADMIN },
      { to: '/hr/nps-declarations', label: 'NPS Declaration Desk', icon: FileSignature, roles: PAYROLL_MASTER_ROLES },
      { to: '/admin/payroll/nps-declarations', label: 'NPS Compliance Dashboard', icon: ClipboardCheck, roles: PAYROLL_MASTER_ROLES },
    ],
  },
  {
    label: 'CPF Trust',
    icon: Landmark,
    roles: CPF_TRUST_ROLES,
    children: [
      { to: '/payroll/trust/members', label: 'CPF Trust Members', icon: Landmark, roles: CPF_TRUST_ROLES },
      { to: '/payroll/trust/passbook', label: 'CPF Passbook', icon: Landmark, roles: CPF_TRUST_ROLES },
      { to: '/payroll/trust/interest-rates', label: 'CPF Rate of Interest Entry', icon: Landmark, roles: CPF_TRUST_ROLES },
      { to: '/payroll/trust/interest-management', label: 'CPF Interest Management', icon: Landmark, roles: CPF_TRUST_ROLES },
      { to: '/payroll/trust/withdrawal-rules', label: 'CPF Withdrawal Rule Engine', icon: Landmark, roles: CPF_TRUST_ROLES },
      { to: '/payroll/trust/incoming-transfers', label: 'Incoming Fund Transfers', icon: Landmark, roles: CPF_TRUST_ROLES },
      { to: '/payroll/trust/loans', label: 'CPF Loans & Advances', icon: HandCoins, roles: CPF_TRUST_ROLES },
      { to: '/admin/cpf/disputes', label: 'CPF Transaction Disputes', icon: Gavel, roles: CPF_TRUST_ROLES },
    ],
  },

  // Co-operative (JCIECCS) - four sibling groups rather than one 3-level "Co-operative" parent with
  // nested sub-categories, since NavGroup only supports one level of children (group -> flat items,
  // same constraint CPF Trust/Payroll Masters/PIMS Workspace are all built around above) - splitting by
  // function (Members & Thrift / Loan Servicing / Payroll & Recovery / Settlement & Clearance) mirrors
  // how e.g. "Attendance & Shifts" and "Leave Management" stay separate groups rather than nesting under
  // one artificial "HR Admin" parent.
  { to: '/jcieccs', label: 'Co-op: Overview', icon: LayoutDashboard, roles: JCIECCS_ROLES },
  {
    label: 'Co-op: Members & Thrift',
    icon: Landmark,
    roles: JCIECCS_ROLES,
    children: [
      { to: '/jcieccs/members/directory', label: 'Member Directory', icon: Users, roles: JCIECCS_ROLES },
      { to: '/jcieccs/members/thrift-ledger', label: 'Thrift Fund Ledger', icon: PiggyBank, roles: JCIECCS_ROLES },
      { to: '/jcieccs/members/staging', label: 'Migration / Staging', icon: UploadCloud, roles: JCIECCS_MIGRATION_ROLES },
    ],
  },
  {
    label: 'Co-op: Loan Servicing',
    icon: HandCoins,
    roles: JCIECCS_ROLES,
    children: [
      { to: '/jcieccs/loans/active', label: 'Active Loans', icon: ListChecks, roles: JCIECCS_ROLES },
      { to: '/jcieccs/loans/apply', label: 'New Loan Application & Disbursal', icon: FileSignature, roles: JCIECCS_ROLES },
      { to: '/jcieccs/loans/restructure', label: 'Restructure & Top-up', icon: TrendingUp, roles: JCIECCS_ROLES },
      { to: '/jcieccs/loans/cash-repayment', label: 'Cash Repayments', icon: Banknote, roles: JCIECCS_ROLES },
    ],
  },
  {
    label: 'Co-op: Payroll & Recovery',
    icon: ScrollText,
    roles: JCIECCS_ROLES,
    children: [
      { to: '/jcieccs/payroll-recovery/demands', label: 'Monthly Demand Schedules', icon: ClipboardCheck, roles: JCIECCS_ROLES },
      { to: '/jcieccs/payroll-recovery/reconciliation', label: 'Deduction Reconciliation', icon: BarChart3, roles: JCIECCS_ROLES },
      { to: '/jcieccs/payroll-recovery/audit-trail', label: 'Priority Cascade Audit Trail', icon: History, roles: JCIECCS_ROLES },
      { to: '/jcieccs/payroll-recovery/integrity-check', label: 'Integrity Checker', icon: ShieldAlert, roles: JCIECCS_ROLES },
    ],
  },
  {
    label: 'Co-op: Settlement & Clearance',
    icon: Scale,
    roles: JCIECCS_SETTLEMENT_ROLES,
    children: [
      { to: '/jcieccs/settlement/no-dues', label: 'Separation Clearance', icon: Scale, roles: JCIECCS_SETTLEMENT_ROLES },
    ],
  },

  { to: '/audit-logs', label: 'Audit Trail Logs', icon: ShieldCheck, roles: SUPER_ADMIN },
]

function flattenLeaves(entries: NavEntry[]): NavItem[] {
  return entries.flatMap((entry) => (isNavGroup(entry) ? entry.children : [entry]))
}

function passesEmploymentCategoryGate(item: NavItem, employmentCategory: EmploymentCategory | null): boolean {
  return item.requiresEmploymentCategory === undefined || item.requiresEmploymentCategory === employmentCategory
}

/**
 * Role-filtered, group-aware sidebar tree - a group with zero visible children after filtering is
 * dropped entirely. `employmentCategory` (from useMyEmploymentCategory - defaults null, i.e. "not
 * yet known/not REGULAR") additionally strictly gates any item carrying requiresEmploymentCategory
 * (currently just e-Service Book & Career Timeline).
 */
export function navEntriesForRoles(roles: Role[], employmentCategory: EmploymentCategory | null = null): NavEntry[] {
  if (roles.length === 0) {
    // Authenticated but the token's roles couldn't be resolved to anything
    // known (see lib/jwt.ts) - fall back to the baseline ESS set instead of
    // rendering an empty sidebar. None of the grouped admin entries carry
    // essDefault, so this is always just the flat ESS items.
    return flattenLeaves(NAV_ENTRIES).filter((item) => item.essDefault && passesEmploymentCategoryGate(item, employmentCategory))
  }
  return NAV_ENTRIES.map((entry) => {
    if (isNavGroup(entry)) {
      if (!entry.roles.some((r) => roles.includes(r))) return null
      const children = entry.children.filter((child) => child.roles.some((r) => roles.includes(r)) && passesEmploymentCategoryGate(child, employmentCategory))
      return children.length > 0 ? { ...entry, children } : null
    }
    return entry.roles.some((r) => roles.includes(r)) && passesEmploymentCategoryGate(entry, employmentCategory) ? entry : null
  }).filter((entry): entry is NavEntry => entry !== null)
}

/** Mobile bottom nav's fixed 5-item set - flattened so a primary item would still surface even if it ever lived inside a group (none currently do). */
export function primaryNavItemsForRoles(roles: Role[], employmentCategory: EmploymentCategory | null = null): NavItem[] {
  return flatNavItemsForRoles(roles, employmentCategory).filter((item) => item.primary)
}

/** Every leaf link visible to `roles`, groups flattened - for consumers (e.g. DashboardPage's quick-action tiles) that just want a flat clickable list, not the sidebar's grouped presentation. */
export function flatNavItemsForRoles(roles: Role[], employmentCategory: EmploymentCategory | null = null): NavItem[] {
  return flattenLeaves(navEntriesForRoles(roles, employmentCategory))
}
