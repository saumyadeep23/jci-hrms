package in.gov.jci.hrms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.jci.hrms.dto.MobilePunchRequest;
import in.gov.jci.hrms.dto.MobilePunchResponse;
import in.gov.jci.hrms.entity.PunchType;
import in.gov.jci.hrms.entity.ReviewStatus;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.security.AttendanceAggregationSecurity;
import in.gov.jci.hrms.security.SecurityConfig;
import in.gov.jci.hrms.service.MobilePunchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SEC-002 (docs/security/SEC_001_002_REMEDIATION.md): the ownership-related
 * tests here (marked below) directly regression-cover the fix - a punch for
 * an employeeId other than the caller's own now requires HR_ADMIN/SUPER_ADMIN,
 * where it previously required only isAuthenticated().
 */
@WebMvcTest(MobilePunchController.class)
@Import({SecurityConfig.class, AttendanceAggregationSecurity.class})
@WithMockUser(roles = "HR_ADMIN")
class MobilePunchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MobilePunchService mobilePunchService;

    private MobilePunchRequest validRequest() {
        return new MobilePunchRequest(1L, Instant.parse("2026-08-23T09:00:00Z"), PunchType.IN,
                new BigDecimal("28.6139"), new BigDecimal("77.2090"), new BigDecimal("5.0"), "device-123", null);
    }

    private MobilePunchResponse responseFor(Long id, MobilePunchRequest request, ReviewStatus reviewStatus) {
        return new MobilePunchResponse(id, request.employeeId(), "EMP-001", request.punchTime(), request.punchType(),
                request.latitude(), request.longitude(), request.accuracyMeters(),
                reviewStatus == ReviewStatus.VALID, reviewStatus, request.deviceId(), request.photoS3Key(), Instant.now());
    }

    @Test
    void create_whenWithinGeofence_returns201WithValidStatus() throws Exception {
        MobilePunchRequest request = validRequest();
        when(mobilePunchService.create(any(MobilePunchRequest.class), any(), anyBoolean()))
                .thenReturn(responseFor(1L, request, ReviewStatus.VALID));

        mockMvc.perform(post("/api/attendance/punch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/attendance/punch/1"))
                .andExpect(jsonPath("$.reviewStatus").value("VALID"));
    }

    @Test
    void create_whenOutsideGeofence_returns201WithFlaggedStatus() throws Exception {
        MobilePunchRequest request = validRequest();
        when(mobilePunchService.create(any(MobilePunchRequest.class), any(), anyBoolean()))
                .thenReturn(responseFor(2L, request, ReviewStatus.FLAGGED_FOR_REVIEW));

        mockMvc.perform(post("/api/attendance/punch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reviewStatus").value("FLAGGED_FOR_REVIEW"))
                .andExpect(jsonPath("$.isWithinGeofence").value(false));
    }

    @Test
    void create_withMissingRequiredFields_returns400() throws Exception {
        MobilePunchRequest invalid = new MobilePunchRequest(null, null, null, null, null, null, null, null);

        mockMvc.perform(post("/api/attendance/punch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_withOutOfRangeLatitude_returns400() throws Exception {
        MobilePunchRequest invalid = new MobilePunchRequest(1L, Instant.now(), PunchType.IN,
                new BigDecimal("200.0"), new BigDecimal("77.2090"), null, null, null);

        mockMvc.perform(post("/api/attendance/punch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_whenEmployeeMissing_returns404() throws Exception {
        MobilePunchRequest request = validRequest();
        when(mobilePunchService.create(any(MobilePunchRequest.class), any(), anyBoolean()))
                .thenThrow(new EmployeeNotFoundException(1L));

        mockMvc.perform(post("/api/attendance/punch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getById_returns200() throws Exception {
        MobilePunchRequest request = validRequest();
        when(mobilePunchService.getById(1L)).thenReturn(responseFor(1L, request, ReviewStatus.VALID));

        mockMvc.perform(get("/api/attendance/punch/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithAnonymousUser
    void create_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(post("/api/attendance/punch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(mobilePunchService);
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getById_withWrongRole_returns403() throws Exception {
        mockMvc.perform(get("/api/attendance/punch/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_withoutEmployeeIdInBody_resolvesFromJwtEmployeeIdClaim() throws Exception {
        MobilePunchRequest bodyWithoutEmployeeId = new MobilePunchRequest(null, Instant.parse("2026-08-23T09:00:00Z"),
                PunchType.IN, new BigDecimal("28.6139"), new BigDecimal("77.2090"), new BigDecimal("5.0"), "device-123", null);
        when(mobilePunchService.create(argThat(req -> req != null && req.employeeId() == null), any(), anyBoolean()))
                .thenReturn(responseFor(1L, new MobilePunchRequest(7L, bodyWithoutEmployeeId.punchTime(), bodyWithoutEmployeeId.punchType(),
                        bodyWithoutEmployeeId.latitude(), bodyWithoutEmployeeId.longitude(), bodyWithoutEmployeeId.accuracyMeters(),
                        bodyWithoutEmployeeId.deviceId(), bodyWithoutEmployeeId.photoS3Key()), ReviewStatus.VALID));

        mockMvc.perform(post("/api/attendance/punch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bodyWithoutEmployeeId))
                        .with(jwt().jwt(builder -> builder.claim("employee_id", "7"))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.employeeId").value(7));
    }

    @Test
    void create_withoutEmployeeIdAndNoJwtClaim_returns400() throws Exception {
        MobilePunchRequest bodyWithoutEmployeeId = new MobilePunchRequest(null, Instant.parse("2026-08-23T09:00:00Z"),
                PunchType.IN, new BigDecimal("28.6139"), new BigDecimal("77.2090"), new BigDecimal("5.0"), "device-123", null);
        when(mobilePunchService.create(any(MobilePunchRequest.class), any(), anyBoolean()))
                .thenThrow(new BusinessRuleViolationException(
                        "employeeId was not supplied and could not be resolved from the caller's identity"));

        mockMvc.perform(post("/api/attendance/punch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bodyWithoutEmployeeId))
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isBadRequest());
    }

    // ---- SEC-002 regression coverage: self-punch allowed, cross-employee denied ----

    @Test
    void create_asEmployeePunchingOwnExplicitEmployeeId_returns201() throws Exception {
        MobilePunchRequest ownRequest = validRequest(); // employeeId = 1L
        when(mobilePunchService.create(any(MobilePunchRequest.class), any(), anyBoolean()))
                .thenReturn(responseFor(1L, ownRequest, ReviewStatus.VALID));

        mockMvc.perform(post("/api/attendance/punch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ownRequest))
                        .with(jwt().jwt(builder -> builder.claim("employee_id", "1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isCreated());
    }

    /** The core SEC-002 fix: before remediation this returned 201 (see git history of this test). */
    @Test
    void create_asOrdinaryEmployeeForAnotherEmployeeId_returns403_andServiceIsNeverInvoked() throws Exception {
        MobilePunchRequest forSomeoneElse = validRequest(); // employeeId = 1L, caller is employee 2

        mockMvc.perform(post("/api/attendance/punch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(forSomeoneElse))
                        .with(jwt().jwt(builder -> builder.claim("employee_id", "2"))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(mobilePunchService);
    }

    /** Same cross-employee attempt from a non-HR admin-tier role (FINANCE_ADMIN) - also denied; only HR_ADMIN/SUPER_ADMIN may act on another employee's behalf. */
    @Test
    @WithMockUser(roles = "FINANCE_ADMIN")
    void create_asFinanceAdminForAnotherEmployeeId_returns403() throws Exception {
        MobilePunchRequest forSomeoneElse = validRequest(); // employeeId = 1L; @WithMockUser carries no employee_id claim

        mockMvc.perform(post("/api/attendance/punch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(forSomeoneElse)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(mobilePunchService);
    }

    /** The legitimate privileged workflow the endpoint's javadoc always described ("an HR tool"), now actually enforced. Class-level @WithMockUser(roles = "HR_ADMIN") applies here. */
    @Test
    void create_asHrAdminForAnotherEmployeeId_returns201() throws Exception {
        MobilePunchRequest request = validRequest();
        when(mobilePunchService.create(any(MobilePunchRequest.class), any(), anyBoolean()))
                .thenReturn(responseFor(1L, request, ReviewStatus.VALID));

        mockMvc.perform(post("/api/attendance/punch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    /** Service-layer defense-in-depth (SEC-002 remediation doc, "another API path" scenario): even if authorization were somehow bypassed, the service itself rejects a mismatched target. */
    @Test
    void create_whenServiceItselfRejectsOwnership_propagatesAsForbidden() throws Exception {
        MobilePunchRequest request = validRequest();
        when(mobilePunchService.create(any(MobilePunchRequest.class), any(), anyBoolean()))
                .thenThrow(new AccessDeniedException("Not authorized to record attendance for employee 1 on behalf of another employee"));

        mockMvc.perform(post("/api/attendance/punch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
