package in.gov.jci.hrms.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Default EmailService: logs at INFO instead of sending, since no SMTP/production mail provider
 * is configured for this app yet. Never logs the email body (which, for onboarding, may carry an
 * activation link containing a query-string reference - logging it would defeat
 * ONBOARDING_SECURITY_REQUIREMENTS.md's token-never-logged requirement, even though the raw token
 * itself is never passed to this class - only the caller's already-built subject/body are, so
 * this stays a hard rule here too, not just relying on the caller's discipline). Swap this bean
 * for a real SMTP/SES-backed implementation once a provider exists - no credentials are invented
 * here.
 */
@Service
public class LoggingEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailService.class);

    @Override
    public void send(String toAddress, String subject, String body) {
        log.info("Email suppressed (no mail provider configured) - to={}, subject={}", toAddress, subject);
    }
}
