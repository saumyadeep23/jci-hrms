package in.gov.jci.hrms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.jci.hrms.dto.LeaveApplicationPreviewRequest;
import in.gov.jci.hrms.dto.LeaveApplicationPreviewResponse;
import in.gov.jci.hrms.dto.LeaveApplicationRequest;
import in.gov.jci.hrms.dto.LeaveApplicationResponse;
import in.gov.jci.hrms.entity.LeaveApplicationStatus;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.InsufficientLeaveBalanceException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.LeaveApplicationRepository;
import in.gov.jci.hrms.security.AttendanceAggregationSecurity;
import in.gov.jci.hrms.security.LeaveApplicationSecurity;
import in.gov.jci.hrms.security.SecurityConfig;
import in.gov.jci.hrms.service.LeaveApplicationService;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LeaveApplicationController.class)
@Import({SecurityConfig.class, LeaveApplicationSecurity.class, AttendanceAggregationSecurity.class})
@WithMockUser(roles = "HR_ADMIN")
class LeaveApplicationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LeaveApplicationService leaveApplicationService;

    @MockBean
    private LeaveApplicationRepository leaveApplicationRepository;

    private LeaveApplicationRequest validRequest() {
        return new LeaveApplicationRequest(1L, 2L, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 12),
                new BigDecimal("3.0"), "Family event", null);
    }

    private LeaveApplicationResponse responseWithStatus(LeaveApplicationStatus status) {
        Instant now = Instant.now();
        return new LeaveApplicationResponse(1L, 1L, "EMP-001", 2L, "EL",
                LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 12), new BigDecimal("3.0"), "Family event",
                status, in.gov.jci.hrms.entity.LeaveSession.FULL_DAY, null, null, null, null, null, null, null, null,
                null, null, null, null, now, now);
    }

    @Test
    void create_withValidRequest_returns201WithDraftStatus() throws Exception {
        LeaveApplicationRequest request = validRequest();
        when(leaveApplicationService.create(any(LeaveApplicationRequest.class), any(), anyBoolean()))
                .thenReturn(responseWithStatus(LeaveApplicationStatus.DRAFT));

        mockMvc.perform(post("/api/leave-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/leave-applications/1"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void create_withMissingRequiredFields_returns400() throws Exception {
        LeaveApplicationRequest invalid = new LeaveApplicationRequest(null, null, null, null, null, null, null);

        mockMvc.perform(post("/api/leave-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_whenEmployeeMissing_returns404() throws Exception {
        LeaveApplicationRequest request = validRequest();
        when(leaveApplicationService.create(any(LeaveApplicationRequest.class), any(), anyBoolean()))
                .thenThrow(new EmployeeNotFoundException(1L));

        mockMvc.perform(post("/api/leave-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_whenLeaveTypeMissing_returns404() throws Exception {
        LeaveApplicationRequest request = validRequest();
        when(leaveApplicationService.create(any(LeaveApplicationRequest.class), any(), anyBoolean()))
                .thenThrow(new MasterDataNotFoundException("Leave Type", 2L));

        mockMvc.perform(post("/api/leave-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_withValidRequest_returns200() throws Exception {
        when(leaveApplicationService.update(org.mockito.ArgumentMatchers.eq(1L), any(LeaveApplicationRequest.class)))
                .thenReturn(responseWithStatus(LeaveApplicationStatus.DRAFT));

        mockMvc.perform(put("/api/leave-applications/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void update_whenNotDraft_returns400() throws Exception {
        when(leaveApplicationService.update(org.mockito.ArgumentMatchers.eq(1L), any(LeaveApplicationRequest.class)))
                .thenThrow(new BusinessRuleViolationException("Leave Application 1 must be DRAFT but is PENDING_APPROVAL"));

        mockMvc.perform(put("/api/leave-applications/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_asOwningEmployee_returns200() throws Exception {
        in.gov.jci.hrms.entity.Department department = new in.gov.jci.hrms.entity.Department("ENG", "Engineering");
        in.gov.jci.hrms.entity.Designation designation = new in.gov.jci.hrms.entity.Designation("Manager");
        in.gov.jci.hrms.entity.Employee employee = new in.gov.jci.hrms.entity.Employee("EMP-001", "Asha", "Rao",
                "asha.rao@example.com", LocalDate.of(2020, 1, 1), department, designation);
        org.springframework.test.util.ReflectionTestUtils.setField(employee, "id", 9L);
        in.gov.jci.hrms.entity.LeaveType leaveType = new in.gov.jci.hrms.entity.LeaveType(
                "EL", "Earned Leave", new BigDecimal("30.0"), true, true);
        in.gov.jci.hrms.entity.LeaveApplication application = new in.gov.jci.hrms.entity.LeaveApplication(
                employee, leaveType, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 12), new BigDecimal("3.0"), "Family event");
        when(leaveApplicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(leaveApplicationService.update(org.mockito.ArgumentMatchers.eq(1L), any(LeaveApplicationRequest.class)))
                .thenReturn(responseWithStatus(LeaveApplicationStatus.DRAFT));

        mockMvc.perform(put("/api/leave-applications/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest()))
                        .with(jwt().jwt(builder -> builder.claim("employee_id", "9"))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isOk());
    }

    @Test
    void update_asDifferentEmployee_returns403() throws Exception {
        in.gov.jci.hrms.entity.Department department = new in.gov.jci.hrms.entity.Department("ENG", "Engineering");
        in.gov.jci.hrms.entity.Designation designation = new in.gov.jci.hrms.entity.Designation("Manager");
        in.gov.jci.hrms.entity.Employee employee = new in.gov.jci.hrms.entity.Employee("EMP-001", "Asha", "Rao",
                "asha.rao@example.com", LocalDate.of(2020, 1, 1), department, designation);
        org.springframework.test.util.ReflectionTestUtils.setField(employee, "id", 9L);
        in.gov.jci.hrms.entity.LeaveType leaveType = new in.gov.jci.hrms.entity.LeaveType(
                "EL", "Earned Leave", new BigDecimal("30.0"), true, true);
        in.gov.jci.hrms.entity.LeaveApplication application = new in.gov.jci.hrms.entity.LeaveApplication(
                employee, leaveType, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 12), new BigDecimal("3.0"), "Family event");
        when(leaveApplicationRepository.findById(1L)).thenReturn(Optional.of(application));

        mockMvc.perform(put("/api/leave-applications/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest()))
                        .with(jwt().jwt(builder -> builder.claim("employee_id", "10"))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void submit_withSufficientBalance_returns200WithPendingApprovalStatus() throws Exception {
        when(leaveApplicationService.submit(1L)).thenReturn(responseWithStatus(LeaveApplicationStatus.PENDING_APPROVAL));

        mockMvc.perform(post("/api/leave-applications/1/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
    }

    @Test
    void submit_withInsufficientBalance_returns422() throws Exception {
        when(leaveApplicationService.submit(1L))
                .thenThrow(new InsufficientLeaveBalanceException("Insufficient leave balance: available 1.0, requested 3.0"));

        mockMvc.perform(post("/api/leave-applications/1/submit"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Unprocessable Entity"));
    }

    @Test
    void approve_returns200WithApprovedStatus() throws Exception {
        when(leaveApplicationService.approve(1L)).thenReturn(responseWithStatus(LeaveApplicationStatus.APPROVED));

        mockMvc.perform(post("/api/leave-applications/1/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void approve_whenNotPendingApproval_returns400() throws Exception {
        when(leaveApplicationService.approve(1L))
                .thenThrow(new BusinessRuleViolationException("Leave Application 1 must be PENDING_APPROVAL but is DRAFT"));

        mockMvc.perform(post("/api/leave-applications/1/approve"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reject_returns200WithRejectedStatus() throws Exception {
        when(leaveApplicationService.reject(1L)).thenReturn(responseWithStatus(LeaveApplicationStatus.REJECTED));

        mockMvc.perform(post("/api/leave-applications/1/reject"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void cancel_returns200WithCancelledStatus() throws Exception {
        when(leaveApplicationService.cancel(1L)).thenReturn(responseWithStatus(LeaveApplicationStatus.CANCELLED));

        mockMvc.perform(post("/api/leave-applications/1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancel_whenAlreadyApproved_returns400() throws Exception {
        when(leaveApplicationService.cancel(1L))
                .thenThrow(new BusinessRuleViolationException("Leave Application 1 cannot be cancelled from status APPROVED"));

        mockMvc.perform(post("/api/leave-applications/1/cancel"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void preview_returns200WithCalendarAndDebitableDays() throws Exception {
        LeaveApplicationPreviewRequest request = new LeaveApplicationPreviewRequest(1L, 2L,
                LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 12), null);
        when(leaveApplicationService.preview(any(LeaveApplicationPreviewRequest.class)))
                .thenReturn(new LeaveApplicationPreviewResponse(new BigDecimal("3"), new BigDecimal("3.0"), true, null));

        mockMvc.perform(post("/api/leave-applications/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.debitableDays").value(3.0));
    }

    @Test
    void preview_whenRuleViolated_returns200WithInvalidAndMessage() throws Exception {
        LeaveApplicationPreviewRequest request = new LeaveApplicationPreviewRequest(1L, 2L,
                LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 12), null);
        when(leaveApplicationService.preview(any(LeaveApplicationPreviewRequest.class)))
                .thenReturn(new LeaveApplicationPreviewResponse(new BigDecimal("3"), null, false,
                        "CL cannot be applied contiguously before/after EL"));

        mockMvc.perform(post("/api/leave-applications/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.message").value("CL cannot be applied contiguously before/after EL"));
    }

    @Test
    @WithAnonymousUser
    void create_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(post("/api/leave-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "FINANCE_ADMIN")
    void approve_withWrongRole_returns403() throws Exception {
        mockMvc.perform(post("/api/leave-applications/1/approve"))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancel_asOwningEmployee_returns200() throws Exception {
        in.gov.jci.hrms.entity.Department department = new in.gov.jci.hrms.entity.Department("ENG", "Engineering");
        in.gov.jci.hrms.entity.Designation designation = new in.gov.jci.hrms.entity.Designation("Manager");
        in.gov.jci.hrms.entity.Employee employee = new in.gov.jci.hrms.entity.Employee("EMP-001", "Asha", "Rao",
                "asha.rao@example.com", LocalDate.of(2020, 1, 1), department, designation);
        org.springframework.test.util.ReflectionTestUtils.setField(employee, "id", 9L);
        in.gov.jci.hrms.entity.LeaveType leaveType = new in.gov.jci.hrms.entity.LeaveType(
                "EL", "Earned Leave", new BigDecimal("30.0"), true, true);
        in.gov.jci.hrms.entity.LeaveApplication application = new in.gov.jci.hrms.entity.LeaveApplication(
                employee, leaveType, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 12), new BigDecimal("3.0"), "Family event");
        when(leaveApplicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(leaveApplicationService.cancel(1L)).thenReturn(responseWithStatus(LeaveApplicationStatus.CANCELLED));

        mockMvc.perform(post("/api/leave-applications/1/cancel")
                        .with(jwt().jwt(builder -> builder.claim("employee_id", "9"))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isOk());
    }

    @Test
    void cancel_asDifferentEmployee_returns403() throws Exception {
        in.gov.jci.hrms.entity.Department department = new in.gov.jci.hrms.entity.Department("ENG", "Engineering");
        in.gov.jci.hrms.entity.Designation designation = new in.gov.jci.hrms.entity.Designation("Manager");
        in.gov.jci.hrms.entity.Employee employee = new in.gov.jci.hrms.entity.Employee("EMP-001", "Asha", "Rao",
                "asha.rao@example.com", LocalDate.of(2020, 1, 1), department, designation);
        org.springframework.test.util.ReflectionTestUtils.setField(employee, "id", 9L);
        in.gov.jci.hrms.entity.LeaveType leaveType = new in.gov.jci.hrms.entity.LeaveType(
                "EL", "Earned Leave", new BigDecimal("30.0"), true, true);
        in.gov.jci.hrms.entity.LeaveApplication application = new in.gov.jci.hrms.entity.LeaveApplication(
                employee, leaveType, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 12), new BigDecimal("3.0"), "Family event");
        when(leaveApplicationRepository.findById(1L)).thenReturn(Optional.of(application));

        mockMvc.perform(post("/api/leave-applications/1/cancel")
                        .with(jwt().jwt(builder -> builder.claim("employee_id", "10"))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isForbidden());
    }

    // ---- SEC-007 (docs/security/SEC_001_002_REMEDIATION.md pattern) ----

    @Test
    void create_asOrdinaryEmployeeForAnotherEmployeeId_returns403_andServiceIsNeverInvoked() throws Exception {
        LeaveApplicationRequest forSomeoneElse = validRequest(); // employeeId = 1L, caller is employee 2

        mockMvc.perform(post("/api/leave-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(forSomeoneElse))
                        .with(jwt().jwt(builder -> builder.claim("employee_id", "2"))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(leaveApplicationService);
    }

    @Test
    void create_asEmployeePunchingOwnEmployeeId_returns201() throws Exception {
        LeaveApplicationRequest ownRequest = validRequest(); // employeeId = 1L
        when(leaveApplicationService.create(any(LeaveApplicationRequest.class), any(), anyBoolean()))
                .thenReturn(responseWithStatus(LeaveApplicationStatus.DRAFT));

        mockMvc.perform(post("/api/leave-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ownRequest))
                        .with(jwt().jwt(builder -> builder.claim("employee_id", "1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isCreated());
    }
}
