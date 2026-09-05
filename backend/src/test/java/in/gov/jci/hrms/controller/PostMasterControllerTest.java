package in.gov.jci.hrms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.jci.hrms.dto.PostIncumbencyResponse;
import in.gov.jci.hrms.dto.PostInventorySummaryResponse;
import in.gov.jci.hrms.dto.PostMasterRequest;
import in.gov.jci.hrms.dto.PostMasterResponse;
import in.gov.jci.hrms.dto.PostStatusUpdateRequest;
import in.gov.jci.hrms.entity.AssignmentType;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataInUseException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.exception.MasterDataValidationException;
import in.gov.jci.hrms.entity.VacancyStatus;
import in.gov.jci.hrms.security.SecurityConfig;
import in.gov.jci.hrms.service.PostIncumbencyService;
import in.gov.jci.hrms.service.PostMasterService;
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
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostMasterController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "HR_ADMIN")
class PostMasterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PostMasterService postMasterService;

    @MockBean
    private PostIncumbencyService postIncumbencyService;

    private PostMasterRequest validRequest() {
        return new PostMasterRequest("PC-001", "Chief Engineer", 10L, 20L, null, null, null, null, null, true, true);
    }

    private PostMasterResponse responseFor(Long id, PostMasterRequest request) {
        Instant now = Instant.now();
        return new PostMasterResponse(id, request.postCode(), request.title(),
                request.departmentId(), "Engineering", request.designationId(), "Manager",
                null, null, null, null, null, null, null, null, null, null,
                VacancyStatus.VACANT, request.isBudgeted(), request.active(), now, now);
    }

    @Test
    void create_withValidRequest_returns201WithLocationHeader() throws Exception {
        PostMasterRequest request = validRequest();
        when(postMasterService.create(any(PostMasterRequest.class))).thenReturn(responseFor(1L, request));

        mockMvc.perform(post("/api/v1/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/posts/1"))
                .andExpect(jsonPath("$.postCode").value("PC-001"));
    }

    @Test
    void create_withMissingRequiredFields_returns400() throws Exception {
        PostMasterRequest invalid = new PostMasterRequest("", "", null, null, null, null, null, null, null, null, null);

        mockMvc.perform(post("/api/v1/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_whenCodeAlreadyInUse_returns409() throws Exception {
        PostMasterRequest request = validRequest();
        when(postMasterService.create(any(PostMasterRequest.class)))
                .thenThrow(new MasterDataConflictException("Post code already in use: PC-001"));

        mockMvc.perform(post("/api/v1/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    void update_whenReportingPostIsSelf_returns400() throws Exception {
        PostMasterRequest request = validRequest();
        when(postMasterService.update(org.mockito.ArgumentMatchers.eq(1L), any(PostMasterRequest.class)))
                .thenThrow(new MasterDataValidationException("A post cannot report to itself"));

        mockMvc.perform(put("/api/v1/posts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    void getById_whenMissing_returns404() throws Exception {
        when(postMasterService.getById(99L)).thenThrow(new MasterDataNotFoundException("Post", 99L));

        mockMvc.perform(get("/api/v1/posts/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void delete_whenActivelyOccupied_returns409() throws Exception {
        doThrow(new MasterDataInUseException("Post", 1L)).when(postMasterService).delete(1L);

        mockMvc.perform(delete("/api/v1/posts/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    void delete_whenUnreferenced_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/posts/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithAnonymousUser
    void getById_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/posts/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void create_withWrongRole_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    void summary_returnsKpiCounts() throws Exception {
        when(postMasterService.summary()).thenReturn(new PostInventorySummaryResponse(10, 4, 3, 2, 1));

        mockMvc.perform(get("/api/v1/posts/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSanctioned").value(10))
                .andExpect(jsonPath("$.occupied").value(4))
                .andExpect(jsonPath("$.vacant").value(3))
                .andExpect(jsonPath("$.frozen").value(2))
                .andExpect(jsonPath("$.abolished").value(1));
    }

    @Test
    void incumbencyHistory_returnsChronologicalLedger() throws Exception {
        Instant now = Instant.now();
        when(postMasterService.incumbencyHistory(1L)).thenReturn(List.of(
                new PostIncumbencyResponse(1L, 1L, "PC-001", 100L, "0001", AssignmentType.SUBSTANTIVE,
                        LocalDate.of(2024, 1, 15), null, "ORD-1", true, now, now)));

        mockMvc.perform(get("/api/v1/posts/1/incumbency-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].employeeCode").value("0001"));
    }

    @Test
    void updateStatus_toFrozen_returns200() throws Exception {
        PostMasterRequest request = validRequest();
        PostMasterResponse frozen = responseFor(1L, request);
        when(postMasterService.updateStatus(eq(1L), any(PostStatusUpdateRequest.class))).thenReturn(frozen);

        mockMvc.perform(patch("/api/v1/posts/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PostStatusUpdateRequest(VacancyStatus.FROZEN, "Budget freeze"))))
                .andExpect(status().isOk());
    }

    @Test
    void updateStatus_toAbolishedWhileOccupied_returns409() throws Exception {
        when(postMasterService.updateStatus(eq(1L), any(PostStatusUpdateRequest.class)))
                .thenThrow(new MasterDataInUseException("Post", 1L));

        mockMvc.perform(patch("/api/v1/posts/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PostStatusUpdateRequest(VacancyStatus.ABOLISHED, null))))
                .andExpect(status().isConflict());
    }

    @Test
    void list_withFilters_returnsOk() throws Exception {
        mockMvc.perform(get("/api/v1/posts")
                        .param("departmentId", "10")
                        .param("vacancyStatus", "VACANT")
                        .param("isBudgeted", "true")
                        .param("locationType", "DPC")
                        .param("search", "chief"))
                .andExpect(status().isOk());
    }
}
