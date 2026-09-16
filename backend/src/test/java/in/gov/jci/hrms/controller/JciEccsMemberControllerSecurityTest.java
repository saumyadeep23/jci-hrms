package in.gov.jci.hrms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.jci.hrms.dto.JciEccsMemberStatusChangeRequest;
import in.gov.jci.hrms.entity.JciEccsMembershipStatus;
import in.gov.jci.hrms.security.RbacSecurity;
import in.gov.jci.hrms.security.SecurityConfig;
import in.gov.jci.hrms.service.JciEccsMemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Non-financial RBAC migration (docs/security/RBAC_MIGRATION_REPORT.md): proves the legacy SUPER_ADMIN
 * god-role can no longer independently suspend/cease a JCIECCS membership - a business (JCIECCS
 * functional) action, not technical administration - while confirming both the retained legacy
 * COOP_ADMIN role and the new DB-backed JCIECCS_APPROVE permission each independently still work.
 */
@WebMvcTest(JciEccsMemberController.class)
@Import(SecurityConfig.class)
class JciEccsMemberControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JciEccsMemberService memberService;

    @MockBean(name = "rbac")
    private RbacSecurity rbac;

    private JciEccsMemberStatusChangeRequest request() {
        return new JciEccsMemberStatusChangeRequest(JciEccsMembershipStatus.SUSPENDED, LocalDate.now(), "test");
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void changeStatus_withOnlySuperAdminRoleAndNoPermission_returns403() throws Exception {
        when(rbac.hasPermission(any(), eq("JCIECCS_APPROVE"))).thenReturn(false);

        mockMvc.perform(put("/api/jcieccs/members/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "COOP_ADMIN")
    void changeStatus_withCoopAdminRole_isNotForbidden() throws Exception {
        mockMvc.perform(put("/api/jcieccs/members/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request())))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus()).isNotEqualTo(403));
    }

    @Test
    @WithMockUser(roles = "USER")
    void changeStatus_withNewRbacPermissionGranted_isNotForbidden() throws Exception {
        when(rbac.hasPermission(any(), eq("JCIECCS_APPROVE"))).thenReturn(true);

        mockMvc.perform(put("/api/jcieccs/members/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request())))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus()).isNotEqualTo(403));
    }

    @Test
    @WithMockUser(roles = "USER")
    void changeStatus_withoutCoopAdminRoleOrPermission_returns403() throws Exception {
        when(rbac.hasPermission(any(), eq("JCIECCS_APPROVE"))).thenReturn(false);

        mockMvc.perform(put("/api/jcieccs/members/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request())))
                .andExpect(status().isForbidden());
    }
}
