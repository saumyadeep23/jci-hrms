package in.gov.jci.hrms.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Enables the Caffeine-backed "pincodeCache" cache (see application.yml
 * spring.cache.caffeine.spec) used by PincodeLookupService, FR-EMP.14.
 */
@Configuration
@EnableCaching
public class CacheConfig {
}
