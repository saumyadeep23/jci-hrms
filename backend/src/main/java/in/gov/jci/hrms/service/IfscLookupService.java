package in.gov.jci.hrms.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import in.gov.jci.hrms.dto.IfscLookupResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.regex.Pattern;

/**
 * FR-EMP.14: proxies the Razorpay IFSC lookup so the frontend doesn't call a
 * third-party API directly. Never throws - always returns an
 * IfscLookupResponse with found=false and a message on any failure, so a
 * lookup outage never blocks manual entry of bank name/branch.
 */
@Service
public class IfscLookupService {

    private static final Logger log = LoggerFactory.getLogger(IfscLookupService.class);
    private static final Pattern IFSC_PATTERN = Pattern.compile("^[A-Z]{4}0[A-Z0-9]{6}$");

    private final RestClient restClient;

    public IfscLookupService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
                .baseUrl("https://ifsc.razorpay.com")
                .build();
    }

    /**
     * Cached (see application.yml spring.cache.caffeine.spec) keyed by the
     * normalized (uppercased) IFSC. "unless" skips caching not-found/error
     * responses, so a transient upstream outage doesn't get stuck negative
     * for the TTL.
     */
    @Cacheable(value = "ifscCache", key = "#ifsc == null ? '' : #ifsc.toUpperCase()", unless = "#result == null || !#result.found()")
    public IfscLookupResponse lookup(String ifsc) {
        String normalized = ifsc == null ? "" : ifsc.toUpperCase();
        if (!IFSC_PATTERN.matcher(normalized).matches()) {
            return new IfscLookupResponse(ifsc, false, null, null,
                    "Invalid IFSC format; please enter bank/branch manually.");
        }

        try {
            IfscApiResponse response = restClient.get()
                    .uri("/{ifsc}", normalized)
                    .retrieve()
                    .body(IfscApiResponse.class);

            if (response == null || response.bank() == null) {
                return notFound(ifsc);
            }
            return new IfscLookupResponse(ifsc, true, response.bank(), response.branch(), null);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                return notFound(ifsc);
            }
            log.warn("IFSC lookup failed for {}: {}", ifsc, ex.getMessage());
            return unavailable(ifsc);
        } catch (RestClientException ex) {
            log.warn("IFSC lookup failed for {}: {}", ifsc, ex.getMessage());
            return unavailable(ifsc);
        }
    }

    private IfscLookupResponse notFound(String ifsc) {
        return new IfscLookupResponse(ifsc, false, null, null,
                "No records found for this IFSC code; please enter bank/branch manually.");
    }

    private IfscLookupResponse unavailable(String ifsc) {
        return new IfscLookupResponse(ifsc, false, null, null,
                "IFSC lookup service unavailable; please enter bank/branch manually.");
    }

    private record IfscApiResponse(
            @JsonProperty("BANK") String bank,
            @JsonProperty("BRANCH") String branch
    ) {
    }
}
