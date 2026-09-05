package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeEmploymentCategory;
import in.gov.jci.hrms.entity.EmploymentCategory;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Employee Directory filtering (search/roId/designationId/departmentId/employmentType/status) -
 * deletedAt IS NULL is deliberately not repeated here since Employee's own
 * {@code @SQLRestriction("deleted_at IS NULL")} already applies to every query Hibernate builds
 * against this entity, Specification-based ones included - adding it again here would be a no-op
 * duplicate WHERE clause.
 */
public final class EmployeeSpecification {

    /** status="SEPARATED" - every terminal, no-longer-serving EmployeeStatus. See EmployeeReleaseService for what actually flips an employee into one of these. */
    private static final Set<String> SEPARATED_STATUSES = Set.of("RETIRED", "RESIGNED", "DECEASED", "TERMINATED");

    private EmployeeSpecification() {
    }

    public static Specification<Employee> filterEmployees(String search, Long roId, Long designationId, Long departmentId,
                                                            EmploymentCategory employmentType, String status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("employeeCode")), pattern),
                        cb.like(cb.lower(root.get("fullName")), pattern)));
            }
            if (roId != null) {
                predicates.add(cb.equal(root.get("regionalOffice").get("id"), roId));
            }
            if (designationId != null) {
                predicates.add(cb.equal(root.get("designation").get("id"), designationId));
            }
            if (departmentId != null) {
                predicates.add(cb.equal(root.get("department").get("id"), departmentId));
            }
            if (employmentType != null) {
                Subquery<Long> categorySubquery = query.subquery(Long.class);
                var categoryRoot = categorySubquery.from(EmployeeEmploymentCategory.class);
                categorySubquery.select(categoryRoot.get("id"))
                        .where(cb.equal(categoryRoot.get("employee"), root),
                                cb.equal(categoryRoot.get("employmentCategory"), employmentType));
                predicates.add(cb.exists(categorySubquery));
            }
            String statusFilter = (status != null ? status : "ACTIVE").toUpperCase();
            if (statusFilter.equals("ALL")) {
                // No status predicate - Employee's own @SQLRestriction("deleted_at IS NULL") is still the only implicit filter.
            } else if (statusFilter.equals("SEPARATED")) {
                predicates.add(cb.upper(root.get("status").as(String.class)).in(SEPARATED_STATUSES));
            } else {
                predicates.add(cb.equal(cb.upper(root.get("status").as(String.class)), statusFilter));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
