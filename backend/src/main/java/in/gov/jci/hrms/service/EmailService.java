package in.gov.jci.hrms.service;

/**
 * Abstraction boundary (ONBOARDING_SECURITY_REQUIREMENTS.md instruction 34) so onboarding/domain
 * logic never embeds SMTP logic. No production mail provider is configured for this app yet
 * (CLAUDE.md); LoggingEmailService is the safe default implementation until one is - no SMTP
 * credentials are invented anywhere in this codebase.
 */
public interface EmailService {

    void send(String toAddress, String subject, String body);
}
