package in.gov.jci.hrms.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * There's no authentication system in this app yet, so there's no real
 * principal to attribute audit events to. Until one exists, the acting
 * user is read from an X-Acting-User header if the caller sets one, and
 * left null otherwise - a placeholder, not a security control. Revisit
 * this once auth (see aws-auth / Spring Security) is wired in and a real
 * authenticated principal is available.
 */
final class AuditActor {

    private static final String ACTING_USER_HEADER = "X-Acting-User";

    private AuditActor() {
    }

    static String currentUsername() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        String header = request.getHeader(ACTING_USER_HEADER);
        return (header != null && !header.isBlank()) ? header : null;
    }

    static String currentClientIp() {
        HttpServletRequest request = currentRequest();
        return request != null ? request.getRemoteAddr() : null;
    }

    private static HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        return null;
    }
}
