package in.gov.jci.hrms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.jci.hrms.dto.DistrictMasterRequest;
import in.gov.jci.hrms.dto.DistrictMasterResponse;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.security.RbacSecurity;
import in.gov.jci.hrms.security.SecurityConfig;
import in.gov.jci.hrms.service.DistrictMasterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Final RBAC business-authority closure (docs/security/RBAC_MIGRATION_REPORT.md): District master
 * maintenance is now gated by ESTABLISHMENT_VIEW/ESTABLISHMENT_MAINTAIN permissions, not any role
 * name - see StateMasterControllerTest's own javadoc for the same rationale.
 */
@WebMvcTest(DistrictMasterController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "USER")
class DistrictMasterControllerTest {

    private static final UUID STATE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DISTRICT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DistrictMasterService districtMasterService;

    @MockBean(name = "rbac")
    private RbacSecurity rbac;

    private DistrictMasterRequest validRequest() {
        return new DistrictMasterRequest("WB-KOL", "Kolkata", STATE_ID, true);
    }

    private DistrictMasterResponse responseFor(UUID id, DistrictMasterRequest request) {
        Instant now = Instant.now();
        return new DistrictMasterResponse(id, request.districtCode(), request.districtName(),
                request.stateId(), "West Bengal", request.active(), now);
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
        DistrictMasterRequest request = validRequest();
        when(districtMasterService.create(any(DistrictMasterRequest.class))).thenReturn(responseFor(DISTRICT_ID, request));

        mockMvc.perform(post("/api/v1/admin/masters/districts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/admin/masters/districts/" + DISTRICT_ID))
                .andExpect(jsonPath("$.districtName").value("Kolkata"));
    }

    @Test
    void create_whenStateMissing_returns404() throws Exception {
        grantMaintain();
        when(districtMasterService.create(any(DistrictMasterRequest.class)))
                .thenThrow(new MasterDataNotFoundException("State", STATE_ID));

        mockMvc.perform(post("/api/v1/admin/masters/districts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_whenCodeAlreadyTaken_returns409() throws Exception {
        grantMaintain();
        when(districtMasterService.create(any(DistrictMasterRequest.class)))
                .thenThrow(new MasterDataConflictException("District code already in use: WB-KOL"));

        mockMvc.perform(post("/api/v1/admin/masters/districts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isConflict());
    }

    @Test
    void create_withoutEstablishmentMaintainPermission_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/masters/districts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }

    // SYSTEM_ADMIN must not be implicitly permitted merely by holding that role.
    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void create_asSystemAdminWithoutExplicitPermission_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/masters/districts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void list_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/masters/districts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_withoutEstablishmentViewPermission_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/masters/districts"))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_withStateIdFilter_passesItThrough() throws Exception {
        grantView();
        Page<DistrictMasterResponse> page = new PageImpl<>(List.of(responseFor(DISTRICT_ID, validRequest())));
        when(districtMasterService.list(eq(STATE_ID), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/masters/districts").param("stateId", STATE_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].districtCode").value("WB-KOL"));
    }

    @Test
    void list_withoutStateIdFilter_passesNull() throws Exception {
        grantView();
        Page<DistrictMasterResponse> page = new PageImpl<>(List.of(responseFor(DISTRICT_ID, validRequest())));
        when(districtMasterService.list(isNull(), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/masters/districts"))
                .andExpect(status().isOk());
    }
}
