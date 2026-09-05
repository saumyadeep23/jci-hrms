package in.gov.jci.hrms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.jci.hrms.dto.PostIncumbencyRequest;
import in.gov.jci.hrms.dto.PostIncumbencyResponse;
import in.gov.jci.hrms.entity.AssignmentType;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.exception.MasterDataValidationException;
import in.gov.jci.hrms.security.SecurityConfig;
import in.gov.jci.hrms.service.PostIncumbencyService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostIncumbencyController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "HR_ADMIN")
class PostIncumbencyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PostIncumbencyService postIncumbencyService;

    private PostIncumbencyRequest validRequest() {
        return new PostIncumbencyRequest(1L, 2L, AssignmentType.SUBSTANTIVE, LocalDate.of(2026, 1, 1), null, "ORD/1");
    }

    private PostIncumbencyResponse responseFor(Long id, PostIncumbencyRequest request) {
        Instant now = Instant.now();
        return new PostIncumbencyResponse(id, request.postId(), "PC-001", request.employeeId(), "EMP-002",
                request.assignmentType(), request.startDate(), request.endDate(), request.orderReference(),
                true, now, now);
    }

    @Test
    void create_withValidRequest_returns201WithLocationHeader() throws Exception {
        PostIncumbencyRequest request = validRequest();
        when(postIncumbencyService.create(any(PostIncumbencyRequest.class))).thenReturn(responseFor(1L, request));

        mockMvc.perform(post("/api/post-incumbencies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/post-incumbencies/1"))
                .andExpect(jsonPath("$.assignmentType").value("SUBSTANTIVE"));
    }

    @Test
    void create_withMissingRequiredFields_returns400() throws Exception {
        PostIncumbencyRequest invalid = new PostIncumbencyRequest(null, null, null, null, null, null);

        mockMvc.perform(post("/api/post-incumbencies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_whenPostMissing_returns404() throws Exception {
        PostIncumbencyRequest request = validRequest();
        when(postIncumbencyService.create(any(PostIncumbencyRequest.class)))
                .thenThrow(new MasterDataNotFoundException("Post", 1L));

        mockMvc.perform(post("/api/post-incumbencies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_whenEmployeeMissing_returns404() throws Exception {
        PostIncumbencyRequest request = validRequest();
        when(postIncumbencyService.create(any(PostIncumbencyRequest.class)))
                .thenThrow(new EmployeeNotFoundException(2L));

        mockMvc.perform(post("/api/post-incumbencies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getById_whenMissing_returns404() throws Exception {
        when(postIncumbencyService.getById(99L)).thenThrow(new MasterDataNotFoundException("Post Incumbency", 99L));

        mockMvc.perform(get("/api/post-incumbencies/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void end_withValidRequest_returns200() throws Exception {
        PostIncumbencyRequest request = validRequest();
        PostIncumbencyResponse ended = new PostIncumbencyResponse(1L, request.postId(), "PC-001", request.employeeId(),
                "EMP-002", request.assignmentType(), request.startDate(), LocalDate.of(2026, 6, 1),
                request.orderReference(), false, Instant.now(), Instant.now());
        when(postIncumbencyService.end(eq(1L), eq(LocalDate.of(2026, 6, 1)))).thenReturn(ended);

        mockMvc.perform(post("/api/post-incumbencies/1/end").param("endDate", "2026-06-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.endDate").value("2026-06-01"));
    }

    @Test
    void end_whenAlreadyEnded_returns400() throws Exception {
        when(postIncumbencyService.end(eq(1L), any(LocalDate.class)))
                .thenThrow(new MasterDataValidationException("Post Incumbency 1 is already ended"));

        mockMvc.perform(post("/api/post-incumbencies/1/end").param("endDate", "2026-06-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithAnonymousUser
    void getById_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get("/api/post-incumbencies/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void create_withWrongRole_returns403() throws Exception {
        mockMvc.perform(post("/api/post-incumbencies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }
}
