package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.DisciplinaryCaseRequest;
import in.gov.jci.hrms.dto.DisciplinaryCaseResponse;
import in.gov.jci.hrms.dto.DisciplinaryStageUpdateRequest;
import in.gov.jci.hrms.service.DisciplinaryService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/disciplinary")
@PreAuthorize("hasRole('HR_ADMIN')")
public class DisciplinaryController {

    private final DisciplinaryService disciplinaryService;

    public DisciplinaryController(DisciplinaryService disciplinaryService) {
        this.disciplinaryService = disciplinaryService;
    }

    @PostMapping
    public ResponseEntity<DisciplinaryCaseResponse> createCase(@Valid @RequestBody DisciplinaryCaseRequest request) {
        DisciplinaryCaseResponse created = disciplinaryService.createCase(request);
        return ResponseEntity.created(URI.create("/api/disciplinary/" + created.id())).body(created);
    }

    @GetMapping
    public Page<DisciplinaryCaseResponse> list(Pageable pageable) {
        return disciplinaryService.list(pageable);
    }

    @GetMapping("/{id}")
    public DisciplinaryCaseResponse getById(@PathVariable Long id) {
        return disciplinaryService.getById(id);
    }

    @PutMapping("/{id}/stage")
    public DisciplinaryCaseResponse updateStage(@PathVariable Long id, @Valid @RequestBody DisciplinaryStageUpdateRequest request) {
        return disciplinaryService.updateStage(id, request);
    }

    @GetMapping("/employee/{employeeId}")
    public List<DisciplinaryCaseResponse> listByEmployee(@PathVariable Long employeeId) {
        return disciplinaryService.listByEmployee(employeeId);
    }
}
