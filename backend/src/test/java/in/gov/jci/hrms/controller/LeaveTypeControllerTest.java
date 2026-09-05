package in.gov.jci.hrms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.jci.hrms.dto.LeaveTypeRequest;
import in.gov.jci.hrms.dto.LeaveTypeResponse;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataInUseException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.security.SecurityConfig;
import in.gov.jci.hrms.service.LeaveTypeService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LeaveTypeController.class)
@Import(SecurityConfig.class)
class LeaveTypeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LeaveTypeService leaveTypeService;

    private LeaveTypeRequest validRequest() {
        return new LeaveTypeRequest("CL", "Casual Leave", BigDecimal.valueOf(8.0), null, false, null, true);
    }

    private LeaveTypeResponse responseFor(Long id, LeaveTypeRequest request) {
        Instant now = Instant.now();
        return new LeaveTypeResponse(id, request.code(), request.name(), request.annualQuota(),
                request.maxAccumulationDays(), request.isEncashable(), request.careerLimitDays(), request.active(),
                now, now);
    }

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void create_asHrAdmin_returns201WithLocationHeader() throws Exception {
        LeaveTypeRequest request = validRequest();
        when(leaveTypeService.create(any(LeaveTypeRequest.class))).thenReturn(responseFor(1L, request));

        mockMvc.perform(post("/api/leave-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/leave-types/1"))
                .andExpect(jsonPath("$.code").value("CL"));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void create_asEmployee_returns403() throws Exception {
        mockMvc.perform(post("/api/leave-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void create_whenCodeAlreadyInUse_returns409() throws Exception {
        when(leaveTypeService.create(any(LeaveTypeRequest.class)))
                .thenThrow(new MasterDataConflictException("Leave Type code already in use: CL"));

        mockMvc.perform(post("/api/leave-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void list_asEmployee_returns200() throws Exception {
        mockMvc.perform(get("/api/leave-types"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getById_whenMissing_returns404() throws Exception {
        when(leaveTypeService.getById(99L)).thenThrow(new MasterDataNotFoundException("Leave Type", 99L));

        mockMvc.perform(get("/api/leave-types/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void delete_whenReferencedByLeaveBalance_returns409() throws Exception {
        doThrow(new MasterDataInUseException("Leave Type", 1L)).when(leaveTypeService).delete(1L);

        mockMvc.perform(delete("/api/leave-types/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void delete_whenNotReferenced_returns204() throws Exception {
        mockMvc.perform(delete("/api/leave-types/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void delete_asEmployee_returns403() throws Exception {
        mockMvc.perform(delete("/api/leave-types/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void getById_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get("/api/leave-types/1"))
                .andExpect(status().isUnauthorized());
    }
}
