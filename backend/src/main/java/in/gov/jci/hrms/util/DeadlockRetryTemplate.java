package in.gov.jci.hrms.util;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Retries an operation a few times on a genuine database deadlock (Postgres SQLState 40P01, surfaced as
 * {@link CannotAcquireLockException}/{@link DeadlockLoserDataAccessException}) before giving up.
 *
 * <p>JCIECCS's pessimistic-locking conventions ({@code findByIdForUpdate} on loan/member/recovery/
 * collection_detail rows - see {@code JciEccsRecoveryService}, {@code JciEccsLoanRepository}) prevent lost
 * updates and duplicate financial effects, but two independently-evolved code paths (payroll debit
 * confirmation and cash repayment) can legitimately acquire the same set of rows in different orders under
 * real concurrent load - this is a well-known, unavoidable class of contention in any system with more
 * than one lock-acquisition path, not a correctness bug. Postgres's own deadlock detector always picks a
 * victim and fully rolls back its transaction (no partial financial state survives), so retrying the whole
 * operation from a clean, freshly-committed baseline is safe - every JCIECCS operation this wraps is already
 * idempotent on its own key (a payroll debit-confirmation line's PENDING_DEBIT gate, a cash repayment's
 * idempotencyKey), so a retry can never double-post even if the first attempt's rollback is imperfectly
 * observed by the caller.
 *
 * <p>Deliberately NOT solved with {@code synchronized} or an application-level mutex - this only ever
 * retries a genuine, already-rolled-back deadlock loser; it does not serialize unrelated requests.
 */
@Component
public class DeadlockRetryTemplate {

    private static final int MAX_ATTEMPTS = 3;
    private static final long BASE_BACKOFF_MILLIS = 25;

    public <T> T execute(Supplier<T> operation) {
        DataAccessException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return operation.get();
            } catch (CannotAcquireLockException | DeadlockLoserDataAccessException e) {
                lastFailure = e;
                if (attempt < MAX_ATTEMPTS) {
                    backoff(attempt);
                }
            }
        }
        throw lastFailure;
    }

    private void backoff(int attempt) {
        try {
            Thread.sleep(BASE_BACKOFF_MILLIS * attempt);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
