package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.IfscLookupResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class IfscLookupServiceTest {

    private MockRestServiceServer mockServer;
    private IfscLookupService ifscLookupService;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        ifscLookupService = new IfscLookupService(builder);
    }

    @Test
    void lookup_withInvalidFormat_doesNotCallExternalApiAndReturnsNotFound() {
        IfscLookupResponse response = ifscLookupService.lookup("BAD");

        assertThat(response.found()).isFalse();
        assertThat(response.message()).containsIgnoringCase("invalid");
        mockServer.verify();
    }

    @Test
    void lookup_whenFound_returnsBankAndBranch() {
        mockServer.expect(requestTo("https://ifsc.razorpay.com/SBIN0000001"))
                .andRespond(withSuccess("""
                        {"BANK": "State Bank of India", "BRANCH": "Mumbai Main"}
                        """, MediaType.APPLICATION_JSON));

        IfscLookupResponse response = ifscLookupService.lookup("SBIN0000001");

        assertThat(response.found()).isTrue();
        assertThat(response.bankName()).isEqualTo("State Bank of India");
        assertThat(response.branch()).isEqualTo("Mumbai Main");
        mockServer.verify();
    }

    @Test
    void lookup_whenApiReturns404_returnsGracefulNotFoundResponse() {
        mockServer.expect(requestTo("https://ifsc.razorpay.com/AAAA0000001"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        IfscLookupResponse response = ifscLookupService.lookup("AAAA0000001");

        assertThat(response.found()).isFalse();
        assertThat(response.message()).isNotBlank();
        mockServer.verify();
    }

    @Test
    void lookup_whenExternalApiFails_returnsGracefulErrorResponseInsteadOfThrowing() {
        mockServer.expect(requestTo("https://ifsc.razorpay.com/SBIN0000001"))
                .andRespond(withServerError());

        IfscLookupResponse response = ifscLookupService.lookup("SBIN0000001");

        assertThat(response.found()).isFalse();
        assertThat(response.message()).containsIgnoringCase("unavailable");
        mockServer.verify();
    }
}
