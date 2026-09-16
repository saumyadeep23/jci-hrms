package in.gov.jci.hrms.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import in.gov.jci.hrms.controller.DepartmentController;
import in.gov.jci.hrms.dto.DepartmentResponse;
import in.gov.jci.hrms.service.DepartmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SEC-001 regression coverage (docs/security/SEC_001_002_REMEDIATION.md,
 * spec section 12 items F/G/H): exercises the REAL SecurityConfig.jwtDecoder()
 * bean - unlike SecurityConfigTest.RoleMatrixTest, JwtDecoder is NOT
 * @MockBean-replaced here, so a malformed/expired/wrongly-signed bearer
 * token is actually rejected by Spring Security's own decoder/validator
 * pipeline, not by a stand-in. security.auth.mode defaults to "local-dev"
 * (application.yml) and no Spring profile is active in this test, so
 * SecurityConfig.jwtDecoder() builds the same HS256 decoder it always has
 * for local dev/CI/tests - this suite proves that decoder still correctly
 * rejects bad tokens after the SEC-001 fail-closed guard was added.
 */
@WebMvcTest(DepartmentController.class)
@Import(SecurityConfig.class)
class JwtAuthenticationSecurityTest {

    private static final String LOCAL_DEV_SECRET = AuthenticationModeGuard.LOCAL_DEV_DEFAULT_SECRET;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DepartmentService departmentService;

    @Test
    void validLocalDevToken_isAccepted() throws Exception {
        String token = signedToken(LOCAL_DEV_SECRET, Instant.now().plusSeconds(3600), List.of("HR_ADMIN"));
        when(departmentService.getById(1L)).thenReturn(
                new DepartmentResponse(1L, "ENG", "Engineering", null, Instant.now(), Instant.now(), null));

        mockMvc.perform(get("/api/departments/1").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void malformedToken_isRejectedWith401() throws Exception {
        mockMvc.perform(get("/api/departments/1").header("Authorization", "Bearer not-a-jwt-at-all"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredToken_isRejectedWith401() throws Exception {
        String token = signedToken(LOCAL_DEV_SECRET, Instant.now().minusSeconds(3600), List.of("HR_ADMIN"));

        mockMvc.perform(get("/api/departments/1").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongSignatureToken_isRejectedWith401() throws Exception {
        // Well-formed HS256 JWT, but signed with a key that is NOT the configured local-dev secret -
        // proves the decoder actually verifies the signature rather than trusting the token's shape.
        String token = signedToken("a-completely-different-secret-value-that-is-not-configured-anywhere",
                Instant.now().plusSeconds(3600), List.of("HR_ADMIN"));

        mockMvc.perform(get("/api/departments/1").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingBearerToken_isRejectedWith401() throws Exception {
        mockMvc.perform(get("/api/departments/1")).andExpect(status().isUnauthorized());
    }

    private static String signedToken(String secret, Instant expiry, List<String> roles) throws JOSEException {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("test-subject")
                .issueTime(Date.from(Instant.now().minusSeconds(60)))
                .expirationTime(Date.from(expiry))
                .claim("realm_access", Map.of("roles", roles))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
}
