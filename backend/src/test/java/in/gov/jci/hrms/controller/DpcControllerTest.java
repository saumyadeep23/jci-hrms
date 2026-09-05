package in.gov.jci.hrms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.jci.hrms.dto.DecommissionRequest;
import in.gov.jci.hrms.dto.DpcRequest;
import in.gov.jci.hrms.dto.DpcResponse;
import in.gov.jci.hrms.entity.CityClass;
import in.gov.jci.hrms.entity.DpcType;
import in.gov.jci.hrms.exception.MasterDataInUseException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.security.SecurityConfig;
import in.gov.jci.hrms.service.DpcService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DpcController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "HR_ADMIN")
class DpcControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DpcService dpcService;

    private DpcRequest validRequest() {
        return new DpcRequest(1L, "0001", "Delhi DPC 1", "New Delhi", "Delhi", null, null, null, true,
                "DEL-1", DpcType.DPC, "DL-ND", CityClass.X);
    }

    private DpcResponse responseFor(Long id, DpcRequest request) {
        Instant now = Instant.now();
        return new DpcResponse(id, request.roId(), "01", "Delhi RO", request.code(), request.name(),
                request.district(), request.state(), request.latitude(), request.longitude(),
                request.geofenceRadiusMeters(), request.active(), request.shortName(), request.dpcType(),
                request.districtCode(), request.cityClass(), now, now);
    }

    @Test
    void create_withValidRequest_returns201WithLocationHeader() throws Exception {
        DpcRequest request = validRequest();
        when(dpcService.create(any(DpcRequest.class))).thenReturn(responseFor(1L, request));

        mockMvc.perform(post("/api/dpcs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/dpcs/1"))
                .andExpect(jsonPath("$.code").value("0001"));
    }

    @Test
    void create_withMissingRequiredFields_returns400() throws Exception {
        DpcRequest invalid = new DpcRequest(null, "", "", "", "", null, null, null, null,
                null, null, null, null);

        mockMvc.perform(post("/api/dpcs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_withMalformedCode_returns400() throws Exception {
        DpcRequest invalid = new DpcRequest(1L, "12345", "Delhi DPC 1", "New Delhi", "Delhi", null, null, null, true,
                null, DpcType.DPC, null, CityClass.X);

        mockMvc.perform(post("/api/dpcs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_whenRegionalOfficeMissing_returns404() throws Exception {
        DpcRequest request = validRequest();
        when(dpcService.create(any(DpcRequest.class)))
                .thenThrow(new MasterDataNotFoundException("Regional Office", 1L));

        mockMvc.perform(post("/api/dpcs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void delete_whenReferencedByActiveEmployee_returns409() throws Exception {
        doThrow(new MasterDataInUseException("DPC", 1L))
                .when(dpcService).delete(org.mockito.ArgumentMatchers.eq(1L), any(), any());

        mockMvc.perform(delete("/api/dpcs/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DecommissionRequest("No longer required"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    void delete_whenNotReferenced_returns204() throws Exception {
        mockMvc.perform(delete("/api/dpcs/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DecommissionRequest("No longer required"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_withBlankReason_returns400() throws Exception {
        mockMvc.perform(delete("/api/dpcs/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DecommissionRequest(""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithAnonymousUser
    void create_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(post("/api/dpcs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void create_withWrongRole_returns403() throws Exception {
        mockMvc.perform(post("/api/dpcs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }
}
