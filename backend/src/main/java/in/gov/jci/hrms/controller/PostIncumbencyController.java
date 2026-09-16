package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.PostIncumbencyRequest;
import in.gov.jci.hrms.dto.PostIncumbencyResponse;
import in.gov.jci.hrms.service.PostIncumbencyService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/post-incumbencies")
@PreAuthorize("hasRole('HR_ADMIN')")
public class PostIncumbencyController {

    private final PostIncumbencyService postIncumbencyService;

    public PostIncumbencyController(PostIncumbencyService postIncumbencyService) {
        this.postIncumbencyService = postIncumbencyService;
    }

    @PostMapping
    public ResponseEntity<PostIncumbencyResponse> create(@Valid @RequestBody PostIncumbencyRequest request) {
        PostIncumbencyResponse created = postIncumbencyService.create(request);
        return ResponseEntity.created(URI.create("/api/post-incumbencies/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public PostIncumbencyResponse getById(@PathVariable Long id) {
        return postIncumbencyService.getById(id);
    }

    @GetMapping
    public Page<PostIncumbencyResponse> list(Pageable pageable) {
        return postIncumbencyService.list(pageable);
    }

    @PostMapping("/{id}/end")
    public PostIncumbencyResponse end(@PathVariable Long id,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return postIncumbencyService.end(id, endDate);
    }
}
