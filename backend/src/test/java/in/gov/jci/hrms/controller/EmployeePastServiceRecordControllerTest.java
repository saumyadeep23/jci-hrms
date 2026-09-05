package in.gov.jci.hrms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.jci.hrms.dto.PastServiceRecordRequest;
import in.gov.jci.hrms.dto.PastServiceRecordResponse;
import in.gov.jci.hrms.entity.PastServiceOrganizationType;
import in.gov.jci.hrms.exception.MasterDataValidationException;
import in.gov.jci.hrms.security.SecurityConfig;
import in.gov.jci.hrms.service.EmployeePastServiceRecordService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EmployeePastServiceRecordController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "HR_ADMIN")
class EmployeePastServiceRecordControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EmployeePastServiceRecordService pastServiceRecordService;

    private PastServiceRecordRequest validRequest() {
        return new PastServiceRecordRequest("Tata Consultancy Services", PastServiceOrganizationType.PRIVATE_SECTOR,
                "Software Engineer", LocalDate.of(2018, 6, 1), LocalDate.of(2023, 12, 31),
                null, null, null, false, null, "Better opportunity", null, "TCS-NOC-991");
    }

    private PastServiceRecordResponse responseFor(Long id, PastServiceRecordRequest request) {
        return new PastServiceRecordResponse(id, 1L, request.organizationName(), request.organizationType(),
                request.designationHeld(), request.fromDate(), request.toDate(), null,
                request.lastPayScalePattern(), request.lastDrawnBasic(), request.lastDrawnGross(),
                request.qualifyingForPensionGratuity(), request.qualifyingServiceOrderRef(), request.reasonForLeaving(),
                request.experienceCertificateS3Key(), request.relievingNocDocumentS3Key(), false, null, null);
    }

    @Test
    void create_withValidRequest_returns201WithLocationHeader() throws Exception {
        PastServiceRecordRequest request = validRequest();
        when(pastServiceRecordService.create(eq(1L), any(PastServiceRecordRequest.class))).thenReturn(responseFor(3L, request));

        mockMvc.perform(post("/api/employees/1/past-service-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/employees/1/past-service-records/3"))
                .andExpect(jsonPath("$.organizationName").value("Tata Consultancy Services"));
    }

    @Test
    void create_whenDatesInvalid_returns400() throws Exception {
        when(pastServiceRecordService.create(eq(1L), any(PastServiceRecordRequest.class)))
                .thenThrow(new MasterDataValidationException("toDate must not be before fromDate"));

        mockMvc.perform(post("/api/employees/1/past-service-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/employees/1/past-service-records/3"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithAnonymousUser
    void list_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get("/api/employees/1/past-service-records"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void create_withWrongRole_returns403() throws Exception {
        mockMvc.perform(post("/api/employees/1/past-service-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }
}
