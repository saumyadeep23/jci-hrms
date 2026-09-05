package in.gov.jci.hrms.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import in.gov.jci.hrms.dto.PincodeLookupResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.regex.Pattern;

/**
 * FR-EMP.14: proxies the public India Post pincode lookup so the frontend
 * doesn't call a third-party API directly. Never throws - always returns a
 * PincodeLookupResponse with found=false and a message on any failure, so a
 * lookup outage never blocks manual entry of district/state.
 */
@Service
public class PincodeLookupService {

    private static final Logger log = LoggerFactory.getLogger(PincodeLookupService.class);
    private static final Pattern PINCODE_PATTERN = Pattern.compile("^\\d{6}$");

    /**
     * api.postalpincode.in sits behind a WAF/CDN that outright drops
     * connections carrying a generic library User-Agent (verified: the JDK
     * HttpClient default "Java/21.x" gets connection-reset, not even a
     * clean 403) - a browser-shaped UA is required for the request to land
     * at all.
     */
    private static final String BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final RestClient restClient;

    public PincodeLookupService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
                .baseUrl("https://api.postalpincode.in")
                .defaultHeader(HttpHeaders.USER_AGENT, BROWSER_USER_AGENT)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Cached (see application.yml spring.cache.caffeine.spec) keyed by pincode.
     * "unless" skips caching not-found/error responses, so a transient upstream
     * outage or a not-yet-assigned pincode doesn't get stuck negative for the TTL.
     */
    @Cacheable(value = "pincodeCache", key = "#pincode", unless = "#result == null || !#result.found()")
    public PincodeLookupResponse lookup(String pincode) {
        if (!PINCODE_PATTERN.matcher(pincode).matches()) {
            return new PincodeLookupResponse(pincode, false, null, null,
                    "Invalid pincode format; please enter district/state manually.", List.of());
        }

        try {
            List<PostalApiEntry> entries = restClient.get()
                    .uri("/pincode/{pincode}", pincode)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            if (entries == null || entries.isEmpty()) {
                return notFound(pincode);
            }

            PostalApiEntry entry = entries.get(0);
            if (!"Success".equalsIgnoreCase(entry.status()) || entry.postOffice() == null || entry.postOffice().isEmpty()) {
                return notFound(pincode);
            }

            PostalApiEntry.PostOffice postOffice = entry.postOffice().get(0);
            List<String> postOfficeNames = entry.postOffice().stream()
                    .map(PostalApiEntry.PostOffice::name)
                    .filter(name -> name != null && !name.isBlank())
                    .toList();
            return new PincodeLookupResponse(pincode, true, postOffice.district(), postOffice.state(), null, postOfficeNames);
        } catch (RestClientException ex) {
            log.warn("Pincode lookup failed for {}: {}", pincode, ex.getMessage());
            return new PincodeLookupResponse(pincode, false, null, null,
                    "Pincode lookup service unavailable; please enter district/state manually.", List.of());
        }
    }

    private PincodeLookupResponse notFound(String pincode) {
        return new PincodeLookupResponse(pincode, false, null, null,
                "No records found for this pincode; please enter district/state manually.", List.of());
    }

    private record PostalApiEntry(
            @JsonProperty("Status") String status,
            @JsonProperty("Message") String message,
            @JsonProperty("PostOffice") List<PostOffice> postOffice
    ) {
        private record PostOffice(
                @JsonProperty("Name") String name,
                @JsonProperty("District") String district,
                @JsonProperty("State") String state
        ) {
        }
    }
}
