package in.gov.jci.hrms.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * The database server's own current instant (Postgres clock_timestamp(), not
 * statement/transaction-start-pinned now()), used as the sole ground truth for joining-report
 * session (FN/AN) determination - see JoiningReportService. Deliberately not this JVM's
 * Clock/Instant.now(): the feature this exists for is explicitly a "Database-Clock ... Evaluation"
 * requirement, and going through the DB avoids any app-server-clock drift/skew from being able to
 * shift which side of the 13:00 IST cutoff a borderline submission lands on.
 */
@Service
public class DbClockService {

    private final JdbcTemplate jdbcTemplate;

    public DbClockService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Instant now() {
        Timestamp timestamp = jdbcTemplate.queryForObject("SELECT clock_timestamp()", Timestamp.class);
        return timestamp.toInstant();
    }
}
