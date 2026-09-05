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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One row per employee, feeding vw_jci_employee_master_360 and
 * ReservationRosterReportService (V31/V32 migrations). Optional - the
 * onboarding wizard only creates a row here if the candidate's Step 1/7
 * social-profile fields were actually filled in; ReservationRosterReportService
 * already COALESCEs a missing row to 'GEN' via its LEFT JOIN.
 */
@Entity
@Table(name = "employee_social_profiles")
public class EmployeeSocialProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, unique = true)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "social_category", nullable = false, length = 20)
    private SocialCategory socialCategory = SocialCategory.GEN;

    @Column(name = "sub_caste_community", length = 100)
    private String subCasteCommunity;

    @Column(name = "is_pwbd", nullable = false)
    private boolean pwbd = false;

    @Column(name = "disability_type", length = 100)
    private String disabilityType;

    @Column(name = "disability_percentage", precision = 5, scale = 2)
    private BigDecimal disabilityPercentage;

    @Column(name = "is_ex_serviceman", nullable = false)
    private boolean exServiceman = false;

    @Column(name = "is_sports_quota", nullable = false)
    private boolean sportsQuota = false;

    @Column(name = "reservation_cert_no", length = 100)
    private String reservationCertNo;

    @Column(name = "cert_issuing_authority", length = 150)
    private String certIssuingAuthority;

    @Column(name = "cert_issue_date")
    private LocalDate certIssueDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EmployeeSocialProfile() {
    }

    public EmployeeSocialProfile(Employee employee, SocialCategory socialCategory) {
        this.employee = employee;
        this.socialCategory = socialCategory != null ? socialCategory : SocialCategory.GEN;
    }

    public Long getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public SocialCategory getSocialCategory() {
        return socialCategory;
    }

    public void setSocialCategory(SocialCategory socialCategory) {
        this.socialCategory = socialCategory;
    }

    public String getSubCasteCommunity() {
        return subCasteCommunity;
    }

    public void setSubCasteCommunity(String subCasteCommunity) {
        this.subCasteCommunity = subCasteCommunity;
    }

    public boolean isPwbd() {
        return pwbd;
    }

    public void setPwbd(boolean pwbd) {
        this.pwbd = pwbd;
    }

    public String getDisabilityType() {
        return disabilityType;
    }

    public void setDisabilityType(String disabilityType) {
        this.disabilityType = disabilityType;
    }

    public BigDecimal getDisabilityPercentage() {
        return disabilityPercentage;
    }

    public void setDisabilityPercentage(BigDecimal disabilityPercentage) {
        this.disabilityPercentage = disabilityPercentage;
    }

    public boolean isExServiceman() {
        return exServiceman;
    }

    public void setExServiceman(boolean exServiceman) {
        this.exServiceman = exServiceman;
    }

    public boolean isSportsQuota() {
        return sportsQuota;
    }

    public void setSportsQuota(boolean sportsQuota) {
        this.sportsQuota = sportsQuota;
    }

    public String getReservationCertNo() {
        return reservationCertNo;
    }

    public void setReservationCertNo(String reservationCertNo) {
        this.reservationCertNo = reservationCertNo;
    }

    public String getCertIssuingAuthority() {
        return certIssuingAuthority;
    }

    public void setCertIssuingAuthority(String certIssuingAuthority) {
        this.certIssuingAuthority = certIssuingAuthority;
    }

    public LocalDate getCertIssueDate() {
        return certIssueDate;
    }

    public void setCertIssueDate(LocalDate certIssueDate) {
        this.certIssueDate = certIssueDate;
    }
}
