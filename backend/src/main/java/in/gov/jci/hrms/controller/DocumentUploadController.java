package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.DocumentUploadResponse;
import in.gov.jci.hrms.dto.InvalidFilePayloadResponse;
import in.gov.jci.hrms.entity.UploadCategory;
import in.gov.jci.hrms.exception.InvalidFilePayloadException;
import in.gov.jci.hrms.service.DocumentUploadService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;

/** PIMS_SPEC.md Section 3.B. */
@RestController
@RequestMapping("/api/v1/documents")
@PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN', 'EMPLOYEE')")
public class DocumentUploadController {

    private final DocumentUploadService documentUploadService;

    public DocumentUploadController(DocumentUploadService documentUploadService) {
        this.documentUploadService = documentUploadService;
    }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<DocumentUploadResponse> upload(@RequestParam("file") MultipartFile file,
                                                           @RequestParam("category") UploadCategory category,
                                                           @RequestParam(value = "entityRefId", required = false) String entityRefId) {
        DocumentUploadResponse response = documentUploadService.upload(file, category);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @ExceptionHandler(InvalidFilePayloadException.class)
    public ResponseEntity<InvalidFilePayloadResponse> handleInvalidFilePayload(InvalidFilePayloadException ex) {
        return ResponseEntity.status(ex.getStatus()).body(new InvalidFilePayloadResponse(
                Instant.now(), ex.getStatus().value(), ex.getErrorCode(), ex.getMessage(),
                ex.getField(), ex.getAllowedMimeTypes(), ex.getMaxAllowedSizeBytes()));
    }
}
