package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.PincodeLookupResponse;
import in.gov.jci.hrms.security.SecurityConfig;
import in.gov.jci.hrms.service.PincodeLookupService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PIMS_SPEC.md route alias for LookupController's /api/lookups/pincode - see GeoLookupController. */
@WebMvcTest(GeoLookupController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "EMPLOYEE")
class GeoLookupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PincodeLookupService pincodeLookupService;

    @Test
    void pincode_whenFound_returns200WithDropdownReadyPostOffices() throws Exception {
        when(pincodeLookupService.lookup("560001"))
                .thenReturn(new PincodeLookupResponse("560001", true, "Bangalore", "Karnataka", null,
                        List.of("Bangalore GPO", "Bangalore City")));

        mockMvc.perform(get("/api/v1/geo/pincode/560001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.state").value("Karnataka"))
                .andExpect(jsonPath("$.district").value("Bangalore"))
                .andExpect(jsonPath("$.postOffices[0]").value("Bangalore GPO"))
                .andExpect(jsonPath("$.postOffices[1]").value("Bangalore City"));
    }

    @Test
    void pincode_whenLookupFails_stillReturns200WithFoundFalse() throws Exception {
        when(pincodeLookupService.lookup("999999"))
                .thenReturn(new PincodeLookupResponse("999999", false, null, null, "No records found", List.of()));

        mockMvc.perform(get("/api/v1/geo/pincode/999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(false));
    }

    @Test
    @WithAnonymousUser
    void pincode_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/geo/pincode/560001"))
                .andExpect(status().isUnauthorized());
    }
}
