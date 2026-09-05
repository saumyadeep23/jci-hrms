package in.gov.jci.hrms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.jci.hrms.dto.HolidayBulkUploadResult;
import in.gov.jci.hrms.dto.HolidayMasterCreateRequest;
import in.gov.jci.hrms.dto.HolidayMasterRow;
import in.gov.jci.hrms.dto.HolidayMasterUpdateRequest;
import in.gov.jci.hrms.entity.HolidayType;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.security.SecurityConfig;
import in.gov.jci.hrms.service.HolidayMasterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HolidayMasterController.class)
@Import(SecurityConfig.class)
class HolidayMasterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private HolidayMasterService holidayMasterService;

    private HolidayMasterRow row() {
        return new HolidayMasterRow(1L, LocalDate.of(2026, 1, 26), "Republic Day", HolidayType.GAZETTED, false, "ALL", "All India / Central", null);
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void list_asEmployee_returns200() throws Exception {
        when(holidayMasterService.list(2026, "ALL", null)).thenReturn(List.of(row()));

        mockMvc.perform(get("/api/v1/master/holidays").param("year", "2026").param("stateCode", "ALL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].holidayName").value("Republic Day"));
    }

    @Test
    @WithAnonymousUser
    void list_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/master/holidays"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void create_asHrAdmin_returns201() throws Exception {
        HolidayMasterCreateRequest request = new HolidayMasterCreateRequest(
                "Republic Day", LocalDate.of(2026, 1, 26), HolidayType.GAZETTED, List.of("ALL"), null);
        when(holidayMasterService.create(any(HolidayMasterCreateRequest.class))).thenReturn(List.of(row()));

        mockMvc.perform(post("/api/v1/master/holidays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].stateCode").value("ALL"));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void create_asEmployee_returns403() throws Exception {
        HolidayMasterCreateRequest request = new HolidayMasterCreateRequest(
                "Republic Day", LocalDate.of(2026, 1, 26), HolidayType.GAZETTED, List.of("ALL"), null);

        mockMvc.perform(post("/api/v1/master/holidays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void create_withEmptyStateCodes_returns400() throws Exception {
        HolidayMasterCreateRequest invalid = new HolidayMasterCreateRequest(
                "Republic Day", LocalDate.of(2026, 1, 26), HolidayType.GAZETTED, List.of(), null);

        mockMvc.perform(post("/api/v1/master/holidays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void update_asSuperAdmin_returns200() throws Exception {
        HolidayMasterUpdateRequest request = new HolidayMasterUpdateRequest("Republic Day", LocalDate.of(2026, 1, 26), HolidayType.GAZETTED, "ALL", null);
        when(holidayMasterService.update(eq(1L), any(HolidayMasterUpdateRequest.class))).thenReturn(row());

        mockMvc.perform(put("/api/v1/master/holidays/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void delete_asHrAdmin_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/master/holidays/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void delete_whenWithinLockedPayrollCycle_returns400() throws Exception {
        doThrow(new BusinessRuleViolationException("locked")).when(holidayMasterService).delete(1L);

        mockMvc.perform(delete("/api/v1/master/holidays/1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void delete_asEmployee_returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/master/holidays/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void bulkUpload_asHrAdmin_returns200() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "gazette.csv", "text/csv", "holidayDate,holidayName,holidayType,stateCodes,isRestricted\n".getBytes());
        when(holidayMasterService.bulkUpload(any())).thenReturn(new HolidayBulkUploadResult(0, 0, List.of()));

        mockMvc.perform(multipart("/api/v1/master/holidays/bulk-upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(0));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void bulkUpload_asEmployee_returns403() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "gazette.csv", "text/csv", "x".getBytes());

        mockMvc.perform(multipart("/api/v1/master/holidays/bulk-upload").file(file))
                .andExpect(status().isForbidden());
    }
}
