package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.NpsAdminSummaryResponse;
import in.gov.jci.hrms.dto.NpsDeclarationRequest;
import in.gov.jci.hrms.dto.NpsDeclarationResponse;
import in.gov.jci.hrms.dto.NpsPreviewResponse;

import java.util.List;

/** Employee NPS Declaration Desk - a lock-per-financial-year rule that a plain CRUD master doesn't need, hence its own service alongside PayrollMasterService. */
public interface NpsDeclarationService {

    /**
     * Resolves the current April-March financial year and records the declaration for it. Throws
     * BusinessRuleViolationException if the employee already has a declaration for that FY - the
     * declaration percentage can only be set once per financial year.
     */
    NpsDeclarationResponse recordDeclaration(NpsDeclarationRequest request);

    List<NpsDeclarationResponse> listAll();

    List<NpsDeclarationResponse> listByEmployee(Long employeeId);

    /** Current Basic Pay/DA snapshot, current-FY declaration status, and full declaration history for one employee. */
    NpsPreviewResponse preview(Long employeeId);

    /** Submitted-vs-pending datasets for the HR/Bill Section admin dashboard, for one financial year. */
    NpsAdminSummaryResponse adminSummary(String financialYear);
}
