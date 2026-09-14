package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.JciEccsLifecycleEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JciEccsLifecycleEventRepository extends JpaRepository<JciEccsLifecycleEvent, Long> {

    List<JciEccsLifecycleEvent> findByEntityTypeAndEntityIdOrderByEventDateAsc(String entityType, Long entityId);
}
