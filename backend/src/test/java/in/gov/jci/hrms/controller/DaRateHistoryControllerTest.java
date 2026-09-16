package in.gov.jci.hrms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.jci.hrms.dto.DaRateHistoryRequest;
import in.gov.jci.hrms.dto.DaRateHistoryResponse;
import in.gov.jci.hrms.entity.ScaleType;
import in.gov.jci.hrms.exception.DaRateNotFoundException;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.security.SecurityConfig;
import in.gov.jci.hrms.service.DaRateHistoryService;
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
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DaRateHistoryController.class)
@Import(SecurityConfig.class)
class DaRateHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DaRateHistoryService daRateHistoryService;

    private DaRateHistoryRequest validRequest() {
        return new DaRateHistoryRequest(ScaleType.IDA, LocalDate.of(2026, 4, 1), new BigDecimal("18.00"), true,
                "F.No.1(3)/2026-E.II", LocalDate.of(2026, 3, 20), "Revised per DoPT circular");
    }

    private DaRateHistoryResponse responseFor(Long id, DaRateHistoryRequest request) {
        return new DaRateHistoryResponse(id, request.scaleType(), request.effectiveFrom(), null,
                request.daPercentage(), request.active(), request.orderNumber(), request.orderDate(), request.remarks());
    }

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void create_withValidRequest_returns201() throws Exception {
        when(daRateHistoryService.create(any(DaRateHistoryRequest.class))).thenReturn(responseFor(1L, validRequest()));

        mockMvc.perform(post("/api/da-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/da-rates/1"))
                .andExpect(jsonPath("$.scaleType").value("IDA"));
    }

    // Non-financial RBAC migration (docs/security/RBAC_MIGRATION_REPORT.md): SUPER_ADMIN removed from
    // this master-data mutation - HR_ADMIN only, matching the pattern used elsewhere in this controller.
    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void create_asSuperAdmin_returns403() throws Exception {
        mockMvc.perform(post("/api/da-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "FINANCE_ADMIN")
    void create_asFinanceAdmin_returns403() throws Exception {
        mockMvc.perform(post("/api/da-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void create_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(post("/api/da-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void create_withMissingRequiredFields_returns400() throws Exception {
        DaRateHistoryRequest invalid = new DaRateHistoryRequest(null, null, null, null, null, null, null);

        mockMvc.perform(post("/api/da-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void create_whenDuplicateEffectiveDate_returns409() throws Exception {
        when(daRateHistoryService.create(any(DaRateHistoryRequest.class)))
                .thenThrow(new MasterDataConflictException("DA Rate already has an entry for IDA effective 2026-04-01"));

        mockMvc.perform(post("/api/da-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    @WithMockUser(roles = "FINANCE_ADMIN")
    void list_asFinanceAdmin_returns200() throws Exception {
        when(daRateHistoryService.list()).thenReturn(List.of(responseFor(1L, validRequest())));

        mockMvc.perform(get("/api/da-rates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].scaleType").value("IDA"));
    }

    @Test
    @WithAnonymousUser
    void list_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get("/api/da-rates"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "FINANCE_ADMIN")
    void getCurrent_whenFound_returns200() throws Exception {
        when(daRateHistoryService.getCurrent(ScaleType.IDA, LocalDate.of(2026, 6, 15)))
                .thenReturn(responseFor(1L, validRequest()));

        mockMvc.perform(get("/api/da-rates/current").param("scaleType", "IDA").param("effectiveDate", "2026-06-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scaleType").value("IDA"));
    }

    @Test
    @WithMockUser(roles = "FINANCE_ADMIN")
    void getCurrent_whenNoneFound_returns404() throws Exception {
        when(daRateHistoryService.getCurrent(ScaleType.IDA, LocalDate.of(2020, 1, 1)))
                .thenThrow(new DaRateNotFoundException(ScaleType.IDA, LocalDate.of(2020, 1, 1)));

        mockMvc.perform(get("/api/da-rates/current").param("scaleType", "IDA").param("effectiveDate", "2020-01-01"))
                .andExpect(status().isNotFound());
    }
}
