package in.gov.jci.hrms.security.onboarding;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class InvitationTokenServiceTest {

    private final InvitationTokenService service = new InvitationTokenService();

    @Test
    void generateRawToken_producesAtLeast256BitsOfEntropy() {
        String token = service.generateRawToken();
        byte[] decoded = Base64.getUrlDecoder().decode(token);
        assertThat(decoded.length).isGreaterThanOrEqualTo(32);
    }

    @Test
    void generateRawToken_isDifferentEveryCall() {
        assertThat(service.generateRawToken()).isNotEqualTo(service.generateRawToken());
    }

    @Test
    void hash_isDeterministicForTheSameInput() {
        String token = service.generateRawToken();
        assertThat(service.hash(token)).isEqualTo(service.hash(token));
    }

    @Test
    void hash_differsForDifferentTokens() {
        assertThat(service.hash(service.generateRawToken())).isNotEqualTo(service.hash(service.generateRawToken()));
    }

    @Test
    void hash_neverEqualsTheRawToken() {
        String token = service.generateRawToken();
        assertThat(service.hash(token)).isNotEqualTo(token);
    }
}
