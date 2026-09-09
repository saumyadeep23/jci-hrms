package in.gov.jci.hrms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.jci.hrms.dto.EmployeeRequest;
import in.gov.jci.hrms.dto.EmployeeResponse;
import in.gov.jci.hrms.entity.EmployeeStatus;
import in.gov.jci.hrms.entity.Gender;
import in.gov.jci.hrms.entity.MaritalStatus;
import in.gov.jci.hrms.entity.Salutation;
import in.gov.jci.hrms.exception.DuplicateEmployeeException;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.security.EmployeeSecurity;
import in.gov.jci.hrms.security.SecurityConfig;
import in.gov.jci.hrms.service.Employee360Service;
import in.gov.jci.hrms.service.EmployeeService;
import in.gov.jci.hrms.service.EmployeeServiceBookService;
import in.gov.jci.hrms.service.SuperannuationExtensionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EmployeeController.class)
@Import({SecurityConfig.class, EmployeeSecurity.class})
@WithMockUser(roles = "HR_ADMIN")
class EmployeeControllerTest {

    private static final Long DEPARTMENT_ID = 10L;
    private static final Long DESIGNATION_ID = 20L;
    private static final Long RO_ID = 30L;
    private static final Long DPC_ID = 40L;
    private static final Long PAY_SCALE_ID = 50L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EmployeeService employeeService;

    @MockBean
    private Employee360Service employee360Service;

    @MockBean
    private EmployeeServiceBookService employeeServiceBookService;

    @MockBean
    private SuperannuationExtensionService superannuationExtensionService;

    @MockBean
    private in.gov.jci.hrms.service.SeparatedEmployeeDirectoryService separatedEmployeeDirectoryService;

    private EmployeeRequest validRequest() {
        return new EmployeeRequest(
                Salutation.MS, "Asha", null, "Rao", Gender.FEMALE, LocalDate.of(1990, 5, 1), MaritalStatus.SINGLE, null,
                "Indian", null, "ABCDE1234F", "CPF00001", null, "123456789012", "asha.rao@example.com", null, "9876543210", null,
                LocalDate.of(2024, 1, 15), DEPARTMENT_ID, DESIGNATION_ID, RO_ID, DPC_ID, PAY_SCALE_ID,
                EmployeeStatus.ACTIVE, false, null, null, null, null, null
        );
    }

    private EmployeeResponse responseFor(Long id, EmployeeRequest request) {
        Instant now = Instant.now();
        return new EmployeeResponse(
                id, "0001", "EMP000001", null, request.salutation(), request.firstName(), request.middleName(), request.lastName(),
                request.firstName() + " " + request.lastName(), request.gender(), request.dateOfBirth(), request.maritalStatus(),
                request.bloodGroup(), request.nationality(), request.motherTongue(), request.panNumber(), "XXXX-XXXX-9012",
                request.personalEmail(), request.officialEmail(), request.phone(), request.officialMobile(),
                request.dateOfJoining(),
                request.departmentId(), "Engineering",
                request.designationId(), "Backend Developer",
                request.roId(), "Delhi RO",
                request.dpcId(), "Delhi DPC 1",
                request.payScaleId(), "E1",
                request.status(),
                request.geofenceExempted(), "REGIONAL_OFFICE",
                now, now, null, null, null, null, null, false, false, false, null
        );
    }

    @Test
    void create_withValidRequest_returns201WithLocationHeader() throws Exception {
        EmployeeRequest request = validRequest();
        when(employeeService.create(any(EmployeeRequest.class))).thenReturn(responseFor(1L, request));

        mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/employees/1"))
                .andExpect(jsonPath("$.personalEmail").value("asha.rao@example.com"))
                .andExpect(jsonPath("$.departmentName").value("Engineering"));
    }

    @Test
    void create_withMissingRequiredFields_returns400WithFieldErrors() throws Exception {
        EmployeeRequest invalid = new EmployeeRequest(
                null, "", null, "Rao", null, null, null, null, "", null, "not-a-pan", null, null, null, "not-an-email", null, "123", null,
                null, null, null, null, null, null, null, false, null, null, null, null, null
        );

        mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(containsString("personalEmail")));
    }

    @Test
    void create_whenDepartmentMissing_returns404() throws Exception {
        EmployeeRequest request = validRequest();
        when(employeeService.create(any(EmployeeRequest.class)))
                .thenThrow(new MasterDataNotFoundException("Department", DEPARTMENT_ID));

        mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void getById_whenFound_returns200() throws Exception {
        EmployeeRequest request = validRequest();
        when(employeeService.getById(1L)).thenReturn(responseFor(1L, request));

        mockMvc.perform(get("/api/employees/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.personalEmail").value("asha.rao@example.com"));
    }

    @Test
    void getById_whenMissing_returns404() throws Exception {
        when(employeeService.getById(99L)).thenThrow(new EmployeeNotFoundException(99L));

        mockMvc.perform(get("/api/employees/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Employee not found with id 99"));
    }

    @Test
    void list_returns200WithPagedContent() throws Exception {
        EmployeeRequest request = validRequest();
        Pageable pageable = PageRequest.of(0, 20);
        Page<EmployeeResponse> page = new PageImpl<>(List.of(responseFor(1L, request)), pageable, 1);
        when(employeeService.list(any(), any(), any(), any(), any(), any(), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/employees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].personalEmail").value("asha.rao@example.com"));
    }

    @Test
    void update_whenDuplicateEmail_returns409() throws Exception {
        EmployeeRequest request = validRequest();
        when(employeeService.update(eq(1L), any(EmployeeRequest.class)))
                .thenThrow(new DuplicateEmployeeException("Employee code, PAN, or personal email already in use"));

        mockMvc.perform(put("/api/employees/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    void update_withMissingRequiredFields_returns400() throws Exception {
        EmployeeRequest invalid = new EmployeeRequest(
                null, "Asha", null, "Rao", null, null, null, null, "", null, "not-a-pan", null, null, null, "asha.rao@example.com",
                null, "123", null, null, null, null, null, null, null, null, false, null, null, null, null, null
        );

        mockMvc.perform(put("/api/employees/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/employees/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithAnonymousUser
    void list_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get("/api/employees"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "FINANCE_ADMIN")
    void create_withWrongRole_returns403() throws Exception {
        mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    void getById_asSelf_returns200() throws Exception {
        EmployeeRequest request = validRequest();
        when(employeeService.getById(1L)).thenReturn(responseFor(1L, request));

        mockMvc.perform(get("/api/employees/1")
                        .with(jwt().jwt(builder -> builder.claim("employee_id", "1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isOk());
    }

    @Test
    void getById_asDifferentEmployee_returns403() throws Exception {
        mockMvc.perform(get("/api/employees/1")
                        .with(jwt().jwt(builder -> builder.claim("employee_id", "2"))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isForbidden());
    }
}
