package in.gov.jci.hrms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.jci.hrms.dto.DesignationRequest;
import in.gov.jci.hrms.dto.DesignationResponse;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataInUseException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.security.SecurityConfig;
import in.gov.jci.hrms.service.DesignationService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DesignationController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "HR_ADMIN")
class DesignationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DesignationService designationService;

    private DesignationRequest validRequest() {
        return new DesignationRequest("Backend Developer", "Builds and operates backend services", null);
    }

    private DesignationResponse responseFor(Long id, DesignationRequest request) {
        Instant now = Instant.now();
        return new DesignationResponse(id, request.title(), request.description(), now, now, null, null, null, null, null, null, null, null);
    }

    @Test
    void create_withValidRequest_returns201WithLocationHeader() throws Exception {
        DesignationRequest request = validRequest();
        when(designationService.create(any(DesignationRequest.class))).thenReturn(responseFor(1L, request));

        mockMvc.perform(post("/api/designations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/designations/1"))
                .andExpect(jsonPath("$.title").value("Backend Developer"));
    }

    @Test
    void create_withMissingRequiredFields_returns400() throws Exception {
        DesignationRequest invalid = new DesignationRequest("", null, null);

        mockMvc.perform(post("/api/designations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_whenTitleAlreadyInUse_returns409() throws Exception {
        DesignationRequest request = validRequest();
        when(designationService.create(any(DesignationRequest.class)))
                .thenThrow(new MasterDataConflictException("Designation title already in use: Backend Developer"));

        mockMvc.perform(post("/api/designations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    void getById_whenMissing_returns404() throws Exception {
        when(designationService.getById(99L)).thenThrow(new MasterDataNotFoundException("Designation", 99L));

        mockMvc.perform(get("/api/designations/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void delete_whenReferencedByActiveEmployee_returns409() throws Exception {
        doThrow(new MasterDataInUseException("Designation", 1L)).when(designationService).delete(1L);

        mockMvc.perform(delete("/api/designations/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    void delete_whenNotReferenced_returns204() throws Exception {
        mockMvc.perform(delete("/api/designations/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithAnonymousUser
    void getById_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get("/api/designations/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void create_withWrongRole_returns403() throws Exception {
        mockMvc.perform(post("/api/designations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }
}
