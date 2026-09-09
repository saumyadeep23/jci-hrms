package in.gov.jci.hrms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.jci.hrms.dto.HolidayCalendarResponse;
import in.gov.jci.hrms.dto.HolidayRequest;
import in.gov.jci.hrms.dto.HolidayResponse;
import in.gov.jci.hrms.entity.HolidayType;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.security.SecurityConfig;
import in.gov.jci.hrms.service.HolidayService;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HolidayController.class)
@Import(SecurityConfig.class)
class HolidayControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private HolidayService holidayService;

    private HolidayRequest validRequest() {
        return new HolidayRequest(LocalDate.of(2026, 1, 26), "Republic Day", HolidayType.GAZETTED, null);
    }

    private HolidayResponse responseFor(Long id, HolidayRequest request) {
        Instant now = Instant.now();
        return new HolidayResponse(id, request.holidayDate(), request.name(), request.holidayType(), request.state(), now, now);
    }

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void create_asHrAdmin_returns201WithLocationHeader() throws Exception {
        HolidayRequest request = validRequest();
        when(holidayService.create(any(HolidayRequest.class))).thenReturn(responseFor(1L, request));

        mockMvc.perform(post("/api/holidays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/holidays/1"))
                .andExpect(jsonPath("$.name").value("Republic Day"));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void create_asEmployee_returns403() throws Exception {
        mockMvc.perform(post("/api/holidays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void create_whenDateAlreadyTaken_returns409() throws Exception {
        when(holidayService.create(any(HolidayRequest.class)))
                .thenThrow(new MasterDataConflictException("Holiday already exists for date: 2026-01-26"));

        mockMvc.perform(post("/api/holidays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getById_asEmployee_returns200() throws Exception {
        when(holidayService.getById(1L)).thenReturn(responseFor(1L, validRequest()));

        mockMvc.perform(get("/api/holidays/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Republic Day"));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getById_whenMissing_returns404() throws Exception {
        when(holidayService.getById(99L)).thenThrow(new MasterDataNotFoundException("Holiday", 99L));

        mockMvc.perform(get("/api/holidays/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    @WithAnonymousUser
    void getById_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get("/api/holidays/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMyCalendar_resolvesEmployeeFromJwtClaim_returns200() throws Exception {
        HolidayCalendarResponse response = new HolidayCalendarResponse(
                "RO - Kolkata RO, West Bengal", "West Bengal",
                List.of(responseFor(1L, validRequest())),
                LocalDate.of(2026, 1, 26), "Republic Day");
        when(holidayService.getMyCalendar(eq(7L), eq(2026), eq(1))).thenReturn(response);

        mockMvc.perform(get("/api/holidays/my-calendar").param("year", "2026").param("month", "1")
                        .with(jwt().jwt(builder -> builder.claim("employee_id", "7"))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.officeLabel").value("RO - Kolkata RO, West Bengal"))
                .andExpect(jsonPath("$.upcomingHolidayName").value("Republic Day"));
    }

    @Test
    @WithAnonymousUser
    void getMyCalendar_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get("/api/holidays/my-calendar").param("year", "2026").param("month", "1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMyRestrictedHolidays_resolvesEmployeeFromJwtClaim_returns200() throws Exception {
        HolidayResponse rh = new HolidayResponse(2L, LocalDate.of(2026, 9, 14), "Ganesh Chaturthi",
                HolidayType.RESTRICTED, "West Bengal", Instant.now(), Instant.now());
        when(holidayService.getMyRestrictedHolidays(eq(7L), eq(2026))).thenReturn(List.of(rh));

        mockMvc.perform(get("/api/holidays/my-restricted").param("year", "2026")
                        .with(jwt().jwt(builder -> builder.claim("employee_id", "7"))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Ganesh Chaturthi"));
    }

    @Test
    @WithAnonymousUser
    void getMyRestrictedHolidays_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get("/api/holidays/my-restricted").param("year", "2026"))
                .andExpect(status().isUnauthorized());
    }
}
