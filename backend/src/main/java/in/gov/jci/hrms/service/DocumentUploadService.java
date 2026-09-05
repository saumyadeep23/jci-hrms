package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.DocumentUploadResponse;
import in.gov.jci.hrms.entity.UploadCategory;
import in.gov.jci.hrms.exception.InvalidFilePayloadException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.UUID;

/**
 * POST /api/v1/documents/upload - PIMS_SPEC.md Section 3. Validates
 * (category-specific MIME/extension/size, then the file's own magic bytes),
 * sanitizes the filename, stores via DocumentStorageService, and returns
 * the resulting key. Deliberately doesn't know about employees/qualifications/
 * bank accounts/etc. - linking the returned fileS3Key to a domain record is
 * the caller's job (e.g. EmployeeOnboardingService, once the corresponding
 * step is saved/finalized).
 */
@Service
public class DocumentUploadService {

    private final DocumentStorageService storageService;

    public DocumentUploadService(DocumentStorageService storageService) {
        this.storageService = storageService;
    }

    public DocumentUploadResponse upload(MultipartFile file, UploadCategory category) {
        if (file == null || file.isEmpty()) {
            throw invalid(HttpStatus.BAD_REQUEST, "INVALID_FILE_PAYLOAD", "file is required and must not be empty", "file", null, null);
        }

        UploadCategoryPolicy.Rule rule = UploadCategoryPolicy.ruleFor(category);

        String originalFileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        String extension = extensionOf(originalFileName);
        String mimeType = file.getContentType();

        if (mimeType == null || !rule.allowedMimeTypes().contains(mimeType)) {
            throw invalid(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "INVALID_FILE_PAYLOAD",
                    "Content-Type '" + mimeType + "' is not permitted for category " + category
                            + "; allowed types are " + rule.allowedMimeTypes(),
                    "file", rule.allowedMimeTypes(), null);
        }
        if (extension == null || !rule.allowedExtensions().contains(extension)) {
            throw invalid(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "INVALID_FILE_PAYLOAD",
                    "File extension '" + extension + "' is not permitted for category " + category
                            + "; allowed extensions are " + rule.allowedExtensions(),
                    "file", rule.allowedMimeTypes(), null);
        }
        if (file.getSize() > rule.maxSizeBytes()) {
            throw invalid(HttpStatus.BAD_REQUEST, "INVALID_FILE_PAYLOAD",
                    "File size (%s) exceeds the maximum allowed limit of %s for category %s"
                            .formatted(humanReadableBytes(file.getSize()), humanReadableBytes(rule.maxSizeBytes()), category),
                    "file", rule.allowedMimeTypes(), rule.maxSizeBytes());
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded file", e);
        }

        if (!FileSignatureValidator.matchesDeclaredType(content, mimeType)) {
            throw invalid(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "INVALID_FILE_PAYLOAD",
                    "File content does not match its declared Content-Type (" + mimeType + ") - the file may be corrupt, mislabeled, or disguised",
                    "file", rule.allowedMimeTypes(), null);
        }

        String key = generateKey(category, originalFileName, extension);
        try {
            storageService.store(key, content);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store uploaded file", e);
        }

        return new DocumentUploadResponse(key, category, originalFileName, mimeType, file.getSize(), Instant.now());
    }

    /** documents/{YEAR}/{MONTH}/{UUID}_{cleanFilename} - PIMS_SPEC.md Section 3.C. */
    private String generateKey(UploadCategory category, String originalFileName, String extension) {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        String cleanFilename = sanitizeFilename(baseNameOf(originalFileName)) + "." + extension;
        return "documents/%04d/%02d/%s_%s_%s".formatted(
                now.getYear(), now.getMonthValue(), UUID.randomUUID(), category.name().toLowerCase(Locale.ROOT), cleanFilename);
    }

    /**
     * Path Traversal Guard - PIMS_SPEC.md Section 3.C: strips any directory
     * components (so "../../etc/passwd" collapses to "passwd") and then
     * keeps only alphanumerics, dot, dash and underscore from what remains.
     */
    private String sanitizeFilename(String baseName) {
        String noPathComponents = baseName.replace('\\', '/');
        int lastSlash = noPathComponents.lastIndexOf('/');
        String nameOnly = lastSlash >= 0 ? noPathComponents.substring(lastSlash + 1) : noPathComponents;
        String sanitized = nameOnly.replaceAll("[^A-Za-z0-9._-]", "_");
        return sanitized.isBlank() ? "file" : sanitized;
    }

    private String baseNameOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String humanReadableBytes(long bytes) {
        double mb = bytes / (1024.0 * 1024.0);
        if (mb >= 1) {
            return "%.1f MB".formatted(mb);
        }
        return "%.0f KB".formatted(bytes / 1024.0);
    }

    private InvalidFilePayloadException invalid(HttpStatus status, String errorCode, String message, String field,
                                                 java.util.Set<String> allowedMimeTypes, Long maxAllowedSizeBytes) {
        return new InvalidFilePayloadException(status, errorCode, message, field, allowedMimeTypes, maxAllowedSizeBytes);
    }
}
