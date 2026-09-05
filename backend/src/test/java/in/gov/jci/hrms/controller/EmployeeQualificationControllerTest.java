package in.gov.jci.hrms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.jci.hrms.dto.QualificationRequest;
import in.gov.jci.hrms.dto.QualificationResponse;
import in.gov.jci.hrms.entity.CourseType;
import in.gov.jci.hrms.entity.QualificationLevel;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.security.SecurityConfig;
import in.gov.jci.hrms.service.EmployeeQualificationService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EmployeeQualificationController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "HR_ADMIN")
class EmployeeQualificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EmployeeQualificationService qualificationService;

    private QualificationRequest validRequest() {
        return new QualificationRequest(QualificationLevel.GRADUATION, "B.Tech", "Computer Science",
                "Delhi University", "IIT Delhi", 2015, new BigDecimal("82.50"), null, CourseType.FULL_TIME,
                true, null);
    }

    private QualificationResponse responseFor(Long id, QualificationRequest request) {
        return new QualificationResponse(id, 1L, request.qualificationLevel(), request.degreeTitle(),
                request.specialization(), request.boardUniversity(), request.institutionName(),
                request.passingYear(), request.percentageCgpa(), request.divisionClass(), request.courseType(),
                request.highestQualification(), request.certificateDocumentS3Key(), false, null, null);
    }

    @Test
    void create_withValidRequest_returns201WithLocationHeader() throws Exception {
        QualificationRequest request = validRequest();
        when(qualificationService.create(eq(1L), any(QualificationRequest.class))).thenReturn(responseFor(5L, request));

        mockMvc.perform(post("/api/employees/1/qualifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/employees/1/qualifications/5"))
                .andExpect(jsonPath("$.degreeTitle").value("B.Tech"));
    }

    @Test
    void create_whenEmployeeMissing_returns404() throws Exception {
        when(qualificationService.create(eq(99L), any(QualificationRequest.class)))
                .thenThrow(new EmployeeNotFoundException(99L));

        mockMvc.perform(post("/api/employees/99/qualifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/employees/1/qualifications/5"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithAnonymousUser
    void list_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get("/api/employees/1/qualifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void create_withWrongRole_returns403() throws Exception {
        mockMvc.perform(post("/api/employees/1/qualifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }
}
