import { Navigate, Route, Routes } from 'react-router-dom'
import { AppShell } from './components/layout/AppShell'
import { ProtectedRoute } from './components/common/ProtectedRoute'
import type { Role } from './types/auth'
import { LoginPage } from './pages/auth/LoginPage'
import { DashboardPage } from './pages/DashboardPage'
import { AttendancePunchPage } from './pages/attendance/AttendancePunchPage'
import { PayslipViewerPage } from './pages/ess/PayslipViewerPage'
import { PfStatementPage } from './pages/ess/PfStatementPage'
import { Form16Page } from './pages/ess/Form16Page'
import { ProfilePage } from './pages/ess/ProfilePage'
import { ServiceBookPage } from './pages/ess/ServiceBookPage'
import { MyTransfersPage } from './pages/ess/MyTransfersPage'
import { LeavePage } from './pages/ess/LeavePage'
import { LoansPage } from './pages/ess/LoansPage'
import { AparSelfAppraisalPage } from './pages/ess/AparSelfAppraisalPage'
import { EmployeeDirectoryPage } from './pages/hr/EmployeeDirectoryPage'
import { LeaveSanctionQueuePage } from './pages/hr/LeaveSanctionQueuePage'
import { RegularizationApprovalQueuePage } from './pages/hr/RegularizationApprovalQueuePage'
import { LeaveEncashmentPage } from './pages/ess/LeaveEncashmentPage'
import { LeaveBaselineTakeOnPage } from './pages/admin/LeaveBaselineTakeOnPage'
import { AparCycleManagementPage } from './pages/hr/AparCycleManagementPage'
import { LegacyMigrationPage } from './pages/hr/LegacyMigrationPage'
import { DisciplinaryTrackerPage } from './pages/hr/DisciplinaryTrackerPage'
import { DaRateHistoryPage } from './pages/finance/DaRateHistoryPage'
import { PayrollRunsPage } from './pages/finance/PayrollRunsPage'
import { AuditLogViewerPage } from './pages/admin/AuditLogViewerPage'
import { StateMasterPage } from './pages/admin/StateMasterPage'
import { DistrictMasterPage } from './pages/admin/DistrictMasterPage'
import { RoMasterPage } from './pages/admin/RoMasterPage'
import { DpcMasterPage } from './pages/admin/DpcMasterPage'
import { MasterDataConsolePage } from './pages/admin/MasterDataConsolePage'
import { PostMasterAdminPage } from './pages/admin/PostMasterAdminPage'
import { OnboardingDraftsPage } from './pages/hr/OnboardingDraftsPage'
import { OnboardingWizardPage } from './pages/hr/onboarding/OnboardingWizardPage'
import { PimsDashboardPage } from './pages/pims/PimsDashboardPage'
import { PimsReportsHubPage } from './pages/reports/PimsReportsHubPage'
import { AlmsReportsCenterPage } from './pages/admin/reports/AlmsReportsCenterPage'
import { LeaveTypeMasterPage } from './pages/admin/master/LeaveTypeMasterPage'
import { DeviceMasterPage } from './pages/admin/attendance/DeviceMasterPage'
import { DutyRosterPage } from './pages/admin/attendance/DutyRosterPage'
import { HolidayMasterPage } from './pages/admin/master/HolidayMasterPage'
import { FunctionalRolesMasterPage } from './pages/admin/master/FunctionalRolesMasterPage'
import { MovementManagementPage } from './pages/admin/pims/MovementManagementPage'

const HR_ROLES: Role[] = ['HR_ADMIN', 'SUPER_ADMIN']
const FINANCE_ROLES: Role[] = ['FINANCE_ADMIN', 'SUPER_ADMIN']
const SUPER_ADMIN_ROLES: Role[] = ['SUPER_ADMIN']
const ALMS_REPORT_ROLES: Role[] = ['HR_ADMIN', 'FINANCE_ADMIN', 'SUPER_ADMIN']

function Protected({ children, roles }: { children: React.ReactNode; roles?: Role[] }) {
  return (
    <ProtectedRoute roles={roles}>
      <AppShell>{children}</AppShell>
    </ProtectedRoute>
  )
}

function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />

      <Route path="/" element={<Protected><DashboardPage /></Protected>} />
      <Route path="/attendance" element={<Protected><AttendancePunchPage /></Protected>} />

      <Route path="/payslips" element={<Protected><PayslipViewerPage /></Protected>} />
      <Route path="/pf-statement" element={<Protected><PfStatementPage /></Protected>} />
      <Route path="/form16" element={<Protected><Form16Page /></Protected>} />
      <Route path="/profile" element={<Protected><ProfilePage /></Protected>} />
      <Route path="/service-book" element={<Protected><ServiceBookPage /></Protected>} />
      <Route path="/my-transfers" element={<Protected><MyTransfersPage /></Protected>} />
      <Route path="/leaves" element={<Protected><LeavePage /></Protected>} />
      <Route path="/self-service/leave/encashment" element={<Protected><LeaveEncashmentPage /></Protected>} />
      <Route path="/loans" element={<Protected><LoansPage /></Protected>} />
      <Route path="/apar" element={<Protected><AparSelfAppraisalPage /></Protected>} />

      <Route path="/pims" element={<Protected roles={HR_ROLES}><PimsDashboardPage /></Protected>} />
      <Route path="/reports/pims" element={<Protected roles={HR_ROLES}><PimsReportsHubPage /></Protected>} />
      <Route path="/employees" element={<Protected roles={HR_ROLES}><EmployeeDirectoryPage /></Protected>} />
      <Route path="/onboarding/drafts" element={<Protected roles={HR_ROLES}><OnboardingDraftsPage /></Protected>} />
      <Route path="/onboarding/new" element={<Protected roles={HR_ROLES}><OnboardingWizardPage /></Protected>} />
      <Route path="/leave-approvals" element={<Protected roles={HR_ROLES}><LeaveSanctionQueuePage /></Protected>} />
      <Route path="/attendance/regularization-approvals" element={<Protected roles={HR_ROLES}><RegularizationApprovalQueuePage /></Protected>} />
      <Route path="/admin/leave/baseline-takeon" element={<Protected roles={HR_ROLES}><LeaveBaselineTakeOnPage /></Protected>} />
      <Route path="/admin/reports/alms" element={<Protected roles={ALMS_REPORT_ROLES}><AlmsReportsCenterPage /></Protected>} />
      <Route path="/admin/master/leave-types" element={<Protected roles={HR_ROLES}><LeaveTypeMasterPage /></Protected>} />
      <Route path="/admin/attendance/devices" element={<Protected roles={HR_ROLES}><DeviceMasterPage /></Protected>} />
      <Route path="/admin/attendance/rosters" element={<Protected roles={HR_ROLES}><DutyRosterPage /></Protected>} />
      <Route path="/admin/master/holidays" element={<Protected roles={HR_ROLES}><HolidayMasterPage /></Protected>} />
      <Route path="/admin/master/functional-roles" element={<Protected roles={HR_ROLES}><FunctionalRolesMasterPage /></Protected>} />
      <Route path="/admin/pims/movements" element={<Protected roles={HR_ROLES}><MovementManagementPage /></Protected>} />
      <Route path="/apar-cycles" element={<Protected roles={HR_ROLES}><AparCycleManagementPage /></Protected>} />
      <Route path="/migration" element={<Protected roles={HR_ROLES}><LegacyMigrationPage /></Protected>} />
      <Route path="/disciplinary" element={<Protected roles={HR_ROLES}><DisciplinaryTrackerPage /></Protected>} />

      <Route path="/da-rates" element={<Protected roles={FINANCE_ROLES}><DaRateHistoryPage /></Protected>} />
      <Route path="/payroll" element={<Protected roles={FINANCE_ROLES}><PayrollRunsPage /></Protected>} />

      <Route path="/audit-logs" element={<Protected roles={SUPER_ADMIN_ROLES}><AuditLogViewerPage /></Protected>} />
      {/* Widened from SUPER_ADMIN-only: PostMasterController and every master this console edits (besides states/districts, gated within the page itself) already accept HR_ADMIN at the API level - see nav-config.ts's NAV_ENTRIES comment. */}
      <Route path="/admin/masters" element={<Protected roles={HR_ROLES}><MasterDataConsolePage /></Protected>} />
      <Route path="/admin/posts" element={<Protected roles={HR_ROLES}><PostMasterAdminPage /></Protected>} />
      <Route path="/admin/posts/new" element={<Protected roles={HR_ROLES}><PostMasterAdminPage /></Protected>} />
      <Route path="/admin/states" element={<Protected roles={SUPER_ADMIN_ROLES}><StateMasterPage /></Protected>} />
      <Route path="/admin/districts" element={<Protected roles={SUPER_ADMIN_ROLES}><DistrictMasterPage /></Protected>} />
      <Route path="/admin/regional-offices" element={<Protected roles={SUPER_ADMIN_ROLES}><RoMasterPage /></Protected>} />
      <Route path="/admin/dpcs" element={<Protected roles={SUPER_ADMIN_ROLES}><DpcMasterPage /></Protected>} />

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default App
