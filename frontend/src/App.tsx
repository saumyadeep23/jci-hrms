import { Navigate, Route, Routes } from 'react-router-dom'
import { AppShell } from './components/layout/AppShell'
import { ProtectedRoute } from './components/common/ProtectedRoute'
import type { Role } from './types/auth'
import { LoginPage } from './pages/auth/LoginPage'
import { DashboardPage } from './pages/DashboardPage'
import { AttendancePunchPage } from './pages/attendance/AttendancePunchPage'
import { PayslipViewerPage } from './pages/ess/PayslipViewerPage'
import { SalarySlipsPage } from './pages/ess/SalarySlipsPage'
import { PfStatementPage } from './pages/ess/PfStatementPage'
import { CpfLoanSimulatorPage } from './pages/ess/CpfLoanSimulatorPage'
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
import { CeaClaimsPage } from './pages/ess/CeaClaimsPage'
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
import { PayrollMastersPage } from './pages/admin/master/payroll/PayrollMastersPage'
import { NpsDeclarationDeskPage } from './pages/hr/NpsDeclarationDeskPage'
import { NpsComplianceDashboardPage } from './pages/admin/payroll/NpsComplianceDashboardPage'
import { IncomingTransfersPage } from './pages/payroll/trust/IncomingTransfersPage'
import { CpfPassbookView } from './pages/payroll/trust/CpfPassbookView'
import { CpfInterestRateEntryPage } from './pages/payroll/trust/CpfInterestRateEntryPage'
import { CpfInterestManagementPage } from './pages/payroll/trust/CpfInterestManagementPage'
import { CpfWithdrawalRulesPage } from './pages/payroll/trust/CpfWithdrawalRulesPage'
import { CpfMembersListPage } from './pages/payroll/trust/CpfMembersListPage'
import { CpfLoansPage } from './pages/payroll/trust/loans/CpfLoansPage'
import { CpfDisputeAdminPage } from './pages/admin/cpf/CpfDisputeAdminPage'
import { JciEccsDashboardPage } from './pages/coop/jcieccs/JciEccsDashboardPage'
import { MemberDirectoryPage } from './pages/coop/jcieccs/MemberDirectoryPage'
import { ThriftLedgerPage } from './pages/coop/jcieccs/ThriftLedgerPage'
import { MemberStagingPage } from './pages/coop/jcieccs/MemberStagingPage'
import { ActiveLoansPage } from './pages/coop/jcieccs/ActiveLoansPage'
import { ApplyLoanPage } from './pages/coop/jcieccs/ApplyLoanPage'
import { RestructureTopUpPage } from './pages/coop/jcieccs/RestructureTopUpPage'
import { CashRepaymentPage } from './pages/coop/jcieccs/CashRepaymentPage'
import { PayrollDemandsPage } from './pages/coop/jcieccs/PayrollDemandsPage'
import { DeductionReconciliationPage } from './pages/coop/jcieccs/DeductionReconciliationPage'
import { AuditTrailPage } from './pages/coop/jcieccs/AuditTrailPage'
import { IntegrityCheckPage } from './pages/coop/jcieccs/IntegrityCheckPage'
import { SeparationClearancePage } from './pages/coop/jcieccs/SeparationClearancePage'

const HR_ROLES: Role[] = ['HR_ADMIN', 'SUPER_ADMIN']
const FINANCE_ROLES: Role[] = ['FINANCE_ADMIN', 'SUPER_ADMIN']
const SUPER_ADMIN_ROLES: Role[] = ['SUPER_ADMIN']
const ALMS_REPORT_ROLES: Role[] = ['HR_ADMIN', 'FINANCE_ADMIN', 'SUPER_ADMIN']
/** Matches CpfTrustController/CpfLoanController's own @PreAuthorize exactly. */
const CPF_TRUST_ROLES: Role[] = ['FINANCE_ADMIN', 'CPF_ADMIN', 'SUPER_ADMIN']
/** Matches PayrollMasterController's own @PreAuthorize exactly (no SUPER_ADMIN bypass there, so none here either). */
const PAYROLL_MASTER_ROLES: Role[] = ['HR_ADMIN', 'BILL_SUPERVISOR', 'FINANCE_ADMIN']
/** Matches JciEccsLoanController/JciEccsMemberController/JciEccsPayrollBatchController's own @PreAuthorize exactly. */
const JCIECCS_ROLES: Role[] = ['COOP_ADMIN', 'FINANCE_ADMIN', 'SUPER_ADMIN']
/** Matches JciEccsMigrationController's own @PreAuthorize exactly (narrower - no FINANCE_ADMIN). */
const JCIECCS_MIGRATION_ROLES: Role[] = ['COOP_ADMIN', 'SUPER_ADMIN']
/** No backend controller exists yet for separation/no-dues clearance (see SeparationClearancePage) - widened to include HR_ADMIN since this is as much an HR process as a co-op one. */
const JCIECCS_SETTLEMENT_ROLES: Role[] = ['HR_ADMIN', 'COOP_ADMIN', 'FINANCE_ADMIN', 'SUPER_ADMIN']

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
      <Route path="/ess/salary-slips" element={<Protected><SalarySlipsPage /></Protected>} />
      <Route path="/pf-statement" element={<Protected><PfStatementPage /></Protected>} />
      <Route path="/ess/cpf/loan-simulator" element={<Protected><CpfLoanSimulatorPage /></Protected>} />
      <Route path="/form16" element={<Protected><Form16Page /></Protected>} />
      <Route path="/profile" element={<Protected><ProfilePage /></Protected>} />
      <Route path="/service-book" element={<Protected><ServiceBookPage /></Protected>} />
      <Route path="/my-transfers" element={<Protected><MyTransfersPage /></Protected>} />
      <Route path="/leaves" element={<Protected><LeavePage /></Protected>} />
      <Route path="/self-service/leave/encashment" element={<Protected><LeaveEncashmentPage /></Protected>} />
      <Route path="/self-service/cea-claims" element={<Protected><CeaClaimsPage /></Protected>} />
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
      <Route path="/admin/payroll/masters" element={<Protected roles={PAYROLL_MASTER_ROLES}><PayrollMastersPage /></Protected>} />
      {/* HRA Rates is a tab of PayrollMastersPage (?tab= deep-linking, same pattern as MasterDataConsolePage) - this route is just a friendlier bookmarkable alias for it. */}
      <Route path="/admin/payroll/masters/hra-rates" element={<Navigate to="/admin/payroll/masters?tab=hra-rates" replace />} />
      {/* Statutory Parameters is also a tab of PayrollMastersPage - same bookmarkable-alias pattern as HRA Rates above. */}
      <Route path="/admin/payroll/masters/statutory-parameters" element={<Navigate to="/admin/payroll/masters?tab=statutory-parameters" replace />} />
      {/* DA Rate Manager stays the same page/route/role-gate as before (FINANCE_ROLES) - this alias only changes where it's reachable from in the nav tree, not who can reach it. */}
      <Route path="/admin/payroll/masters/da-rates" element={<Navigate to="/da-rates" replace />} />
      <Route path="/hr/nps-declarations" element={<Protected roles={PAYROLL_MASTER_ROLES}><NpsDeclarationDeskPage /></Protected>} />
      <Route path="/admin/payroll/nps-declarations" element={<Protected roles={PAYROLL_MASTER_ROLES}><NpsComplianceDashboardPage /></Protected>} />
      <Route path="/apar-cycles" element={<Protected roles={HR_ROLES}><AparCycleManagementPage /></Protected>} />
      <Route path="/migration" element={<Protected roles={HR_ROLES}><LegacyMigrationPage /></Protected>} />
      <Route path="/disciplinary" element={<Protected roles={HR_ROLES}><DisciplinaryTrackerPage /></Protected>} />

      <Route path="/da-rates" element={<Protected roles={FINANCE_ROLES}><DaRateHistoryPage /></Protected>} />
      <Route path="/payroll" element={<Protected roles={FINANCE_ROLES}><PayrollRunsPage /></Protected>} />
      <Route path="/payroll/trust/members" element={<Protected roles={CPF_TRUST_ROLES}><CpfMembersListPage /></Protected>} />
      <Route path="/payroll/trust/passbook" element={<Protected roles={CPF_TRUST_ROLES}><CpfPassbookView /></Protected>} />
      <Route path="/payroll/trust/interest-rates" element={<Protected roles={CPF_TRUST_ROLES}><CpfInterestRateEntryPage /></Protected>} />
      <Route path="/payroll/trust/interest-management" element={<Protected roles={CPF_TRUST_ROLES}><CpfInterestManagementPage /></Protected>} />
      <Route path="/payroll/trust/withdrawal-rules" element={<Protected roles={CPF_TRUST_ROLES}><CpfWithdrawalRulesPage /></Protected>} />
      <Route path="/payroll/trust/incoming-transfers" element={<Protected roles={CPF_TRUST_ROLES}><IncomingTransfersPage /></Protected>} />
      <Route path="/payroll/trust/loans" element={<Protected roles={CPF_TRUST_ROLES}><CpfLoansPage /></Protected>} />
      <Route path="/admin/cpf/disputes" element={<Protected roles={CPF_TRUST_ROLES}><CpfDisputeAdminPage /></Protected>} />

      {/* Co-operative (JCIECCS) - see nav-config.ts's own comment for why this is four sibling nav
          groups rather than one 3-level "Co-operative" parent; routes are flat here regardless, same
          as every other module in this file. */}
      <Route path="/jcieccs" element={<Protected roles={JCIECCS_ROLES}><JciEccsDashboardPage /></Protected>} />
      <Route path="/jcieccs/members/directory" element={<Protected roles={JCIECCS_ROLES}><MemberDirectoryPage /></Protected>} />
      <Route path="/jcieccs/members/thrift-ledger" element={<Protected roles={JCIECCS_ROLES}><ThriftLedgerPage /></Protected>} />
      <Route path="/jcieccs/members/staging" element={<Protected roles={JCIECCS_MIGRATION_ROLES}><MemberStagingPage /></Protected>} />
      <Route path="/jcieccs/loans/active" element={<Protected roles={JCIECCS_ROLES}><ActiveLoansPage /></Protected>} />
      <Route path="/jcieccs/loans/apply" element={<Protected roles={JCIECCS_ROLES}><ApplyLoanPage /></Protected>} />
      <Route path="/jcieccs/loans/restructure" element={<Protected roles={JCIECCS_ROLES}><RestructureTopUpPage /></Protected>} />
      <Route path="/jcieccs/loans/cash-repayment" element={<Protected roles={JCIECCS_ROLES}><CashRepaymentPage /></Protected>} />
      <Route path="/jcieccs/payroll-recovery/demands" element={<Protected roles={JCIECCS_ROLES}><PayrollDemandsPage /></Protected>} />
      <Route path="/jcieccs/payroll-recovery/reconciliation" element={<Protected roles={JCIECCS_ROLES}><DeductionReconciliationPage /></Protected>} />
      <Route path="/jcieccs/payroll-recovery/audit-trail" element={<Protected roles={JCIECCS_ROLES}><AuditTrailPage /></Protected>} />
      <Route path="/jcieccs/payroll-recovery/integrity-check" element={<Protected roles={JCIECCS_ROLES}><IntegrityCheckPage /></Protected>} />
      <Route path="/jcieccs/settlement/no-dues" element={<Protected roles={JCIECCS_SETTLEMENT_ROLES}><SeparationClearancePage /></Protected>} />

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
