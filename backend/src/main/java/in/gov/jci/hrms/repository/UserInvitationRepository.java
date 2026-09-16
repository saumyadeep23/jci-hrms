package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.InvitationStatus;
import in.gov.jci.hrms.entity.UserInvitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserInvitationRepository extends JpaRepository<UserInvitation, Long> {

    Optional<UserInvitation> findByTokenHashAndStatus(String tokenHash, InvitationStatus status);

    List<UserInvitation> findByUser_IdAndStatus(Long userId, InvitationStatus status);
}
