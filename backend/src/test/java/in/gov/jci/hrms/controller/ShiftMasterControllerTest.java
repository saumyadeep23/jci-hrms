package in.gov.jci.hrms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.jci.hrms.dto.ShiftMasterRequest;
import in.gov.jci.hrms.dto.ShiftMasterResponse;
import in.gov.jci.hrms.exception.MasterDataInUseException;
import in.gov.jci.hrms.exception.MasterDataValidationException;
import in.gov.jci.hrms.security.SecurityConfig;
import in.gov.jci.hrms.service.ShiftMasterService;
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
import java.time.LocalTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ShiftMasterController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "HR_ADMIN")
class ShiftMasterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ShiftMasterService shiftMasterService;

    private ShiftMasterRequest validRequest() {
        return new ShiftMasterRequest("SHIFT_A", "Shift A (Morning)", LocalTime.of(6, 0), LocalTime.of(14, 0), 10, false,
                480, 240, null, true);
    }

    private ShiftMasterResponse responseFor(Long id, ShiftMasterRequest request) {
        Instant now = Instant.now();
        return new ShiftMasterResponse(id, request.shiftCode(), request.shiftName(), request.startTime(), request.endTime(),
                request.gracePeriodMinutes(), request.crossesMidnight(), request.fullDayMinutes(), request.halfDayMinutes(),
                request.applicableOfficeType(), request.active(), now, now);
    }

    @Test
    void create_withValidRequest_returns201WithLocationHeader() throws Exception {
        ShiftMasterRequest request = validRequest();
        when(shiftMasterService.create(any(ShiftMasterRequest.class))).thenReturn(responseFor(1L, request));

        mockMvc.perform(post("/api/v1/attendance/shifts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/attendance/shifts/1"))
                .andExpect(jsonPath("$.shiftCode").value("SHIFT_A"));
    }

    @Test
    void create_withMissingRequiredFields_returns400() throws Exception {
        ShiftMasterRequest invalid = new ShiftMasterRequest("", "", null, null, null, null, null, null, null, null);

        mockMvc.perform(post("/api/v1/attendance/shifts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_whenEndTimeNotAfterStartTimeAndNotOvernight_returns400() throws Exception {
        ShiftMasterRequest request = validRequest();
        when(shiftMasterService.create(any(ShiftMasterRequest.class)))
                .thenThrow(new MasterDataValidationException("endTime must be after startTime unless the shift crosses midnight"));

        mockMvc.perform(post("/api/v1/attendance/shifts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    void delete_whenReferencedByActiveDependency_returns409() throws Exception {
        doThrow(new MasterDataInUseException("Shift", 1L)).when(shiftMasterService).delete(1L);

        mockMvc.perform(delete("/api/v1/attendance/shifts/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    void delete_whenNotReferenced_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/attendance/shifts/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithAnonymousUser
    void create_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/attendance/shifts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void create_withWrongRole_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/attendance/shifts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }
}
