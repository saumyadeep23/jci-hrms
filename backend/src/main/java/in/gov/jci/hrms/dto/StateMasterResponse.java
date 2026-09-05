package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.StateMaster;
import in.gov.jci.hrms.entity.StateType;

import java.time.Instant;
import java.util.UUID;

public record StateMasterResponse(
        UUID id,
        String stateCode,
        String stateName,
        StateType stateType,
        boolean active,
        Instant createdAt
) {
    public static StateMasterResponse from(StateMaster state) {
        return new StateMasterResponse(
                state.getId(),
                state.getStateCode(),
                state.getStateName(),
                state.getStateType(),
                state.isActive(),
                state.getCreatedAt()
        );
    }
}
