package in.gov.jci.hrms.audit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SEC-008 fix (docs/security/MAKER_CHECKER_IMPLEMENTATION.md): AuditActor no
 * longer trusts the client-supplied X-Acting-User header (spoofable, a
 * pre-auth-era placeholder) - it now resolves the real Spring Security
 * principal.
 */
class AuditActorTest {

    @AfterEach
    void resetContext() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    @Test
    void currentUsername_withNoAuthentication_returnsNull() {
        assertThat(AuditActor.currentUsername()).isNull();
    }

    @Test
    void currentUsername_withAnonymousAuthentication_returnsNull() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymous", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        assertThat(AuditActor.currentUsername()).isNull();
    }

    @Test
    void currentUsername_withAuthenticatedJwtPrincipal_returnsTokenSubject() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("asha.rao")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_HR_ADMIN"))));

        assertThat(AuditActor.currentUsername()).isEqualTo("asha.rao");
    }

    /** A spoofed X-Acting-User header must never override (or substitute for) the real authenticated principal. */
    @Test
    void currentUsername_ignoresActingUserHeader_evenWhenAuthenticated() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Acting-User", "someone-else-entirely");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("real.principal")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_HR_ADMIN"))));

        assertThat(AuditActor.currentUsername()).isEqualTo("real.principal");
    }

    @Test
    void currentClientIp_withNoRequestContext_returnsNull() {
        assertThat(AuditActor.currentClientIp()).isNull();
    }

    @Test
    void currentClientIp_returnsRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.20.30.40");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(AuditActor.currentClientIp()).isEqualTo("10.20.30.40");
    }
}
