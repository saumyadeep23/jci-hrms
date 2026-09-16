package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.IncrementBatchProcessRequest;
import in.gov.jci.hrms.dto.IncrementBatchProcessResponse;
import in.gov.jci.hrms.dto.IncrementDueEntry;
import in.gov.jci.hrms.dto.PimsReportFilter;
import in.gov.jci.hrms.service.IncrementProcessingService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** PIMS_SPEC.md dashboard card 5 (Career & Service Book) - Monthly Increment (3% IDA). */
@RestController
@RequestMapping("/api/v1/increments")
@PreAuthorize("hasRole('HR_ADMIN')")
public class IncrementController {

    private final IncrementProcessingService incrementProcessingService;

    public IncrementController(IncrementProcessingService incrementProcessingService) {
        this.incrementProcessingService = incrementProcessingService;
    }

    @GetMapping("/due-list")
    public List<IncrementDueEntry> dueList(@RequestParam(required = false) Long departmentId,
                                            @RequestParam(required = false) Integer incrementMonth) {
        return incrementProcessingService.dueList(
                new PimsReportFilter(departmentId, null, null, null, null, null, null, null, null, null, incrementMonth));
    }

    @PostMapping("/process-batch")
    public IncrementBatchProcessResponse processBatch(@Valid @RequestBody IncrementBatchProcessRequest request) {
        return incrementProcessingService.processBatch(request);
    }
}
