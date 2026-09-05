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
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A DPC (Departmental Purchase Centre) is a field procurement office under a
 * Regional Office. code uniqueness is enforced by a partial unique index
 * (WHERE deleted_at IS NULL) in the Flyway migration.
 */
@Entity
@Table(name = "dpc_master")
@SQLRestriction("deleted_at IS NULL")
public class DepartmentalPurchaseCentre {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Joined via ro_code (a real, independently-unique business key), not
     * ro_master's id PK - matches the shared dev database's actual FK
     * (dpc_master.ro_code -> ro_master.ro_code), which replaced an earlier
     * ro_id-based relation.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ro_code", referencedColumnName = "ro_code", nullable = false)
    private RegionalOffice regionalOffice;

    @Column(name = "dpc_code", nullable = false, length = 20)
    private String code;

    @Column(name = "dpc_name", nullable = false, length = 150)
    private String name;

    @Column(name = "district", nullable = false, length = 100)
    private String district;

    @Column(name = "state", nullable = false, length = 100)
    private String state;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "geofence_radius_meters")
    private Integer geofenceRadiusMeters;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "dpc_short_name", length = 20)
    private String shortName;

    @Enumerated(EnumType.STRING)
    @Column(name = "dpc_type", nullable = false, length = 20)
    private DpcType dpcType = DpcType.DPC;

    @Column(name = "district_code", length = 20)
    private String districtCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "city_class", nullable = false, length = 1, columnDefinition = "VARCHAR")
    private CityClass cityClass = CityClass.Z;

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

    protected DepartmentalPurchaseCentre() {
    }

    /** Kept exactly as-is (existing callers/tests construct DepartmentalPurchaseCentre this way) - newer fields keep their column defaults. */
    public DepartmentalPurchaseCentre(RegionalOffice regionalOffice, String code, String name, String district,
                                       String state, boolean active) {
        this.regionalOffice = regionalOffice;
        this.code = code;
        this.name = name;
        this.district = district;
        this.state = state;
        this.active = active;
    }

    public DepartmentalPurchaseCentre(RegionalOffice regionalOffice, String code, String name, String district,
                                       String state, boolean active, String shortName, DpcType dpcType,
                                       String districtCode, CityClass cityClass) {
        this.regionalOffice = regionalOffice;
        this.code = code;
        this.name = name;
        this.district = district;
        this.state = state;
        this.active = active;
        this.shortName = shortName;
        if (dpcType != null) this.dpcType = dpcType;
        this.districtCode = districtCode;
        if (cityClass != null) this.cityClass = cityClass;
    }

    public Long getId() {
        return id;
    }

    public RegionalOffice getRegionalOffice() {
        return regionalOffice;
    }

    public void setRegionalOffice(RegionalOffice regionalOffice) {
        this.regionalOffice = regionalOffice;
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

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
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

    public Integer getGeofenceRadiusMeters() {
        return geofenceRadiusMeters;
    }

    public void setGeofenceRadiusMeters(Integer geofenceRadiusMeters) {
        this.geofenceRadiusMeters = geofenceRadiusMeters;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public DpcType getDpcType() {
        return dpcType;
    }

    public void setDpcType(DpcType dpcType) {
        this.dpcType = dpcType;
    }

    public String getDistrictCode() {
        return districtCode;
    }

    public void setDistrictCode(String districtCode) {
        this.districtCode = districtCode;
    }

    public CityClass getCityClass() {
        return cityClass;
    }

    public void setCityClass(CityClass cityClass) {
        this.cityClass = cityClass;
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
