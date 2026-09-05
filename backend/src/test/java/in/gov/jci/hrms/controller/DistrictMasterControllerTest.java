package in.gov.jci.hrms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.jci.hrms.dto.DistrictMasterRequest;
import in.gov.jci.hrms.dto.DistrictMasterResponse;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
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

@WebMvcTest(DistrictMasterController.class)
@Import(SecurityConfig.class)
class DistrictMasterControllerTest {

    private static final UUID STATE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DISTRICT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DistrictMasterService districtMasterService;

    private DistrictMasterRequest validRequest() {
        return new DistrictMasterRequest("WB-KOL", "Kolkata", STATE_ID, true);
    }

    private DistrictMasterResponse responseFor(UUID id, DistrictMasterRequest request) {
        Instant now = Instant.now();
        return new DistrictMasterResponse(id, request.districtCode(), request.districtName(),
                request.stateId(), "West Bengal", request.active(), now);
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void create_withValidRequest_returns201() throws Exception {
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
    @WithMockUser(roles = "SUPER_ADMIN")
    void create_whenStateMissing_returns404() throws Exception {
        when(districtMasterService.create(any(DistrictMasterRequest.class)))
                .thenThrow(new MasterDataNotFoundException("State", STATE_ID));

        mockMvc.perform(post("/api/v1/admin/masters/districts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void create_whenCodeAlreadyTaken_returns409() throws Exception {
        when(districtMasterService.create(any(DistrictMasterRequest.class)))
                .thenThrow(new MasterDataConflictException("District code already in use: WB-KOL"));

        mockMvc.perform(post("/api/v1/admin/masters/districts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void create_withNonSuperAdminRole_returns403() throws Exception {
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
    @WithMockUser(roles = "SUPER_ADMIN")
    void list_withStateIdFilter_passesItThrough() throws Exception {
        Page<DistrictMasterResponse> page = new PageImpl<>(List.of(responseFor(DISTRICT_ID, validRequest())));
        when(districtMasterService.list(eq(STATE_ID), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/masters/districts").param("stateId", STATE_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].districtCode").value("WB-KOL"));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void list_withoutStateIdFilter_passesNull() throws Exception {
        Page<DistrictMasterResponse> page = new PageImpl<>(List.of(responseFor(DISTRICT_ID, validRequest())));
        when(districtMasterService.list(isNull(), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/masters/districts"))
                .andExpect(status().isOk());
    }
}
