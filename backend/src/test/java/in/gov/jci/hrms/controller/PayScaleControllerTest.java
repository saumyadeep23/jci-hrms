package in.gov.jci.hrms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.jci.hrms.dto.DependencyCheckResponse;
import in.gov.jci.hrms.dto.PayScaleRequest;
import in.gov.jci.hrms.dto.PayScaleResponse;
import in.gov.jci.hrms.entity.ScaleType;
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
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Read-only surface only (Phase 1 of the pay_scale_master -> grade_scale_master
 * cutover, V60) - see PayScaleController's own javadoc for why POST/PUT/DELETE
 * are removed outright rather than stubbed. The 401-vs-405 split below was
 * verified empirically, not assumed: SecurityConfig's anyRequest().authenticated()
 * runs in the servlet filter chain, ahead of and independent of the
 * DispatcherServlet's handler-method resolution, so an unauthenticated request
 * is rejected before Spring ever discovers no POST/PUT/DELETE handler exists.
 * An authenticated-but-wrong-role request, by contrast, passes that filter,
 * reaches the dispatcher, finds no method-matching handler, and gets a plain
 * 405 - method-level @PreAuthorize never runs because no handler method was
 * resolved to run it against.
 */
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
    void getById_withValidId_returns200() throws Exception {
        when(payScaleService.getById(1L)).thenReturn(responseFor(1L, validRequest()));

        mockMvc.perform(get("/api/pay-scales/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grade").value("E1"));
    }

    @Test
    void list_returns200() throws Exception {
        when(payScaleService.list(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(responseFor(1L, validRequest()))));

        mockMvc.perform(get("/api/pay-scales"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].grade").value("E1"));
    }

    @Test
    void dependencies_returns200() throws Exception {
        when(payScaleService.dependencies(1L)).thenReturn(new DependencyCheckResponse(false, List.of()));

        mockMvc.perform(get("/api/pay-scales/1/dependencies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasActiveDependencies").value(false));
    }

    @Test
    void post_toRemovedCreateEndpoint_returns405() throws Exception {
        mockMvc.perform(post("/api/pay-scales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void put_toRemovedUpdateEndpoint_returns405() throws Exception {
        mockMvc.perform(put("/api/pay-scales/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void delete_toRemovedDeleteEndpoint_returns405() throws Exception {
        mockMvc.perform(delete("/api/pay-scales/1"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @WithAnonymousUser
    void get_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get("/api/pay-scales/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getById_withWrongRole_returns403() throws Exception {
        mockMvc.perform(get("/api/pay-scales/1"))
                .andExpect(status().isForbidden());
    }
}
