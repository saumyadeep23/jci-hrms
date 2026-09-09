package in.gov.jci.hrms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.jci.hrms.dto.PayrollHraRateRequest;
import in.gov.jci.hrms.dto.PayrollHraRateResponse;
import in.gov.jci.hrms.dto.ProcurementAllowanceRequest;
import in.gov.jci.hrms.dto.ProcurementAllowanceResponse;
import in.gov.jci.hrms.dto.PtaxSlabRequest;
import in.gov.jci.hrms.dto.PtaxSlabResponse;
import in.gov.jci.hrms.dto.TransportAllowanceRequest;
import in.gov.jci.hrms.dto.TransportAllowanceResponse;
import in.gov.jci.hrms.exception.MasterDataValidationException;
import in.gov.jci.hrms.security.SecurityConfig;
import in.gov.jci.hrms.service.PayrollHraRateService;
import in.gov.jci.hrms.service.PayrollMasterService;
import in.gov.jci.hrms.service.PayrollStatutoryParameterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PayrollMasterController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "HR_ADMIN")
class PayrollMasterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PayrollMasterService payrollMasterService;

    @MockBean
    private PayrollHraRateService payrollHraRateService;

    @MockBean
    private PayrollStatutoryParameterService payrollStatutoryParameterService;

    @Test
    void createTransportAllowance_withValidRequest_returns201() throws Exception {
        TransportAllowanceRequest request = new TransportAllowanceRequest(1L, "X", new BigDecimal("3200.00"), LocalDate.of(2026, 4, 1));
        Instant now = Instant.now();
        TransportAllowanceResponse response = new TransportAllowanceResponse(1L, 1L, "E5", "X",
                new BigDecimal("3200.00"), LocalDate.of(2026, 4, 1), now);
        when(payrollMasterService.createTransportAllowance(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/payroll/masters/transport-allowances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cityClass").value("X"))
                .andExpect(jsonPath("$.baseRate").value(3200.00));
    }

    @Test
    void listTransportAllowances_returns200() throws Exception {
        Instant now = Instant.now();
        TransportAllowanceResponse response = new TransportAllowanceResponse(1L, 1L, "E5", "Y",
                new BigDecimal("2500.00"), LocalDate.of(2026, 4, 1), now);
        when(payrollMasterService.listTransportAllowances()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/payroll/masters/transport-allowances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cityClass").value("Y"));
    }

    @Test
    void createProcurementAllowance_withValidRequest_returns201() throws Exception {
        ProcurementAllowanceRequest request = new ProcurementAllowanceRequest(2L, new BigDecimal("1500.00"), LocalDate.of(2026, 4, 1));
        Instant now = Instant.now();
        ProcurementAllowanceResponse response = new ProcurementAllowanceResponse(1L, 2L, "Field Officer",
                new BigDecimal("1500.00"), LocalDate.of(2026, 4, 1), null, now);
        when(payrollMasterService.createProcurementAllowance(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/payroll/masters/procurement-allowances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.designationTitle").value("Field Officer"));
    }

    @Test
    void listProcurementAllowances_returns200() throws Exception {
        Instant now = Instant.now();
        ProcurementAllowanceResponse response = new ProcurementAllowanceResponse(1L, 2L, "Field Officer",
                new BigDecimal("1500.00"), LocalDate.of(2026, 4, 1), null, now);
        when(payrollMasterService.listProcurementAllowances()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/payroll/masters/procurement-allowances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].designationId").value(2));
    }

    @Test
    void createPtaxSlab_withValidRequest_returns201() throws Exception {
        PtaxSlabRequest request = new PtaxSlabRequest("WB", new BigDecimal("10000.00"), new BigDecimal("15000.00"),
                new BigDecimal("110.00"), 2, new BigDecimal("300.00"), LocalDate.of(2026, 4, 1));
        Instant now = Instant.now();
        PtaxSlabResponse response = new PtaxSlabResponse(1L, "WB", new BigDecimal("10000.00"), new BigDecimal("15000.00"),
                new BigDecimal("110.00"), 2, new BigDecimal("300.00"), LocalDate.of(2026, 4, 1), null, now);
        when(payrollMasterService.createPtaxSlab(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/payroll/masters/ptax-slabs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stateCode").value("WB"));
    }

    @Test
    void listPtaxSlabsByState_returns200() throws Exception {
        Instant now = Instant.now();
        PtaxSlabResponse response = new PtaxSlabResponse(1L, "WB", new BigDecimal("0.00"), new BigDecimal("10000.00"),
                new BigDecimal("0.00"), null, null, LocalDate.of(2026, 4, 1), null, now);
        when(payrollMasterService.listPtaxSlabsByState("WB")).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/payroll/masters/ptax-slabs/by-state/WB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stateCode").value("WB"));
    }

    @Test
    void createPtaxSlab_withSlabMaxNotGreaterThanSlabMin_returns400() throws Exception {
        PtaxSlabRequest request = new PtaxSlabRequest("WB", new BigDecimal("10000.00"), new BigDecimal("5000.00"),
                new BigDecimal("110.00"), null, null, LocalDate.of(2026, 4, 1));
        when(payrollMasterService.createPtaxSlab(any()))
                .thenThrow(new MasterDataValidationException("slabMax must be greater than slabMin"));

        mockMvc.perform(post("/api/v1/payroll/masters/ptax-slabs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createHraRate_withValidRequest_returns201() throws Exception {
        PayrollHraRateRequest request = new PayrollHraRateRequest("X", new BigDecimal("30.00"), new BigDecimal("6750.00"),
                LocalDate.of(2024, 1, 1), null, "Class X (Metros)");
        Instant now = Instant.now();
        PayrollHraRateResponse response = new PayrollHraRateResponse(1L, "X", new BigDecimal("30.00"), new BigDecimal("6750.00"),
                LocalDate.of(2024, 1, 1), null, "Class X (Metros)", now, now);
        when(payrollHraRateService.create(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/payroll/masters/hra-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cityClass").value("X"))
                .andExpect(jsonPath("$.ratePercentage").value(30.00));
    }

    @Test
    void createHraRate_withInvalidCityClass_returns400() throws Exception {
        String invalidJson = "{\"cityClass\":\"A\",\"ratePercentage\":30.00,\"minAmount\":6750.00,\"effectiveFrom\":\"2024-01-01\"}";

        mockMvc.perform(post("/api/v1/payroll/masters/hra-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listHraRates_returns200() throws Exception {
        Instant now = Instant.now();
        PayrollHraRateResponse response = new PayrollHraRateResponse(1L, "Y", new BigDecimal("20.00"), new BigDecimal("4500.00"),
                LocalDate.of(2024, 1, 1), null, null, now, now);
        when(payrollHraRateService.listAll()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/payroll/masters/hra-rates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cityClass").value("Y"));
    }

    @Test
    void updateHraRate_settingEffectiveTo_returns200() throws Exception {
        PayrollHraRateRequest request = new PayrollHraRateRequest("Z", new BigDecimal("10.00"), new BigDecimal("2250.00"),
                LocalDate.of(2024, 1, 1), LocalDate.of(2026, 12, 31), "Closed out");
        Instant now = Instant.now();
        PayrollHraRateResponse response = new PayrollHraRateResponse(3L, "Z", new BigDecimal("10.00"), new BigDecimal("2250.00"),
                LocalDate.of(2024, 1, 1), LocalDate.of(2026, 12, 31), "Closed out", now, now);
        when(payrollHraRateService.update(org.mockito.ArgumentMatchers.eq(3L), any())).thenReturn(response);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/payroll/masters/hra-rates/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveTo").value("2026-12-31"));
    }
}
