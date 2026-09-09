import { apiClient } from '../../../api/client'
import type {
  DaRateHistoryRequest,
  DaRateHistoryResponse,
  IdaProjectionEmployeeResponse,
  IdaProjectionSimulateRequest,
  IdaProjectionSummaryResponse,
  ScaleType,
} from '../../../types/api'

/**
 * Typed API surface for the CPSE IDA Enhancement / Arrear Projection workflow (Tab 2 of the DA Rate
 * Manager). Kept as one small module (rather than inline apiClient calls in every component, this
 * codebase's usual per-page convention) purely because 4 sibling components share the exact same calls -
 * it deliberately does NOT introduce a project-wide "services/" layer, since no other feature in this
 * app has one.
 *
 * Two corrections from the original spec, both because the real backend (IdaProjectionController) is
 * shaped differently than assumed:
 * - There is no dedicated "/active-rate" endpoint - DaRateHistoryController already exposes exactly this
 *   at GET /da-rates/current?scaleType=&effectiveDate= (Tab 1's own data source), reused here as-is.
 * - There is no "/export-schedule" blob endpoint on the backend - EmployeeArrearScheduleTable exports its
 *   own CSV client-side instead, the same pattern already used by EncashmentApprovalHistoryTab and
 *   CeaClaimsPage's History/Log tab elsewhere in this app.
 * - commitOrder takes no forceRollover flag - IdaProjectionController's commit-order endpoint always
 *   (idempotently) re-runs the Scenario A rollover check itself before committing, so there is nothing to
 *   force. checkRollover is a separate, side-effect-bearing preview call (see RolloverConfirmationModal's
 *   own comment for why "preview" here isn't perfectly undoable) used only to decide whether to show the
 *   confirmation modal before calling commitOrder.
 */
export const daArrearApi = {
  getActiveDaRate: async (scaleType: ScaleType, effectiveDate: string): Promise<DaRateHistoryResponse> =>
    (await apiClient.get<DaRateHistoryResponse>('/da-rates/current', { params: { scaleType, effectiveDate } })).data,

  /** Creates the DPE sanction order row in da_rate_history (Tab 1's own table) - required before commitOrder() can succeed. */
  createSanctionOrder: async (payload: DaRateHistoryRequest): Promise<DaRateHistoryResponse> =>
    (await apiClient.post<DaRateHistoryResponse>('/da-rates', payload)).data,

  runSimulation: async (payload: IdaProjectionSimulateRequest): Promise<IdaProjectionSummaryResponse> =>
    (await apiClient.post<IdaProjectionSummaryResponse>('/v1/payroll/ida-projections/simulate', payload)).data,

  getProjectionBatch: async (id: number): Promise<IdaProjectionSummaryResponse> =>
    (await apiClient.get<IdaProjectionSummaryResponse>(`/v1/payroll/ida-projections/${id}`)).data,

  getProjectionEmployees: async (id: number): Promise<IdaProjectionEmployeeResponse[]> =>
    (await apiClient.get<IdaProjectionEmployeeResponse[]>(`/v1/payroll/ida-projections/${id}/employees`)).data,

  /** Scenario A cutoff/rollover preview - see this module's own javadoc for why it isn't a pure, side-effect-free preview. */
  checkRollover: async (id: number): Promise<IdaProjectionSummaryResponse> =>
    (await apiClient.post<IdaProjectionSummaryResponse>(`/v1/payroll/ida-projections/${id}/check-rollover`)).data,

  commitOrder: async (id: number): Promise<IdaProjectionSummaryResponse> =>
    (await apiClient.post<IdaProjectionSummaryResponse>(`/v1/payroll/ida-projections/${id}/commit-order`)).data,
}
