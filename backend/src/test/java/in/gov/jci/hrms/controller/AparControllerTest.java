package in.gov.jci.hrms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.jci.hrms.dto.AparAcceptRequest;
import in.gov.jci.hrms.dto.EmployeeAparRequest;
import in.gov.jci.hrms.dto.EmployeeAparResponse;
import in.gov.jci.hrms.dto.ReportingAssessmentRequest;
import in.gov.jci.hrms.dto.RepresentationRequest;
import in.gov.jci.hrms.dto.ReviewingAssessmentRequest;
import in.gov.jci.hrms.dto.SelfAppraisalRequest;
import in.gov.jci.hrms.entity.AparCycle;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeApar;
import in.gov.jci.hrms.entity.EmployeeAparStatus;
import in.gov.jci.hrms.entity.FinalGrading;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.repository.EmployeeAparRepository;
import in.gov.jci.hrms.security.AparSecurity;
import in.gov.jci.hrms.security.SecurityConfig;
import in.gov.jci.hrms.service.AparService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AparController.class)
@Import({SecurityConfig.class, AparSecurity.class})
@WithMockUser(roles = "HR_ADMIN")
class AparControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AparService aparService;

    @MockBean
    private EmployeeAparRepository employeeAparRepository;

    private Employee employeeWithId(Long id) {
        Department department = new Department("ENG", "Engineering");
        Designation designation = new Designation("Manager");
        Employee employee = new Employee("EMP-00" + id, "Asha", "Rao", "asha" + id + "@example.com",
                LocalDate.of(2020, 1, 1), department, designation);
        ReflectionTestUtils.setField(employee, "id", id);
        return employee;
    }

    private EmployeeApar aparWith(Long selfId, Long reportingOfficerId, Long reviewingOfficerId, Long acceptingAuthorityId) {
        AparCycle cycle = new AparCycle("2025-2026", LocalDate.of(2025, 4, 1), LocalDate.of(2026, 3, 31));
        return new EmployeeApar(cycle, employeeWithId(selfId), employeeWithId(reportingOfficerId),
                employeeWithId(reviewingOfficerId), employeeWithId(acceptingAuthorityId));
    }

    private EmployeeAparResponse responseWithStatus(EmployeeAparStatus status) {
        Instant now = Instant.now();
        return new EmployeeAparResponse(1L, 2L, "2025-2026", 3L, "EMP-003", 4L, 5L, 6L, status,
                null, null, null, null, null, null, null, null, now, now);
    }

    @Test
    void initiate_withValidRequest_returns201WithDraftStatus() throws Exception {
        EmployeeAparRequest request = new EmployeeAparRequest(2L, 3L, 4L, 5L, 6L);
        when(aparService.initiate(any(EmployeeAparRequest.class))).thenReturn(responseWithStatus(EmployeeAparStatus.DRAFT));

        mockMvc.perform(post("/api/apar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/apar/1"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void initiate_withMissingRequiredFields_returns400() throws Exception {
        EmployeeAparRequest invalid = new EmployeeAparRequest(null, null, null, null, null);

        mockMvc.perform(post("/api/apar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void initiate_whenEmployeeMissing_returns404() throws Exception {
        EmployeeAparRequest request = new EmployeeAparRequest(2L, 3L, 4L, 5L, 6L);
        when(aparService.initiate(any(EmployeeAparRequest.class))).thenThrow(new EmployeeNotFoundException(3L));

        mockMvc.perform(post("/api/apar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void submitSelfAppraisal_returns200() throws Exception {
        when(aparService.submitSelfAppraisal(org.mockito.ArgumentMatchers.eq(1L), any(SelfAppraisalRequest.class)))
                .thenReturn(responseWithStatus(EmployeeAparStatus.SUBMITTED_BY_EMPLOYEE));

        mockMvc.perform(post("/api/apar/1/self-appraisal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SelfAppraisalRequest("My appraisal"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED_BY_EMPLOYEE"));
    }

    @Test
    void submitReportingAssessment_whenWrongStatus_returns400() throws Exception {
        when(aparService.submitReportingAssessment(org.mockito.ArgumentMatchers.eq(1L), any(ReportingAssessmentRequest.class)))
                .thenThrow(new BusinessRuleViolationException("Employee APAR 1 must be SUBMITTED_BY_EMPLOYEE but is DRAFT"));

        mockMvc.perform(post("/api/apar/1/reporting-assessment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReportingAssessmentRequest(new BigDecimal("8.0"), "remarks"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitReviewingAssessment_returns200() throws Exception {
        when(aparService.submitReviewingAssessment(org.mockito.ArgumentMatchers.eq(1L), any(ReviewingAssessmentRequest.class)))
                .thenReturn(responseWithStatus(EmployeeAparStatus.REVIEWED));

        mockMvc.perform(post("/api/apar/1/reviewing-assessment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReviewingAssessmentRequest(new BigDecimal("8.0"), "remarks"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVIEWED"));
    }

    @Test
    void accept_returns200WithFinalGrading() throws Exception {
        when(aparService.accept(org.mockito.ArgumentMatchers.eq(1L), any(AparAcceptRequest.class)))
                .thenReturn(responseWithStatus(EmployeeAparStatus.ACCEPTED));

        mockMvc.perform(post("/api/apar/1/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AparAcceptRequest(new BigDecimal("8.25"), FinalGrading.VERY_GOOD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    void disclose_returns200() throws Exception {
        when(aparService.disclose(1L)).thenReturn(responseWithStatus(EmployeeAparStatus.DISCLOSED));

        mockMvc.perform(post("/api/apar/1/disclose"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISCLOSED"));
    }

    @Test
    void submitRepresentation_returns200() throws Exception {
        when(aparService.submitRepresentation(org.mockito.ArgumentMatchers.eq(1L), any(RepresentationRequest.class)))
                .thenReturn(responseWithStatus(EmployeeAparStatus.REPRESENTATION_SUBMITTED));

        mockMvc.perform(post("/api/apar/1/representation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RepresentationRequest("I disagree"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REPRESENTATION_SUBMITTED"));
    }

    @Test
    void finalizeApar_returns200() throws Exception {
        when(aparService.finalizeApar(1L)).thenReturn(responseWithStatus(EmployeeAparStatus.FINALIZED));

        mockMvc.perform(post("/api/apar/1/finalize"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINALIZED"));
    }

    @Test
    @WithAnonymousUser
    void initiate_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(post("/api/apar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EmployeeAparRequest(2L, 3L, 4L, 5L, 6L))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void initiate_withWrongRole_returns403() throws Exception {
        mockMvc.perform(post("/api/apar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EmployeeAparRequest(2L, 3L, 4L, 5L, 6L))))
                .andExpect(status().isForbidden());
    }

    @Test
    void submitSelfAppraisal_asOwningEmployee_returns200() throws Exception {
        when(employeeAparRepository.findById(1L)).thenReturn(Optional.of(aparWith(20L, 21L, 22L, 23L)));
        when(aparService.submitSelfAppraisal(org.mockito.ArgumentMatchers.eq(1L), any(SelfAppraisalRequest.class)))
                .thenReturn(responseWithStatus(EmployeeAparStatus.SUBMITTED_BY_EMPLOYEE));

        mockMvc.perform(post("/api/apar/1/self-appraisal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SelfAppraisalRequest("My appraisal")))
                        .with(jwt().jwt(builder -> builder.claim("employee_id", "20"))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isOk());
    }

    @Test
    void submitSelfAppraisal_asDifferentEmployee_returns403() throws Exception {
        when(employeeAparRepository.findById(1L)).thenReturn(Optional.of(aparWith(20L, 21L, 22L, 23L)));

        mockMvc.perform(post("/api/apar/1/self-appraisal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SelfAppraisalRequest("Not mine")))
                        .with(jwt().jwt(builder -> builder.claim("employee_id", "99"))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void submitReportingAssessment_asReportingOfficer_returns200() throws Exception {
        when(employeeAparRepository.findById(1L)).thenReturn(Optional.of(aparWith(20L, 21L, 22L, 23L)));
        when(aparService.submitReportingAssessment(org.mockito.ArgumentMatchers.eq(1L), any(ReportingAssessmentRequest.class)))
                .thenReturn(responseWithStatus(EmployeeAparStatus.REPORTED));

        mockMvc.perform(post("/api/apar/1/reporting-assessment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReportingAssessmentRequest(new BigDecimal("8.0"), "remarks")))
                        .with(jwt().jwt(builder -> builder.claim("employee_id", "21"))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isOk());
    }

    @Test
    void submitReportingAssessment_asNonReportingOfficer_returns403() throws Exception {
        when(employeeAparRepository.findById(1L)).thenReturn(Optional.of(aparWith(20L, 21L, 22L, 23L)));

        mockMvc.perform(post("/api/apar/1/reporting-assessment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReportingAssessmentRequest(new BigDecimal("8.0"), "remarks")))
                        .with(jwt().jwt(builder -> builder.claim("employee_id", "20"))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void accept_asAcceptingAuthority_returns200() throws Exception {
        when(employeeAparRepository.findById(1L)).thenReturn(Optional.of(aparWith(20L, 21L, 22L, 23L)));
        when(aparService.accept(org.mockito.ArgumentMatchers.eq(1L), any(AparAcceptRequest.class)))
                .thenReturn(responseWithStatus(EmployeeAparStatus.ACCEPTED));

        mockMvc.perform(post("/api/apar/1/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AparAcceptRequest(new BigDecimal("8.25"), FinalGrading.VERY_GOOD)))
                        .with(jwt().jwt(builder -> builder.claim("employee_id", "23"))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isOk());
    }
}
