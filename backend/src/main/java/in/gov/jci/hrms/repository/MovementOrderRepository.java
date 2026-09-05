package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.MovementOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovementOrderRepository extends JpaRepository<MovementOrder, Long> {

    boolean existsByOrderRefNo(String orderRefNo);

    Page<MovementOrder> findAllByOrderByOrderDateDesc(Pageable pageable);
}
