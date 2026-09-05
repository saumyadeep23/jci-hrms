package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * fromDate/toDate are cast explicitly because PostgreSQL can't otherwise infer a bind
     * parameter's type from a bare "? IS NULL" check alone (no other expression at that specific
     * parameter position gives it a type to unify with) - passing a null fromDate/toDate (the
     * dashboard's unfiltered GET /api/audit-logs?size=1 call always does) then fails at the JDBC
     * level with "ERROR: could not determine data type of parameter $N", surfacing as a 500.
     * entityName/entityId/performedBy don't need the same cast: Hibernate already binds their
     * null checks with an explicit JDBC type (VARCHAR/BIGINT) since those are simple, unambiguous
     * Java types, unlike Instant.
     */
    @Query("SELECT a FROM AuditLog a WHERE "
            + "(CAST(:fromDate AS timestamp) IS NULL OR a.createdAt >= :fromDate) AND "
            + "(CAST(:toDate AS timestamp) IS NULL OR a.createdAt <= :toDate) AND "
            + "(:entityName IS NULL OR a.entityName = :entityName) AND "
            + "(:entityId IS NULL OR a.entityId = :entityId) AND "
            + "(:performedBy IS NULL OR a.actingUsername = :performedBy)")
    Page<AuditLog> search(@Param("entityName") String entityName, @Param("entityId") Long entityId,
                           @Param("performedBy") String performedBy, @Param("fromDate") Instant fromDate,
                           @Param("toDate") Instant toDate, Pageable pageable);
}
