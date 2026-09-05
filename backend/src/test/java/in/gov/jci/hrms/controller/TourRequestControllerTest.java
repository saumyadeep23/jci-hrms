package in.gov.jci.hrms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.jci.hrms.dto.TourRequestRequest;
import in.gov.jci.hrms.dto.TourRequestResponse;
import in.gov.jci.hrms.entity.TourRequestStatus;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.security.SecurityConfig;
import in.gov.jci.hrms.service.TourRequestService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TourRequestController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "EMPLOYEE")
class TourRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TourRequestService tourRequestService;

    private TourRequestRequest validRequest() {
        return new TourRequestRequest("TR-2026-001", 1L, "Client visit", "Delhi", "Mumbai",
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 3), false, null, null, null);
    }

    private TourRequestResponse responseFor(Long id, TourRequestRequest request) {
        Instant now = Instant.now();
        return new TourRequestResponse(id, request.requestNumber(), request.employeeId(), "EMP-001",
                request.purpose(), request.origin(), request.destination(), request.startDate(), request.endDate(),
                request.isPostFacto(), TourRequestStatus.DRAFT, request.directFlightCost(), request.directHotelCost(),
                request.directVehicleCost(), now, now);
    }

    @Test
    void create_withValidRequest_returns201() throws Exception {
        TourRequestRequest request = validRequest();
        when(tourRequestService.create(any(TourRequestRequest.class))).thenReturn(responseFor(1L, request));

        mockMvc.perform(post("/api/reimbursements/tours")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/reimbursements/tours/1"))
                .andExpect(jsonPath("$.requestNumber").value("TR-2026-001"));
    }

    @Test
    void create_withMissingRequiredFields_returns400() throws Exception {
        TourRequestRequest invalid = new TourRequestRequest("", null, "", "", "", null, null, false, null, null, null);

        mockMvc.perform(post("/api/reimbursements/tours")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_whenEmployeeMissing_returns404() throws Exception {
        TourRequestRequest request = validRequest();
        when(tourRequestService.create(any(TourRequestRequest.class))).thenThrow(new EmployeeNotFoundException(1L));

        mockMvc.perform(post("/api/reimbursements/tours")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getById_returns200() throws Exception {
        TourRequestRequest request = validRequest();
        when(tourRequestService.getById(1L)).thenReturn(responseFor(1L, request));

        mockMvc.perform(get("/api/reimbursements/tours/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithAnonymousUser
    void getById_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get("/api/reimbursements/tours/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CPF_ADMIN")
    void create_withWrongRole_returns403() throws Exception {
        mockMvc.perform(post("/api/reimbursements/tours")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }
}
