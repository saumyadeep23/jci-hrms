package in.gov.jci.hrms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.jci.hrms.dto.DeviceRegistrationRequest;
import in.gov.jci.hrms.dto.DeviceStatusUpdateRequest;
import in.gov.jci.hrms.dto.RegisteredDeviceResponse;
import in.gov.jci.hrms.entity.DeviceApprovalStatus;
import in.gov.jci.hrms.entity.DeviceType;
import in.gov.jci.hrms.security.SecurityConfig;
import in.gov.jci.hrms.service.RegisteredDeviceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RegisteredDeviceController.class)
@Import(SecurityConfig.class)
class RegisteredDeviceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RegisteredDeviceService registeredDeviceService;

    private RegisteredDeviceResponse response() {
        return new RegisteredDeviceResponse(1L, 7L, "EMP-007", "Asha Rao", "Head Office", "Asha's Phone",
                DeviceType.ANDROID_MOBILE, "device-uuid-123", "Android 14", DeviceApprovalStatus.PENDING_APPROVAL, null, null, Instant.now());
    }

    private DeviceRegistrationRequest validRequest() {
        return new DeviceRegistrationRequest("Asha's Phone", DeviceType.ANDROID_MOBILE, "device-uuid-123", "Android 14");
    }

    @Test
    void register_resolvesEmployeeIdFromJwtClaim_returns201() throws Exception {
        when(registeredDeviceService.register(eq(7L), any(DeviceRegistrationRequest.class))).thenReturn(response());

        mockMvc.perform(post("/api/v1/attendance/devices/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest()))
                        .with(jwt().jwt(builder -> builder.claim("employee_id", "7"))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
    }

    @Test
    void register_withoutJwtEmployeeIdClaim_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/attendance/devices/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest()))
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithAnonymousUser
    void register_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/attendance/devices/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mine_resolvesEmployeeIdFromJwtClaim_returns200() throws Exception {
        when(registeredDeviceService.mine(7L)).thenReturn(List.of(response()));

        mockMvc.perform(get("/api/v1/attendance/devices/mine")
                        .with(jwt().jwt(builder -> builder.claim("employee_id", "7"))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].deviceIdentifier").value("device-uuid-123"));
    }

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void listAll_asHrAdmin_returns200() throws Exception {
        when(registeredDeviceService.listAll()).thenReturn(List.of(response()));

        mockMvc.perform(get("/api/v1/attendance/devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].employeeCode").value("EMP-007"));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void listAll_asEmployee_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/attendance/devices"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateStatus_asHrAdmin_resolvesApproverFromJwtClaim_returns200() throws Exception {
        DeviceStatusUpdateRequest request = new DeviceStatusUpdateRequest(DeviceApprovalStatus.APPROVED);
        when(registeredDeviceService.updateStatus(eq(1L), any(DeviceStatusUpdateRequest.class), eq(2L))).thenReturn(response());

        mockMvc.perform(patch("/api/v1/attendance/devices/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(jwt().jwt(builder -> builder.claim("employee_id", "2"))
                                .authorities(new SimpleGrantedAuthority("ROLE_HR_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void updateStatus_asEmployee_returns403() throws Exception {
        DeviceStatusUpdateRequest request = new DeviceStatusUpdateRequest(DeviceApprovalStatus.APPROVED);

        mockMvc.perform(patch("/api/v1/attendance/devices/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
