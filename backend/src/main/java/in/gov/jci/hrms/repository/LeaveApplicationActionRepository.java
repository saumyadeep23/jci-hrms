package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.LeaveApplicationAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface LeaveApplicationActionRepository extends JpaRepository<LeaveApplicationAction, Long> {

    /** JOIN FETCHes actionBy/forwardedTo + their designations in one query - the routing timeline renders every actor's name and designation, one row per action. */
    @Query("SELECT a FROM LeaveApplicationAction a JOIN FETCH a.actionBy ab LEFT JOIN FETCH ab.designation "
            + "LEFT JOIN FETCH a.forwardedTo ft LEFT JOIN FETCH ft.designation "
            + "WHERE a.application.id = :applicationId ORDER BY a.createdAt ASC")
    List<LeaveApplicationAction> findByApplicationIdOrderByCreatedAtAsc(Long applicationId);
}
