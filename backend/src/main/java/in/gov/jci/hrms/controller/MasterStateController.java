package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.StateOptionResponse;
import in.gov.jci.hrms.repository.StateMasterRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Holiday & RH Publisher's state dropdown source - deliberately open to any
 * authenticated user (not SUPER_ADMIN-gated like /api/v1/admin/masters/states,
 * the full CRUD console), since HR_ADMIN needs it to build a holiday's state
 * scope and this is a read-only, non-sensitive lookup.
 */
@RestController
@RequestMapping("/api/v1/master/states")
@PreAuthorize("isAuthenticated()")
public class MasterStateController {

    private final StateMasterRepository stateMasterRepository;

    public MasterStateController(StateMasterRepository stateMasterRepository) {
        this.stateMasterRepository = stateMasterRepository;
    }

    @GetMapping
    public List<StateOptionResponse> list() {
        return stateMasterRepository.findByActiveTrueOrderByStateNameAsc().stream()
                .map(StateOptionResponse::from)
                .toList();
    }
}
