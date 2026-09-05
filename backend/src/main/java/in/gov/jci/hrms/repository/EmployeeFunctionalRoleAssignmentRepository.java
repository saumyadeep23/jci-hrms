package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.EmployeeFunctionalRoleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface EmployeeFunctionalRoleAssignmentRepository extends JpaRepository<EmployeeFunctionalRoleAssignment, UUID> {

    /** DoaResolverService/SupervisorResolutionService HOD lookup - the active assignment(s) of roleCode for a department, as of targetDate. */
    @Query("SELECT a FROM EmployeeFunctionalRoleAssignment a WHERE a.role.roleCode = :roleCode "
            + "AND a.department.id = :departmentId AND a.active = true "
            + "AND a.validFrom <= :targetDate AND (a.validTo IS NULL OR a.validTo >= :targetDate)")
    List<EmployeeFunctionalRoleAssignment> findActiveByRoleCodeAndDepartment(@Param("roleCode") String roleCode,
                                                                              @Param("departmentId") Long departmentId,
                                                                              @Param("targetDate") LocalDate targetDate);

    @Query("SELECT a FROM EmployeeFunctionalRoleAssignment a WHERE a.role.roleCode = :roleCode "
            + "AND a.office.id = :officeId AND a.active = true "
            + "AND a.validFrom <= :targetDate AND (a.validTo IS NULL OR a.validTo >= :targetDate)")
    List<EmployeeFunctionalRoleAssignment> findActiveByRoleCodeAndOffice(@Param("roleCode") String roleCode,
                                                                          @Param("officeId") Long officeId,
                                                                          @Param("targetDate") LocalDate targetDate);

    @Query("SELECT a FROM EmployeeFunctionalRoleAssignment a WHERE a.employee.id = :employeeId AND a.active = true "
            + "AND a.validFrom <= :targetDate AND (a.validTo IS NULL OR a.validTo >= :targetDate)")
    List<EmployeeFunctionalRoleAssignment> findActiveByEmployeeId(@Param("employeeId") Long employeeId,
                                                                   @Param("targetDate") LocalDate targetDate);

    /** GET /api/v1/master/functional-roles/assignments - each filter applied only when non-null; activeOnly is never null (controller defaults it). */
    @Query("SELECT a FROM EmployeeFunctionalRoleAssignment a WHERE "
            + "(:roleCode IS NULL OR a.role.roleCode = :roleCode) AND "
            + "(:departmentId IS NULL OR a.department.id = :departmentId) AND "
            + "(:officeId IS NULL OR a.office.id = :officeId) AND "
            + "(:employeeId IS NULL OR a.employee.id = :employeeId) AND "
            + "(:activeOnly = false OR a.active = true) "
            + "ORDER BY a.validFrom DESC")
    List<EmployeeFunctionalRoleAssignment> search(@Param("roleCode") String roleCode,
                                                   @Param("departmentId") Long departmentId,
                                                   @Param("officeId") Long officeId,
                                                   @Param("employeeId") Long employeeId,
                                                   @Param("activeOnly") boolean activeOnly);
}
