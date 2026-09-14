package in.gov.jci.hrms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

/** One required-document row for a withdrawal rule (Part 20) - binds to the already-live cpf_rule_document (11 seeded rows). */
@Entity
@Table(name = "cpf_rule_document")
public class CpfRuleDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "detail_id", nullable = false)
    private CpfWithdrawalRuleDetail detail;

    @Column(name = "document_name", nullable = false, length = 128)
    private String documentName;

    @Column(name = "is_mandatory", nullable = false)
    private boolean mandatory = true;

    @Column(name = "allowed_mime_types", nullable = false, length = 256)
    private String allowedMimeTypes = "application/pdf,image/jpeg";

    @Column(name = "max_size_kb", nullable = false)
    private int maxSizeKb = 5120;

    protected CpfRuleDocument() {
    }

    public CpfRuleDocument(CpfWithdrawalRuleDetail detail, String documentName, boolean mandatory) {
        this.detail = detail;
        this.documentName = documentName;
        this.mandatory = mandatory;
    }

    public UUID getId() {
        return id;
    }

    public String getDocumentName() {
        return documentName;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    public String getAllowedMimeTypes() {
        return allowedMimeTypes;
    }

    public void setAllowedMimeTypes(String allowedMimeTypes) {
        this.allowedMimeTypes = allowedMimeTypes;
    }

    public int getMaxSizeKb() {
        return maxSizeKb;
    }

    public void setMaxSizeKb(int maxSizeKb) {
        this.maxSizeKb = maxSizeKb;
    }
}
