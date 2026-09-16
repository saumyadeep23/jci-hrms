package in.gov.jci.hrms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.jci.hrms.dto.StateMasterRequest;
import in.gov.jci.hrms.dto.StateMasterResponse;
import in.gov.jci.hrms.entity.StateType;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataInUseException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.security.RbacSecurity;
import in.gov.jci.hrms.security.SecurityConfig;
import in.gov.jci.hrms.service.StateMasterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Final RBAC business-authority closure (docs/security/RBAC_MIGRATION_REPORT.md): State master
 * maintenance is now gated by the ESTABLISHMENT_VIEW/ESTABLISHMENT_MAINTAIN permissions (HR_ADMIN_EST's
 * grants, V97), not any role name - tests exercise the permission via a mocked RbacSecurity bean
 * rather than a role claim, since role membership no longer determines access here.
 */
@WebMvcTest(StateMasterController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "USER")
class StateMasterControllerTest {

    private static final UUID STATE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private StateMasterService stateMasterService;

    @MockBean(name = "rbac")
    private RbacSecurity rbac;

    private StateMasterRequest validRequest() {
        return new StateMasterRequest("WB", "West Bengal", StateType.STATE, true, false, BigDecimal.ZERO);
    }

    private StateMasterResponse responseFor(UUID id, StateMasterRequest request) {
        Instant now = Instant.now();
        return new StateMasterResponse(id, request.stateCode(), request.stateName(), request.stateType(), request.active(),
                request.isRemoteArea(), request.remoteAllowancePercentage(), now, now);
    }

    private void grantMaintain() {
        when(rbac.hasPermission(any(), eq("ESTABLISHMENT_MAINTAIN"))).thenReturn(true);
    }

    private void grantView() {
        when(rbac.hasPermission(any(), eq("ESTABLISHMENT_VIEW"))).thenReturn(true);
    }

    @Test
    void create_withEstablishmentMaintainPermission_returns201() throws Exception {
        grantMaintain();
        StateMasterRequest request = validRequest();
        when(stateMasterService.create(any(StateMasterRequest.class))).thenReturn(responseFor(STATE_ID, request));

        mockMvc.perform(post("/api/v1/admin/masters/states")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/admin/masters/states/" + STATE_ID))
                .andExpect(jsonPath("$.stateName").value("West Bengal"));
    }

    @Test
    void create_whenCodeAlreadyTaken_returns409() throws Exception {
        grantMaintain();
        when(stateMasterService.create(any(StateMasterRequest.class)))
                .thenThrow(new MasterDataConflictException("State code already in use: WB"));

        mockMvc.perform(post("/api/v1/admin/masters/states")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    void create_withoutEstablishmentMaintainPermission_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/masters/states")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }

    // SYSTEM_ADMIN must not be implicitly permitted merely by holding that role - only an explicit
    // ESTABLISHMENT_MAINTAIN grant matters, which this test deliberately withholds.
    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void create_asSystemAdminWithoutExplicitPermission_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/masters/states")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void create_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/admin/masters/states")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getById_whenMissing_returns404() throws Exception {
        grantView();
        UUID missingId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        when(stateMasterService.getById(missingId)).thenThrow(new MasterDataNotFoundException("State", missingId));

        mockMvc.perform(get("/api/v1/admin/masters/states/" + missingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void getById_returns200() throws Exception {
        grantView();
        when(stateMasterService.getById(STATE_ID)).thenReturn(responseFor(STATE_ID, validRequest()));

        mockMvc.perform(get("/api/v1/admin/masters/states/" + STATE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stateCode").value("WB"));
    }

    @Test
    void getById_withoutEstablishmentViewPermission_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/masters/states/" + STATE_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_whenInUse_returns409() throws Exception {
        grantMaintain();
        org.mockito.Mockito.doThrow(new MasterDataInUseException("State", STATE_ID)).when(stateMasterService).delete(STATE_ID);

        mockMvc.perform(delete("/api/v1/admin/masters/states/" + STATE_ID))
                .andExpect(status().isConflict());
    }

    @Test
    void delete_returns204() throws Exception {
        grantMaintain();
        mockMvc.perform(delete("/api/v1/admin/masters/states/" + STATE_ID))
                .andExpect(status().isNoContent());
    }
}
