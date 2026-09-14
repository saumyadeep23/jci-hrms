package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.JciEccsMember;
import in.gov.jci.hrms.entity.JciEccsMembershipStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JciEccsMemberRepository extends JpaRepository<JciEccsMember, Long> {

    Optional<JciEccsMember> findByEmployeeId(Long employeeId);

    Optional<JciEccsMember> findByMembershipCode(String membershipCode);

    List<JciEccsMember> findByMembershipStatus(JciEccsMembershipStatus membershipStatus);

    /** Phase 5 concurrency hardening: the thrift running balance is derived by reading the latest
     * jcieccs_thrift_transaction row and inserting the next one (JciEccsRecoveryPostingService.
     * postThriftContribution) - a read-then-insert pattern with no row of its own to serialize on. Locking
     * the member row here (mirroring JciEccsLoanRepository.findByIdForUpdate for loan balances) gives that
     * read-then-insert a stable serialization point so two concurrent thrift-affecting recoveries for the
     * same member can never both compute their newBalance off the same stale previousBalance. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM JciEccsMember m WHERE m.id = :id")
    Optional<JciEccsMember> findByIdForUpdate(@Param("id") Long id);
}
