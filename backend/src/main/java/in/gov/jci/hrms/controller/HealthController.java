package in.gov.jci.hrms.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Simple ping endpoint separate from Actuator's /actuator/health,
 * useful for a quick manual sanity check after a deploy.
 */
@RestController
public class HealthController {

    @GetMapping("/api/ping")
    public Map<String, Object> ping() {
        return Map.of(
                "status", "ok",
                "service", "jci-hrms-backend",
                "timestamp", Instant.now().toString()
        );
    }
}
