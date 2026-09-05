package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.ServiceBookEventResponse;
import in.gov.jci.hrms.security.EmployeeSecurity;
import in.gov.jci.hrms.security.SecurityConfig;
import in.gov.jci.hrms.service.LegacyMigrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ServiceBookController.class)
@Import({SecurityConfig.class, EmployeeSecurity.class})
class ServiceBookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LegacyMigrationService legacyMigrationService;

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void timeline_asHrAdmin_returns200() throws Exception {
        when(legacyMigrationService.getServiceBookTimeline(5L)).thenReturn(List.<ServiceBookEventResponse>of());

        mockMvc.perform(get("/api/service-book/5/timeline"))
                .andExpect(status().isOk());
    }

    @Test
    @WithAnonymousUser
    void timeline_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get("/api/service-book/5/timeline"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void timeline_asSelf_returns200() throws Exception {
        when(legacyMigrationService.getServiceBookTimeline(5L)).thenReturn(List.<ServiceBookEventResponse>of());

        mockMvc.perform(get("/api/service-book/5/timeline")
                        .with(jwt().jwt(builder -> builder.claim("employee_id", "5"))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isOk());
    }

    @Test
    void timeline_asDifferentEmployee_returns403() throws Exception {
        mockMvc.perform(get("/api/service-book/5/timeline")
                        .with(jwt().jwt(builder -> builder.claim("employee_id", "6"))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isForbidden());
    }
}
