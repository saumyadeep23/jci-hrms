package in.gov.jci.hrms.service;

import in.gov.jci.hrms.repository.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * PIMS_SPEC.md Feature 1: employee_code is the current highest numeric code
 * across all employees (active or soft-deleted) plus one, zero-padded to 4
 * digits (e.g. "0001", "0104", "1246") - not a DB sequence, since a plain
 * MAX+1 has no built-in concurrency protection, this takes a session-scoped
 * Postgres advisory lock first, in the same transaction, so two concurrent
 * onboarding finalizations can never compute the same next number.
 * cpf_ac_no is a separate concern entirely - it's a real, HR-entered
 * Contributory Provident Fund account number (client-supplied on
 * EmployeeRequest, same as panNumber - see V56 migration), not generated
 * here or by the DB.
 */
@Service
public class EmployeeCodeGeneratorService {

    private final EmployeeRepository employeeRepository;

    public EmployeeCodeGeneratorService(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    /**
     * REQUIRES_NEW so a caller's surrounding transaction rolling back (e.g.
     * an onboarding finalize that fails validation after the code was
     * minted) never rolls back the advisory lock/number allocation - a
     * "wasted" code number is fine, a reused one is not. The advisory lock
     * itself is released automatically when this transaction commits.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String generateNext() {
        employeeRepository.acquireEmployeeCodeGenerationLock();
        int nextNumber = employeeRepository.nextEmployeeCodeNumber();
        return "%04d".formatted(nextNumber);
    }
}
