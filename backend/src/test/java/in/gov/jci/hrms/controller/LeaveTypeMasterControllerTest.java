package in.gov.jci.hrms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.jci.hrms.dto.LeaveTypeMasterResponse;
import in.gov.jci.hrms.dto.LeaveTypeMasterUpdateRequest;
import in.gov.jci.hrms.entity.EmploymentCategory;
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

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LeaveTypeMasterController.class)
@Import(SecurityConfig.class)
class LeaveTypeMasterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LeaveTypeService leaveTypeService;

    private LeaveTypeMasterResponse response() {
        return new LeaveTypeMasterResponse(1L, "EL", "Earned Leave", 300, true, true, true, Set.of(EmploymentCategory.REGULAR));
    }

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void list_asHrAdmin_returns200() throws Exception {
        when(leaveTypeService.listForMaster()).thenReturn(List.of(response()));

        mockMvc.perform(get("/api/v1/master/leave-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("EL"))
                .andExpect(jsonPath("$[0].eligibleCategories[0]").value("REGULAR"));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void list_asEmployee_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/master/leave-types"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void list_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/master/leave-types"))
                .andExpect(status().isUnauthorized());
    }

    // Non-financial RBAC migration (docs/security/RBAC_MIGRATION_REPORT.md): SUPER_ADMIN removed -
    // this master-data mutation is HR_ADMIN only now.
    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void update_asSuperAdmin_returns403() throws Exception {
        LeaveTypeMasterUpdateRequest request = new LeaveTypeMasterUpdateRequest(300, true, Set.of(EmploymentCategory.REGULAR));

        mockMvc.perform(put("/api/v1/master/leave-types/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void update_withEmptyEligibleCategories_returns400() throws Exception {
        LeaveTypeMasterUpdateRequest invalid = new LeaveTypeMasterUpdateRequest(300, true, Set.of());

        mockMvc.perform(put("/api/v1/master/leave-types/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void update_whenMissing_returns404() throws Exception {
        LeaveTypeMasterUpdateRequest request = new LeaveTypeMasterUpdateRequest(300, true, Set.of(EmploymentCategory.REGULAR));
        when(leaveTypeService.updateMaster(eq(99L), any(LeaveTypeMasterUpdateRequest.class)))
                .thenThrow(new MasterDataNotFoundException("Leave Type", 99L));

        mockMvc.perform(put("/api/v1/master/leave-types/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void update_asEmployee_returns403() throws Exception {
        LeaveTypeMasterUpdateRequest request = new LeaveTypeMasterUpdateRequest(300, true, Set.of(EmploymentCategory.REGULAR));

        mockMvc.perform(put("/api/v1/master/leave-types/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
