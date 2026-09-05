package in.gov.jci.hrms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.jci.hrms.dto.DecommissionRequest;
import in.gov.jci.hrms.dto.RegionalOfficeRequest;
import in.gov.jci.hrms.dto.RegionalOfficeResponse;
import in.gov.jci.hrms.entity.CityClass;
import in.gov.jci.hrms.entity.OfficeType;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataInUseException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.security.SecurityConfig;
import in.gov.jci.hrms.service.RegionalOfficeService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RegionalOfficeController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "HR_ADMIN")
class RegionalOfficeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RegionalOfficeService regionalOfficeService;

    private RegionalOfficeRequest validRequest() {
        return new RegionalOfficeRequest("01", "Delhi RO", "Delhi", CityClass.X, true, OfficeType.REGIONAL_OFFICE,
                "1 Connaught Place", "New Delhi", "New Delhi", "ND-01", "110001",
                new java.math.BigDecimal("28.6139"), new java.math.BigDecimal("77.2090"),
                new java.math.BigDecimal("50.00"), java.math.BigDecimal.ZERO, true);
    }

    private RegionalOfficeResponse responseFor(Long id, RegionalOfficeRequest request) {
        Instant now = Instant.now();
        return new RegionalOfficeResponse(id, request.code(), request.name(), request.state(),
                request.cityClass(), request.active(), request.officeType(), request.addressLine(), request.city(),
                request.district(), request.districtCode(), request.pinCode(), request.latitude(), request.longitude(),
                request.geofenceRadiusMeters(), request.recreationClubDeduction(),
                request.procurementAllowanceApplicable(), now, now);
    }

    @Test
    void create_withValidRequest_returns201WithLocationHeader() throws Exception {
        RegionalOfficeRequest request = validRequest();
        when(regionalOfficeService.create(any(RegionalOfficeRequest.class))).thenReturn(responseFor(1L, request));

        mockMvc.perform(post("/api/regional-offices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/regional-offices/1"))
                .andExpect(jsonPath("$.code").value("01"));
    }

    @Test
    void create_withMissingRequiredFields_returns400() throws Exception {
        RegionalOfficeRequest invalid = new RegionalOfficeRequest("", "", "", null, null, null,
                null, null, null, null, null, null, null, null, null, null);

        mockMvc.perform(post("/api/regional-offices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_withMalformedCode_returns400() throws Exception {
        RegionalOfficeRequest invalid = new RegionalOfficeRequest("RO-DEL", "Delhi RO", "Delhi", CityClass.X, true,
                OfficeType.REGIONAL_OFFICE, null, null, null, null, null, null, null, null, null, false);

        mockMvc.perform(post("/api/regional-offices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_whenCodeAlreadyInUse_returns409() throws Exception {
        RegionalOfficeRequest request = validRequest();
        when(regionalOfficeService.create(any(RegionalOfficeRequest.class)))
                .thenThrow(new MasterDataConflictException("Regional Office code already in use: 01"));

        mockMvc.perform(post("/api/regional-offices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    void getById_whenMissing_returns404() throws Exception {
        when(regionalOfficeService.getById(99L)).thenThrow(new MasterDataNotFoundException("Regional Office", 99L));

        mockMvc.perform(get("/api/regional-offices/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void delete_whenReferencedByActiveEmployee_returns409() throws Exception {
        doThrow(new MasterDataInUseException("Regional Office", 1L))
                .when(regionalOfficeService).delete(org.mockito.ArgumentMatchers.eq(1L), any(), any());

        mockMvc.perform(delete("/api/regional-offices/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DecommissionRequest("No longer required"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    void delete_whenNotReferenced_returns204() throws Exception {
        mockMvc.perform(delete("/api/regional-offices/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DecommissionRequest("No longer required"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_withBlankReason_returns400() throws Exception {
        mockMvc.perform(delete("/api/regional-offices/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DecommissionRequest(""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithAnonymousUser
    void getById_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get("/api/regional-offices/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void create_withWrongRole_returns403() throws Exception {
        mockMvc.perform(post("/api/regional-offices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }
}
