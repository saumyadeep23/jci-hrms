package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.AparCycleRequest;
import in.gov.jci.hrms.dto.AparCycleResponse;
import in.gov.jci.hrms.service.AparCycleService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/apar/cycles")
@PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
public class AparCycleController {

    private final AparCycleService aparCycleService;

    public AparCycleController(AparCycleService aparCycleService) {
        this.aparCycleService = aparCycleService;
    }

    @PostMapping
    public ResponseEntity<AparCycleResponse> create(@Valid @RequestBody AparCycleRequest request) {
        AparCycleResponse created = aparCycleService.create(request);
        return ResponseEntity.created(URI.create("/api/apar/cycles/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public AparCycleResponse getById(@PathVariable Long id) {
        return aparCycleService.getById(id);
    }

    @GetMapping
    public Page<AparCycleResponse> list(Pageable pageable) {
        return aparCycleService.list(pageable);
    }

    @PostMapping("/{id}/advance")
    public AparCycleResponse advance(@PathVariable Long id) {
        return aparCycleService.advance(id);
    }
}
