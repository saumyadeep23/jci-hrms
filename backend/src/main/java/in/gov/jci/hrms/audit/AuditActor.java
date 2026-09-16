package in.gov.jci.hrms.audit;

import in.gov.jci.hrms.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * SEC-008 fix (docs/security/MAKER_CHECKER_IMPLEMENTATION.md): this
 * previously read an X-Acting-User HTTP header - a pre-auth-era placeholder
 * from before Spring Security/JWT auth existed in this app, and trivially
 * spoofable by any caller. Now that every request is authenticated
 * (SecurityConfig), the real Spring Security principal is authoritative;
 * the header is never consulted for a security-attributed audit record.
 * Returns null only when there is genuinely no authenticated principal in
 * context (e.g. a background/bootstrap task running outside a request).
 */
final class AuditActor {

    private AuditActor() {
    }

    static String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return SecurityUtils.currentUsername(authentication);
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
