package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.DependencyCheckResponse;
import in.gov.jci.hrms.dto.DependencyUsage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Generic "does anything still actively reference this master record"
 * check - PIMS_SPEC.md Section 1.B's Referential Integrity & Dependency
 * Guard, reused by every master-data service's deactivate/dependencies
 * endpoints rather than duplicating one existsBy...() query per
 * relationship. table/column/label always come from each service's own
 * hardcoded DependencyProbe list (never user input), so building the SQL
 * by string concatenation here is safe - only the id value is a bind
 * parameter.
 */
@Service
public class MasterDependencyService {

    /** hasSoftDelete: whether to add "AND deleted_at IS NULL" so a soft-deleted referencing row doesn't count as active. */
    public record DependencyProbe(String table, String column, String label, boolean hasSoftDelete) {
    }

    private final JdbcTemplate jdbcTemplate;

    public MasterDependencyService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public DependencyCheckResponse check(List<DependencyProbe> probes, Object id) {
        List<DependencyUsage> usages = new ArrayList<>();
        for (DependencyProbe probe : probes) {
            String sql = "SELECT COUNT(*) FROM " + probe.table() + " WHERE " + probe.column() + " = ?"
                    + (probe.hasSoftDelete() ? " AND deleted_at IS NULL" : "");
            Long count = jdbcTemplate.queryForObject(sql, Long.class, id);
            if (count != null && count > 0) {
                usages.add(new DependencyUsage(probe.table(), probe.label(), count));
            }
        }
        return new DependencyCheckResponse(!usages.isEmpty(), usages);
    }
}
