package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.PincodeLookupResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PincodeLookupServiceTest {

    private MockRestServiceServer mockServer;
    private PincodeLookupService pincodeLookupService;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        pincodeLookupService = new PincodeLookupService(builder);
    }

    @Test
    void lookup_withInvalidFormat_doesNotCallExternalApiAndReturnsNotFound() {
        PincodeLookupResponse response = pincodeLookupService.lookup("12AB");

        assertThat(response.found()).isFalse();
        assertThat(response.message()).containsIgnoringCase("invalid");
        mockServer.verify();
    }

    @Test
    void lookup_whenFound_returnsDistrictAndState() {
        mockServer.expect(requestTo("https://api.postalpincode.in/pincode/560001"))
                .andRespond(withSuccess("""
                        [
                          {
                            "Message": "Number of pincode(s) found:1",
                            "Status": "Success",
                            "PostOffice": [
                              {"Name": "Bangalore GPO", "District": "Bangalore", "State": "Karnataka"}
                            ]
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        PincodeLookupResponse response = pincodeLookupService.lookup("560001");

        assertThat(response.found()).isTrue();
        assertThat(response.district()).isEqualTo("Bangalore");
        assertThat(response.state()).isEqualTo("Karnataka");
        assertThat(response.postOffices()).containsExactly("Bangalore GPO");
        mockServer.verify();
    }

    @Test
    void lookup_whenMultiplePostOffices_returnsAllNamesForCityPicker() {
        mockServer.expect(requestTo("https://api.postalpincode.in/pincode/110001"))
                .andRespond(withSuccess("""
                        [
                          {
                            "Message": "Number of pincode(s) found:2",
                            "Status": "Success",
                            "PostOffice": [
                              {"Name": "Connaught Place", "District": "New Delhi", "State": "Delhi"},
                              {"Name": "Parliament House", "District": "New Delhi", "State": "Delhi"}
                            ]
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        PincodeLookupResponse response = pincodeLookupService.lookup("110001");

        assertThat(response.postOffices()).containsExactly("Connaught Place", "Parliament House");
        mockServer.verify();
    }

    @Test
    void lookup_whenNotFound_returnsGracefulNotFoundResponse() {
        mockServer.expect(requestTo("https://api.postalpincode.in/pincode/999999"))
                .andRespond(withSuccess("""
                        [
                          {"Message": "No records found", "Status": "Error", "PostOffice": null}
                        ]
                        """, MediaType.APPLICATION_JSON));

        PincodeLookupResponse response = pincodeLookupService.lookup("999999");

        assertThat(response.found()).isFalse();
        assertThat(response.district()).isNull();
        assertThat(response.message()).isNotBlank();
        mockServer.verify();
    }

    @Test
    void lookup_whenExternalApiFails_returnsGracefulErrorResponseInsteadOfThrowing() {
        mockServer.expect(requestTo("https://api.postalpincode.in/pincode/560001"))
                .andRespond(withServerError());

        PincodeLookupResponse response = pincodeLookupService.lookup("560001");

        assertThat(response.found()).isFalse();
        assertThat(response.message()).containsIgnoringCase("unavailable");
        mockServer.verify();
    }
}
