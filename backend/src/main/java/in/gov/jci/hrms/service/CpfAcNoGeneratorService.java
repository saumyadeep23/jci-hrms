package in.gov.jci.hrms.service;

import in.gov.jci.hrms.repository.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fallback CPF A/C No generation for onboarding, mirroring
 * EmployeeCodeGeneratorService's pattern: a plain MAX+1 has no built-in
 * concurrency protection (a Java-level `synchronized` method wouldn't
 * either - it only serializes calls within one JVM instance, not across
 * the multiple ECS tasks this app can run as), so this takes a
 * session-scoped Postgres advisory lock first, in the same transaction,
 * before reading nextCpfAcNoNumber(). Only ever a suggested default -
 * EmployeeService.create() only calls this when the caller left cpfAcNo
 * blank; HR can always supply a real legacy PF ledger number instead.
 */
@Service
public class CpfAcNoGeneratorService {

    private final EmployeeRepository employeeRepository;

    public CpfAcNoGeneratorService(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    /**
     * REQUIRES_NEW so a caller's surrounding transaction rolling back
     * never rolls back the advisory lock/number allocation - same
     * "wasted number is fine, a reused one is not" reasoning as
     * EmployeeCodeGeneratorService.generateNext().
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String generateNext() {
        employeeRepository.acquireCpfAcNoGenerationLock();
        return String.valueOf(employeeRepository.nextCpfAcNoNumber());
    }
}
