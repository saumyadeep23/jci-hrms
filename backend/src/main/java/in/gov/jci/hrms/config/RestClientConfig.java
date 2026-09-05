package in.gov.jci.hrms.config;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

/**
 * Applies connect/read timeouts to the auto-configured RestClient.Builder
 * bean used by the FR-EMP.14 lookup proxies, so a slow upstream (Pincode/
 * IFSC API) fails fast instead of hanging a request thread. Kept separate
 * from PincodeLookupService/IfscLookupService (rather than each calling
 * .requestFactory() itself) so those services stay test-friendly: a test
 * that binds its own RestClient.Builder to a MockRestServiceServer never
 * goes through this customizer, so the mock's request factory isn't
 * clobbered.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClientCustomizer lookupTimeoutCustomizer() {
        return builder -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(3000);
            factory.setReadTimeout(3000);
            builder.requestFactory(factory);
        };
    }
}
