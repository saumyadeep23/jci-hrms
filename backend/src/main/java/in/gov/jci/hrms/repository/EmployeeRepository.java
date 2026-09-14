package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {

    /**
     * Eagerly loads the ManyToOne master-data associations so
     * EmployeeResponse.from() doesn't trigger a lazy-load query per
     * association per row (N+1) when building the response.
     */
    @Override
    @EntityGraph(attributePaths = {"department", "designation", "regionalOffice", "departmentalPurchaseCentre"})
    Optional<Employee> findById(Long id);

    @Override
    @EntityGraph(attributePaths = {"department", "designation", "regionalOffice", "departmentalPurchaseCentre"})
    Page<Employee> findAll(Pageable pageable);

    /** Employee Directory's filtered/paged listing (EmployeeSpecification) - same eager-fetch treatment as the plain findAll(Pageable) above. */
    @Override
    @EntityGraph(attributePaths = {"department", "designation", "regionalOffice", "departmentalPurchaseCentre"})
    Page<Employee> findAll(@NonNull Specification<Employee> spec, @NonNull Pageable pageable);

    Optional<Employee> findByEmployeeCode(String employeeCode);

    Optional<Employee> findByPersonalEmail(String personalEmail);

    boolean existsByRegionalOfficeId(Long regionalOfficeId);

    boolean existsByDepartmentalPurchaseCentreId(Long dpcId);

    boolean existsByDepartmentId(Long departmentId);

    boolean existsByDesignationId(Long designationId);

    /** NPS Declaration Desk's HR admin "Pending / Not Submitted" tab - every active, NPS-eligible employee, before filtering out who already declared for the selected FY. */
    @EntityGraph(attributePaths = {"regionalOffice", "departmentalPurchaseCentre"})
    List<Employee> findByNpsEligibleTrueAndStatus(EmployeeStatus status);

    /** PayrollBatchComputationService.processBatch()'s "active employees eligible for payroll" source set - see its javadoc for why status alone (plus a current RegularPayFixation, checked per-employee) is the eligibility gate. */
    @EntityGraph(attributePaths = {"regionalOffice", "designation", "department"})
    List<Employee> findByStatus(EmployeeStatus status);

    /** Batch-fetch for JciEccsMemberService (JciEccsMember only stores a plain employee_id Long, no
     * JPA association) - eager RO/DPC avoids N+1 when resolving each member's place-of-posting string. */
    @EntityGraph(attributePaths = {"regionalOffice", "departmentalPurchaseCentre"})
    List<Employee> findByIdIn(Collection<Long> ids);

    /**
     * PIMS_SPEC.md Feature 1's exact "current highest numeric employee_code
     * + 1" algorithm - deliberately scans ALL rows (soft-deleted included,
     * matching the spec's literal query), not just active ones, so a
     * terminated employee's number is never reissued.
     */
    @Query(value = "SELECT COALESCE(MAX(SUBSTRING(employee_code FROM '[0-9]+')::INTEGER), 0) + 1 "
            + "FROM employees WHERE employee_code ~ '^[0-9]+$'", nativeQuery = true)
    int nextEmployeeCodeNumber();

    /**
     * Session-scoped advisory lock serializing concurrent employee_code
     * allocations - MAX+1 has no natural concurrency protection on its own
     * (unlike a DB sequence), so EmployeeCodeGeneratorService takes this
     * lock first, inside the same short-lived transaction, before reading
     * nextEmployeeCodeNumber(). Released automatically at transaction end.
     */
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(778899001)) lock_acquired", nativeQuery = true)
    int acquireEmployeeCodeGenerationLock();

    /**
     * Fallback "next" CPF A/C No suggestion for onboarding - mirrors
     * nextEmployeeCodeNumber()'s MAX+1-over-regex approach, including
     * scanning ALL rows rather than filtering deleted_at IS NULL: a
     * terminated employee's number should never be reissued, doubly so
     * here since cpf_ac_no is a real external PF ledger account number,
     * not just an internal HRMS id. Only numeric-looking values are
     * considered (the '^[0-9]+$' filter runs before the CAST, so a legacy
     * non-numeric register entry like "PF/1001" can never throw a cast
     * exception or skew the max) - this is only a suggestion HR is always
     * free to override with any real legacy number, not an authoritative
     * allocator like employee_code.
     */
    @Query(value = "SELECT COALESCE(MAX(CAST(cpf_ac_no AS BIGINT)), 0) + 1 FROM employees WHERE cpf_ac_no ~ '^[0-9]+$'", nativeQuery = true)
    long nextCpfAcNoNumber();

    /**
     * Session-scoped advisory lock serializing concurrent cpf_ac_no
     * allocations - same MAX+1-has-no-natural-concurrency-protection
     * rationale as acquireEmployeeCodeGenerationLock(), with its own lock
     * key so the two generators never needlessly contend with each other.
     */
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(778899002)) lock_acquired", nativeQuery = true)
    int acquireCpfAcNoGenerationLock();
}
