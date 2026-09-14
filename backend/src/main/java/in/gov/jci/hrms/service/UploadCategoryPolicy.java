package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.UploadCategory;

import java.util.Map;
import java.util.Set;

import static in.gov.jci.hrms.entity.UploadCategory.AADHAAR;
import static in.gov.jci.hrms.entity.UploadCategory.APAR;
import static in.gov.jci.hrms.entity.UploadCategory.APPOINTMENT_ORDER;
import static in.gov.jci.hrms.entity.UploadCategory.BANK_PROOF;
import static in.gov.jci.hrms.entity.UploadCategory.CASTE_CERT;
import static in.gov.jci.hrms.entity.UploadCategory.CPF_DISPUTE_ATTACHMENT;
import static in.gov.jci.hrms.entity.UploadCategory.CPF_WITHDRAWAL_SUPPORTING_DOC;
import static in.gov.jci.hrms.entity.UploadCategory.DISCIPLINARY;
import static in.gov.jci.hrms.entity.UploadCategory.JOINING_REPORT;
import static in.gov.jci.hrms.entity.UploadCategory.OTHER;
import static in.gov.jci.hrms.entity.UploadCategory.PAN_CARD;
import static in.gov.jci.hrms.entity.UploadCategory.PAST_SERVICE_NOC;
import static in.gov.jci.hrms.entity.UploadCategory.PHOTO;
import static in.gov.jci.hrms.entity.UploadCategory.PWBD_CERT;
import static in.gov.jci.hrms.entity.UploadCategory.QUALIFICATION;
import static in.gov.jci.hrms.entity.UploadCategory.SERVICE_BOOK_SCAN;
import static in.gov.jci.hrms.entity.UploadCategory.SIGNATURE;

/** PIMS document-upload spec's Category/MIME/Extension/Size matrix (Section 3.A). */
public final class UploadCategoryPolicy {

    private static final Set<String> IMAGE_MIME_TYPES = Set.of("image/jpeg", "image/png");
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png");
    private static final Set<String> DOC_OR_IMAGE_MIME_TYPES = Set.of("application/pdf", "image/jpeg", "image/png");
    private static final Set<String> DOC_OR_IMAGE_EXTENSIONS = Set.of("pdf", "jpg", "jpeg", "png");
    private static final Set<String> PDF_MIME_TYPES = Set.of("application/pdf");
    private static final Set<String> PDF_EXTENSIONS = Set.of("pdf");

    private static final long ONE_MB = 1024L * 1024L;

    public record Rule(Set<String> allowedMimeTypes, Set<String> allowedExtensions, long maxSizeBytes) {
    }

    private static final Map<UploadCategory, Rule> RULES = Map.ofEntries(
            Map.entry(PHOTO, new Rule(IMAGE_MIME_TYPES, IMAGE_EXTENSIONS, 1 * ONE_MB)),
            Map.entry(SIGNATURE, new Rule(IMAGE_MIME_TYPES, IMAGE_EXTENSIONS, 500 * 1024L)),
            Map.entry(PAN_CARD, new Rule(DOC_OR_IMAGE_MIME_TYPES, DOC_OR_IMAGE_EXTENSIONS, 2 * ONE_MB)),
            Map.entry(AADHAAR, new Rule(DOC_OR_IMAGE_MIME_TYPES, DOC_OR_IMAGE_EXTENSIONS, 2 * ONE_MB)),
            Map.entry(CASTE_CERT, new Rule(DOC_OR_IMAGE_MIME_TYPES, DOC_OR_IMAGE_EXTENSIONS, 2 * ONE_MB)),
            Map.entry(PWBD_CERT, new Rule(DOC_OR_IMAGE_MIME_TYPES, DOC_OR_IMAGE_EXTENSIONS, 2 * ONE_MB)),
            Map.entry(BANK_PROOF, new Rule(DOC_OR_IMAGE_MIME_TYPES, DOC_OR_IMAGE_EXTENSIONS, 2 * ONE_MB)),
            Map.entry(QUALIFICATION, new Rule(PDF_MIME_TYPES, PDF_EXTENSIONS, 5 * ONE_MB)),
            Map.entry(PAST_SERVICE_NOC, new Rule(PDF_MIME_TYPES, PDF_EXTENSIONS, 5 * ONE_MB)),
            Map.entry(APPOINTMENT_ORDER, new Rule(PDF_MIME_TYPES, PDF_EXTENSIONS, 10 * ONE_MB)),
            Map.entry(JOINING_REPORT, new Rule(PDF_MIME_TYPES, PDF_EXTENSIONS, 10 * ONE_MB)),
            Map.entry(SERVICE_BOOK_SCAN, new Rule(PDF_MIME_TYPES, PDF_EXTENSIONS, 10 * ONE_MB)),
            Map.entry(APAR, new Rule(PDF_MIME_TYPES, PDF_EXTENSIONS, 10 * ONE_MB)),
            Map.entry(DISCIPLINARY, new Rule(PDF_MIME_TYPES, PDF_EXTENSIONS, 10 * ONE_MB)),
            Map.entry(CPF_DISPUTE_ATTACHMENT, new Rule(DOC_OR_IMAGE_MIME_TYPES, DOC_OR_IMAGE_EXTENSIONS, 5 * ONE_MB)),
            Map.entry(CPF_WITHDRAWAL_SUPPORTING_DOC, new Rule(DOC_OR_IMAGE_MIME_TYPES, DOC_OR_IMAGE_EXTENSIONS, 5 * ONE_MB)),
            Map.entry(OTHER, new Rule(PDF_MIME_TYPES, PDF_EXTENSIONS, 10 * ONE_MB))
    );

    private UploadCategoryPolicy() {
    }

    public static Rule ruleFor(UploadCategory category) {
        Rule rule = RULES.get(category);
        if (rule == null) {
            throw new IllegalStateException("No upload rule configured for category " + category);
        }
        return rule;
    }
}
