package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.TourRequestRequest;
import in.gov.jci.hrms.dto.TourRequestResponse;
import in.gov.jci.hrms.service.TourRequestService;
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
@RequestMapping("/api/reimbursements/tours")
@PreAuthorize("hasAnyRole('EMPLOYEE', 'HR_ADMIN', 'FINANCE_ADMIN', 'SUPER_ADMIN')")
public class TourRequestController {

    private final TourRequestService tourRequestService;

    public TourRequestController(TourRequestService tourRequestService) {
        this.tourRequestService = tourRequestService;
    }

    @PostMapping
    public ResponseEntity<TourRequestResponse> create(@Valid @RequestBody TourRequestRequest request) {
        TourRequestResponse created = tourRequestService.create(request);
        return ResponseEntity.created(URI.create("/api/reimbursements/tours/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public TourRequestResponse getById(@PathVariable Long id) {
        return tourRequestService.getById(id);
    }

    @GetMapping
    public Page<TourRequestResponse> list(Pageable pageable) {
        return tourRequestService.list(pageable);
    }
}
