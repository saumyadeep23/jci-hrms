package in.gov.jci.hrms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** PIMS_SPEC.md Step 8. */
@Entity
@Table(name = "employee_documents")
public class EmployeeDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_category", nullable = false, length = 50)
    private DocumentCategory documentCategory;

    @Column(name = "document_title", nullable = false, length = 200)
    private String documentTitle;

    @Column(name = "file_s3_key", nullable = false, length = 500)
    private String fileS3Key;

    @Column(name = "mime_type", nullable = false, length = 50)
    private String mimeType = "application/pdf";

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "is_verified", nullable = false)
    private boolean verified = false;

    @Column(name = "verified_by", length = 150)
    private String verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @CreationTimestamp
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    protected EmployeeDocument() {
    }

    public EmployeeDocument(Employee employee, DocumentCategory documentCategory, String documentTitle, String fileS3Key) {
        this.employee = employee;
        this.documentCategory = documentCategory;
        this.documentTitle = documentTitle;
        this.fileS3Key = fileS3Key;
    }

    public Long getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public DocumentCategory getDocumentCategory() {
        return documentCategory;
    }

    public String getDocumentTitle() {
        return documentTitle;
    }

    public String getFileS3Key() {
        return fileS3Key;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public boolean isVerified() {
        return verified;
    }

    public String getVerifiedBy() {
        return verifiedBy;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }
}
