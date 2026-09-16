package in.gov.jci.hrms.security.onboarding;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/** Thin startup wiring for SystemAdminBootstrapService - kept separate so the service itself stays unit-testable without a Spring context. */
@Component
public class SystemAdminBootstrapRunner implements CommandLineRunner {

    private final SystemAdminBootstrapService bootstrapService;
    private final long bootstrapEmployeeId;

    public SystemAdminBootstrapRunner(SystemAdminBootstrapService bootstrapService,
                                       @Value("${hrms.bootstrap.system-admin-employee-id:8}") long bootstrapEmployeeId) {
        this.bootstrapService = bootstrapService;
        this.bootstrapEmployeeId = bootstrapEmployeeId;
    }

    @Override
    public void run(String... args) {
        bootstrapService.ensureBootstrapAdmin(bootstrapEmployeeId);
    }
}
