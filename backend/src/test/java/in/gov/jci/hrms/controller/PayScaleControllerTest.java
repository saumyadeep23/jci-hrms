package in.gov.jci.hrms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.jci.hrms.dto.PayScaleRequest;
import in.gov.jci.hrms.dto.PayScaleResponse;
import in.gov.jci.hrms.entity.ScaleType;
import in.gov.jci.hrms.exception.MasterDataInUseException;
import in.gov.jci.hrms.exception.MasterDataValidationException;
import in.gov.jci.hrms.security.SecurityConfig;
import in.gov.jci.hrms.service.PayScaleService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PayScaleController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "HR_ADMIN")
class PayScaleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PayScaleService payScaleService;

    private PayScaleRequest validRequest() {
        return new PayScaleRequest(ScaleType.IDA, "E1", new BigDecimal("40000.00"), new BigDecimal("60000.00"),
                new BigDecimal("3.00"), true);
    }

    private PayScaleResponse responseFor(Long id, PayScaleRequest request) {
        Instant now = Instant.now();
        return new PayScaleResponse(id, request.scaleType(), request.grade(), request.minimumBasic(),
                request.maximumBasic(), request.incrementRate(), request.active(), now, now);
    }

    @Test
    void create_withValidRequest_returns201WithLocationHeader() throws Exception {
        PayScaleRequest request = validRequest();
        when(payScaleService.create(any(PayScaleRequest.class))).thenReturn(responseFor(1L, request));

        mockMvc.perform(post("/api/pay-scales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/pay-scales/1"))
                .andExpect(jsonPath("$.grade").value("E1"));
    }

    @Test
    void create_withMissingRequiredFields_returns400() throws Exception {
        PayScaleRequest invalid = new PayScaleRequest(null, "", null, null, null, null);

        mockMvc.perform(post("/api/pay-scales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_whenMinimumGreaterThanMaximum_returns400() throws Exception {
        PayScaleRequest request = validRequest();
        when(payScaleService.create(any(PayScaleRequest.class)))
                .thenThrow(new MasterDataValidationException("minimumBasic must not be greater than maximumBasic"));

        mockMvc.perform(post("/api/pay-scales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    void delete_whenReferencedByActiveEmployee_returns409() throws Exception {
        doThrow(new MasterDataInUseException("Pay Scale", 1L)).when(payScaleService).delete(1L);

        mockMvc.perform(delete("/api/pay-scales/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    void delete_whenNotReferenced_returns204() throws Exception {
        mockMvc.perform(delete("/api/pay-scales/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithAnonymousUser
    void create_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(post("/api/pay-scales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void create_withWrongRole_returns403() throws Exception {
        mockMvc.perform(post("/api/pay-scales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }
}
