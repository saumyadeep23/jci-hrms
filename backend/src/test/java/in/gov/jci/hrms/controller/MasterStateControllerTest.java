package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.entity.StateMaster;
import in.gov.jci.hrms.entity.StateType;
import in.gov.jci.hrms.repository.StateMasterRepository;
import in.gov.jci.hrms.security.SecurityConfig;
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

@WebMvcTest(MasterStateController.class)
@Import(SecurityConfig.class)
class MasterStateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StateMasterRepository stateMasterRepository;

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void list_asAnyAuthenticatedEmployee_returns200() throws Exception {
        when(stateMasterRepository.findByActiveTrueOrderByStateNameAsc())
                .thenReturn(List.of(new StateMaster("WB", "West Bengal", StateType.STATE, true)));

        mockMvc.perform(get("/api/v1/master/states"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stateCode").value("WB"))
                .andExpect(jsonPath("$[0].stateName").value("West Bengal"));
    }

    @Test
    @WithAnonymousUser
    void list_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/master/states"))
                .andExpect(status().isUnauthorized());
    }
}
