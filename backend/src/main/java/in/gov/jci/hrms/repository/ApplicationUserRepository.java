package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.ApplicationUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApplicationUserRepository extends JpaRepository<ApplicationUser, Long> {

    Optional<ApplicationUser> findByEmployee_Id(Long employeeId);

    Optional<ApplicationUser> findByUsername(String username);

    boolean existsByUsername(String username);
}
