package in.gov.jci.hrms.audit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

class AuditActorTest {

    @AfterEach
    void resetRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void currentUsername_withNoRequestContext_returnsNull() {
        assertThat(AuditActor.currentUsername()).isNull();
    }

    @Test
    void currentClientIp_withNoRequestContext_returnsNull() {
        assertThat(AuditActor.currentClientIp()).isNull();
    }

    @Test
    void currentUsername_withActingUserHeader_returnsHeaderValue() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Acting-User", "asha.rao");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(AuditActor.currentUsername()).isEqualTo("asha.rao");
    }

    @Test
    void currentUsername_withoutActingUserHeader_returnsNull() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(AuditActor.currentUsername()).isNull();
    }

    @Test
    void currentClientIp_returnsRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.20.30.40");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(AuditActor.currentClientIp()).isEqualTo("10.20.30.40");
    }
}
