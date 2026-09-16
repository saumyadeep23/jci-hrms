package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.GradeScaleCreateRequest;
import in.gov.jci.hrms.dto.GradeScaleMasterResponse;
import in.gov.jci.hrms.dto.GradeScaleUpdateRequest;
import in.gov.jci.hrms.service.GradeScaleMasterService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

/**
 * The new, additive Board/Executive/Staff grade-scale master - see GradeScaleMaster's javadoc for
 * why it exists alongside (not instead of) PayScaleController.
 *
 * <p>No {@code @CrossOrigin} here deliberately: cross-origin access is already handled globally by
 * SecurityConfig's {@code corsConfigurationSource()} bean (registered for every path, consulted by
 * Spring Security's own CORS filter before a request ever reaches this controller). Adding
 * {@code @CrossOrigin} on top would either be inert (the security filter already answered the
 * preflight) or actively conflict with it - its default {@code origins = "*"} is incompatible with
 * the global config's {@code allowCredentials(true)}, which browsers require an explicit origin
 * list for.
 */
@RestController
@RequestMapping("/api/v1/masters/grade-scales")
@PreAuthorize("hasAnyRole('HR_ADMIN', 'EMPLOYEE', 'FINANCE_ADMIN')")
public class GradeScaleController {

    private final GradeScaleMasterService gradeScaleMasterService;

    public GradeScaleController(GradeScaleMasterService gradeScaleMasterService) {
        this.gradeScaleMasterService = gradeScaleMasterService;
    }

    @GetMapping
    public List<GradeScaleMasterResponse> list() {
        return gradeScaleMasterService.list();
    }

    @GetMapping("/{id}")
    public GradeScaleMasterResponse getById(@PathVariable Long id) {
        return gradeScaleMasterService.getById(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'FINANCE_ADMIN')")
    public ResponseEntity<GradeScaleMasterResponse> create(@Valid @RequestBody GradeScaleCreateRequest request) {
        GradeScaleMasterResponse created = gradeScaleMasterService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/masters/grade-scales/" + created.id())).body(created);
    }

    /** Keyed by scaleCode (e.g. "E9"), not the numeric id - the stable, human-meaningful identifier admins actually address a grade by. */
    @PutMapping("/{scaleCode}")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'FINANCE_ADMIN')")
    public GradeScaleMasterResponse update(@PathVariable String scaleCode, @Valid @RequestBody GradeScaleUpdateRequest request) {
        return gradeScaleMasterService.update(scaleCode, request);
    }
}
