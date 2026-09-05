package in.gov.jci.hrms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * code uniqueness is enforced by a partial unique index
 * (WHERE deleted_at IS NULL) in the Flyway migration.
 */
@Entity
@Table(name = "ro_master")
@SQLRestriction("deleted_at IS NULL")
public class RegionalOffice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ro_code", nullable = false, length = 20)
    private String code;

    @Column(name = "ro_name", nullable = false, length = 150)
    private String name;

    @Column(name = "state", nullable = false, length = 100)
    private String state;

    @Enumerated(EnumType.STRING)
    @Column(name = "city_class", nullable = false, length = 1, columnDefinition = "VARCHAR")
    private CityClass cityClass;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "office_type", nullable = false, length = 20)
    private OfficeType officeType = OfficeType.REGIONAL_OFFICE;

    @Column(name = "address_line", length = 255)
    private String addressLine;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "district", length = 100)
    private String district;

    @Column(name = "district_code", length = 20)
    private String districtCode;

    @Column(name = "pin_code", length = 10)
    private String pinCode;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "geofence_radius_meters", nullable = false, precision = 8, scale = 2)
    private BigDecimal geofenceRadiusMeters = new BigDecimal("50.00");

    @Column(name = "recr_club_deduc", nullable = false, precision = 10, scale = 2)
    private BigDecimal recreationClubDeduction = BigDecimal.ZERO;

    @Column(name = "is_proc_allow_applicable", nullable = false)
    private boolean procurementAllowanceApplicable = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by", length = 150)
    private String deletedBy;

    @Column(name = "deletion_reason", length = 500)
    private String deletionReason;

    protected RegionalOffice() {
    }

    /** Kept exactly as-is (existing callers/tests construct RegionalOffice this way) - the newer fields keep their column defaults. */
    public RegionalOffice(String code, String name, String state, CityClass cityClass, boolean active) {
        this.code = code;
        this.name = name;
        this.state = state;
        this.cityClass = cityClass;
        this.active = active;
    }

    public RegionalOffice(String code, String name, String state, CityClass cityClass, boolean active,
                           OfficeType officeType, String addressLine, String city, String district, String districtCode,
                           String pinCode, BigDecimal latitude, BigDecimal longitude, BigDecimal geofenceRadiusMeters,
                           BigDecimal recreationClubDeduction, boolean procurementAllowanceApplicable) {
        this.code = code;
        this.name = name;
        this.state = state;
        this.cityClass = cityClass;
        this.active = active;
        this.officeType = officeType;
        this.addressLine = addressLine;
        this.city = city;
        this.district = district;
        this.districtCode = districtCode;
        this.pinCode = pinCode;
        this.latitude = latitude;
        this.longitude = longitude;
        if (geofenceRadiusMeters != null) this.geofenceRadiusMeters = geofenceRadiusMeters;
        if (recreationClubDeduction != null) this.recreationClubDeduction = recreationClubDeduction;
        this.procurementAllowanceApplicable = procurementAllowanceApplicable;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public CityClass getCityClass() {
        return cityClass;
    }

    public void setCityClass(CityClass cityClass) {
        this.cityClass = cityClass;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public OfficeType getOfficeType() {
        return officeType;
    }

    public void setOfficeType(OfficeType officeType) {
        this.officeType = officeType;
    }

    public String getAddressLine() {
        return addressLine;
    }

    public void setAddressLine(String addressLine) {
        this.addressLine = addressLine;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public String getDistrictCode() {
        return districtCode;
    }

    public void setDistrictCode(String districtCode) {
        this.districtCode = districtCode;
    }

    public String getPinCode() {
        return pinCode;
    }

    public void setPinCode(String pinCode) {
        this.pinCode = pinCode;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public BigDecimal getGeofenceRadiusMeters() {
        return geofenceRadiusMeters;
    }

    public void setGeofenceRadiusMeters(BigDecimal geofenceRadiusMeters) {
        this.geofenceRadiusMeters = geofenceRadiusMeters;
    }

    public BigDecimal getRecreationClubDeduction() {
        return recreationClubDeduction;
    }

    public void setRecreationClubDeduction(BigDecimal recreationClubDeduction) {
        this.recreationClubDeduction = recreationClubDeduction;
    }

    public boolean isProcurementAllowanceApplicable() {
        return procurementAllowanceApplicable;
    }

    public void setProcurementAllowanceApplicable(boolean procurementAllowanceApplicable) {
        this.procurementAllowanceApplicable = procurementAllowanceApplicable;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public String getDeletedBy() {
        return deletedBy;
    }

    public void setDeletedBy(String deletedBy) {
        this.deletedBy = deletedBy;
    }

    public String getDeletionReason() {
        return deletionReason;
    }

    public void setDeletionReason(String deletionReason) {
        this.deletionReason = deletionReason;
    }
}