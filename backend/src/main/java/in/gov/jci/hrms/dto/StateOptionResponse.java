package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.StateMaster;

/** Read-only, any-authenticated-user projection of StateMaster for dropdown population - narrower than StateMasterResponse (which is SUPER_ADMIN-only CRUD's own DTO, /api/v1/admin/masters/states). */
public record StateOptionResponse(
        String stateCode,
        String stateName
) {
    public static StateOptionResponse from(StateMaster state) {
        return new StateOptionResponse(state.getStateCode(), state.getStateName());
    }
}
