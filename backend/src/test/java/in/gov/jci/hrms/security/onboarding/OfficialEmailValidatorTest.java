package in.gov.jci.hrms.security.onboarding;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** ONBOARDING_SECURITY_REQUIREMENTS.md exact-domain test matrix. */
class OfficialEmailValidatorTest {

    @Test
    void validJcimailAddress_isValid() {
        assertThat(OfficialEmailValidator.validate("employee@jcimail.in")).isEqualTo(OfficialEmailValidator.Result.VALID);
    }

    @Test
    void missingEmail_isMissing() {
        assertThat(OfficialEmailValidator.validate(null)).isEqualTo(OfficialEmailValidator.Result.MISSING);
        assertThat(OfficialEmailValidator.validate("")).isEqualTo(OfficialEmailValidator.Result.MISSING);
        assertThat(OfficialEmailValidator.validate("   ")).isEqualTo(OfficialEmailValidator.Result.MISSING);
    }

    @Test
    void malformedEmail_isMalformed() {
        assertThat(OfficialEmailValidator.validate("not-an-email")).isEqualTo(OfficialEmailValidator.Result.MALFORMED);
        assertThat(OfficialEmailValidator.validate("@jcimail.in")).isEqualTo(OfficialEmailValidator.Result.MALFORMED);
    }

    @Test
    void gmailAddress_isWrongDomain() {
        assertThat(OfficialEmailValidator.validate("employee@gmail.com")).isEqualTo(OfficialEmailValidator.Result.WRONG_DOMAIN);
    }

    @Test
    void govInAddress_isWrongDomain() {
        assertThat(OfficialEmailValidator.validate("employee@jci.gov.in")).isEqualTo(OfficialEmailValidator.Result.WRONG_DOMAIN);
    }

    @Test
    void subdomainOfJcimail_isWrongDomain_notSuffixMatched() {
        assertThat(OfficialEmailValidator.validate("employee@subdomain.jcimail.in")).isEqualTo(OfficialEmailValidator.Result.WRONG_DOMAIN);
    }

    @Test
    void lookalikeDomain_isWrongDomain() {
        assertThat(OfficialEmailValidator.validate("employee@fakejcimail.in")).isEqualTo(OfficialEmailValidator.Result.WRONG_DOMAIN);
    }

    @Test
    void jcimailFollowedByAttackerDomain_isWrongDomain_notSubstringMatched() {
        assertThat(OfficialEmailValidator.validate("employee@jcimail.in.evil.com")).isEqualTo(OfficialEmailValidator.Result.WRONG_DOMAIN);
    }

    @Test
    void mixedCaseAndWhitespace_normalizesAndValidates() {
        assertThat(OfficialEmailValidator.validate("  Employee@JCIMAIL.IN  ")).isEqualTo(OfficialEmailValidator.Result.VALID);
        assertThat(OfficialEmailValidator.normalize("  Employee@JCIMAIL.IN  ")).isEqualTo("employee@jcimail.in");
    }
}
