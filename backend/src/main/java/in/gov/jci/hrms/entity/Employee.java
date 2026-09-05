package in.gov.jci.hrms.entity;

import in.gov.jci.hrms.audit.Auditable;
import in.gov.jci.hrms.audit.AuditableEntityListener;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * employee_code/personal_email/pan_number/cpf_ac_no/hrms_user_id
 * uniqueness is enforced by partial unique indexes (WHERE deleted_at IS
 * NULL) in the Flyway migrations, not table-level constraints - see the V2
 * and V27 migrations.
 *
 * present/permanent address and bank details have moved to normalized child
 * tables (EmployeeAddress, EmployeeBankAccount - PIMS_SPEC.md Steps 2 & 3).
 * The denormalized bank/present/permanent-address snapshot columns this
 * table used to carry (bank columns synced by a since-dropped trigger) were
 * themselves dropped in V51 - EmployeeAddress and EmployeeBankAccount are
 * now the only source of truth for that data; use EmployeeAddressRepository
 * and EmployeeBankAccountRepository directly rather than reading it off
 * this entity. cpf_ac_no (originally personnel_no, a DB-generated internal
 * code - see V56 migration) is each employee's real, HR-entered
 * Contributory Provident Fund account number and is client-supplied like
 * panNumber, not DB-generated.
 */
@Entity
@Table(name = "employees")
@SQLRestriction("deleted_at IS NULL")
@EntityListeners(AuditableEntityListener.class)
public class Employee implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_code", nullable = false, length = 50)
    private String employeeCode;

    @Column(name = "cpf_ac_no", nullable = false, length = 20)
    private String cpfAcNo;

    @Column(name = "hrms_user_id", length = 50)
    private String hrmsUserId;

    @Convert(converter = SalutationConverter.class)
    @Column(name = "salutation", nullable = false, length = 10)
    private Salutation salutation;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "middle_name", length = 100)
    private String middleName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "full_name", insertable = false, updatable = false, length = 255)
    private String fullName;

    @Column(name = "name_in_regional_lang", length = 255)
    private String nameInRegionalLang;

    @Column(name = "previous_name", length = 255)
    private String previousName;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false, length = 20)
    private Gender gender;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "marital_status", nullable = false, length = 20)
    private MaritalStatus maritalStatus;

    @Convert(converter = BloodGroupConverter.class)
    @Column(name = "blood_group", length = 10)
    private BloodGroup bloodGroup;

    @Column(name = "nationality", nullable = false, length = 50)
    private String nationality = "Indian";

    @Column(name = "mother_tongue", length = 50)
    private String motherTongue;

    @Column(name = "pan_number", nullable = false, length = 10)
    private String panNumber;

    /** Stored masked, e.g. "XXXX-XXXX-1234" - see EmployeeService/onboarding finalize for the masking logic. */
    @Column(name = "aadhaar_ref_number", length = 20)
    private String aadhaarRefNumber;

    @Column(name = "personal_email", nullable = false, length = 255)
    private String personalEmail;

    @Column(name = "official_email", length = 255)
    private String officialEmail;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Column(name = "official_mobile", length = 20)
    private String officialMobile;

    @Column(name = "date_of_joining", nullable = false)
    private LocalDate dateOfJoining;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "designation_id", nullable = false)
    private Designation designation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ro_id")
    private RegionalOffice regionalOffice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dpc_id")
    private DepartmentalPurchaseCentre departmentalPurchaseCentre;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pay_scale_id")
    private PayScale payScale;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EmployeeStatus status = EmployeeStatus.ACTIVE;

    @Column(name = "is_geofence_exempted", nullable = false)
    private boolean geofenceExempted = false;

    /**
     * V55 (dual pension schemes): every REGULAR employee participates in the
     * CPSE Defined Contribution (NPS) scheme by default; EPS-95 (EPFO) only
     * applies to employees migrated from an EPS-covered past employment, and
     * higher-pension-on-actual-wages is itself only meaningful when EPS
     * applies at all - epsHigherPensionEligible is force-cleared to false
     * whenever epsEligible is false (see EmployeeService.applyOptionalFields
     * and the ck_employees_eps_higher_pension_requires_eps DB constraint).
     * Not yet consumed by PayrollComputationService.computeEpfEps(), which
     * still applies the standard EPS 15,000 ceiling uniformly - wiring these
     * flags into actual EPS/NPS contribution computation is a separate,
     * payroll-engine change.
     */
    @Column(name = "is_nps_eligible", nullable = false)
    private boolean npsEligible = true;

    @Column(name = "is_eps_eligible", nullable = false)
    private boolean epsEligible = false;

    @Column(name = "is_eps_higher_pension_eligible", nullable = false)
    private boolean epsHigherPensionEligible = false;

    /** NPS Permanent Retirement Account Number (12 digits) - only meaningful when npsEligible. */
    @Column(name = "pran_number", length = 12)
    private String pranNumber;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Employee() {
    }

    public Employee(String employeeCode, Salutation salutation, String firstName, String lastName, Gender gender,
                     LocalDate dateOfBirth, MaritalStatus maritalStatus, String panNumber, String cpfAcNo, String personalEmail,
                     String phone, LocalDate dateOfJoining, Department department, Designation designation) {
        this.employeeCode = employeeCode;
        this.salutation = salutation;
        this.firstName = firstName;
        this.lastName = lastName;
        this.gender = gender;
        this.dateOfBirth = dateOfBirth;
        this.maritalStatus = maritalStatus;
        this.panNumber = panNumber;
        this.cpfAcNo = cpfAcNo;
        this.personalEmail = personalEmail;
        this.phone = phone;
        this.dateOfJoining = dateOfJoining;
        this.department = department;
        this.designation = designation;
    }

    /**
     * Convenience overload matching this entity's pre-PIMS_SPEC.md shape -
     * exists solely so the many unit-test fixtures across unrelated domains
     * (Leave, Payroll, Loans, Attendance, APAR...) that only need "some
     * valid Employee" don't all need touching for fields those tests never
     * exercise. Never used by application code - EmployeeService/
     * EmployeeOnboardingService always go through the full constructor above.
     */
    public Employee(String employeeCode, String firstName, String lastName, String personalEmail,
                     LocalDate dateOfJoining, Department department, Designation designation) {
        this(employeeCode, Salutation.MR, firstName, lastName, Gender.MALE, LocalDate.of(1990, 1, 1), MaritalStatus.SINGLE,
                "ABCDE1234F", "CPF00001", personalEmail, "9999999999", dateOfJoining, department, designation);
    }

    public Long getId() {
        return id;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public void setEmployeeCode(String employeeCode) {
        this.employeeCode = employeeCode;
    }

    public String getCpfAcNo() {
        return cpfAcNo;
    }

    public void setCpfAcNo(String cpfAcNo) {
        this.cpfAcNo = cpfAcNo;
    }

    public String getHrmsUserId() {
        return hrmsUserId;
    }

    public void setHrmsUserId(String hrmsUserId) {
        this.hrmsUserId = hrmsUserId;
    }

    public Salutation getSalutation() {
        return salutation;
    }

    public void setSalutation(Salutation salutation) {
        this.salutation = salutation;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getMiddleName() {
        return middleName;
    }

    public void setMiddleName(String middleName) {
        this.middleName = middleName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getFullName() {
        return fullName;
    }

    public String getNameInRegionalLang() {
        return nameInRegionalLang;
    }

    public void setNameInRegionalLang(String nameInRegionalLang) {
        this.nameInRegionalLang = nameInRegionalLang;
    }

    public String getPreviousName() {
        return previousName;
    }

    public void setPreviousName(String previousName) {
        this.previousName = previousName;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public MaritalStatus getMaritalStatus() {
        return maritalStatus;
    }

    public void setMaritalStatus(MaritalStatus maritalStatus) {
        this.maritalStatus = maritalStatus;
    }

    public BloodGroup getBloodGroup() {
        return bloodGroup;
    }

    public void setBloodGroup(BloodGroup bloodGroup) {
        this.bloodGroup = bloodGroup;
    }

    public String getNationality() {
        return nationality;
    }

    public void setNationality(String nationality) {
        this.nationality = nationality;
    }

    public String getMotherTongue() {
        return motherTongue;
    }

    public void setMotherTongue(String motherTongue) {
        this.motherTongue = motherTongue;
    }

    public String getPanNumber() {
        return panNumber;
    }

    public void setPanNumber(String panNumber) {
        this.panNumber = panNumber;
    }

    public String getAadhaarRefNumber() {
        return aadhaarRefNumber;
    }

    public void setAadhaarRefNumber(String aadhaarRefNumber) {
        this.aadhaarRefNumber = aadhaarRefNumber;
    }

    public String getPersonalEmail() {
        return personalEmail;
    }

    public void setPersonalEmail(String personalEmail) {
        this.personalEmail = personalEmail;
    }

    public String getOfficialEmail() {
        return officialEmail;
    }

    public void setOfficialEmail(String officialEmail) {
        this.officialEmail = officialEmail;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getOfficialMobile() {
        return officialMobile;
    }

    public void setOfficialMobile(String officialMobile) {
        this.officialMobile = officialMobile;
    }

    public LocalDate getDateOfJoining() {
        return dateOfJoining;
    }

    public void setDateOfJoining(LocalDate dateOfJoining) {
        this.dateOfJoining = dateOfJoining;
    }

    public Department getDepartment() {
        return department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }

    public Designation getDesignation() {
        return designation;
    }

    public void setDesignation(Designation designation) {
        this.designation = designation;
    }

    public RegionalOffice getRegionalOffice() {
        return regionalOffice;
    }

    public void setRegionalOffice(RegionalOffice regionalOffice) {
        this.regionalOffice = regionalOffice;
    }

    public DepartmentalPurchaseCentre getDepartmentalPurchaseCentre() {
        return departmentalPurchaseCentre;
    }

    public void setDepartmentalPurchaseCentre(DepartmentalPurchaseCentre departmentalPurchaseCentre) {
        this.departmentalPurchaseCentre = departmentalPurchaseCentre;
    }

    public PayScale getPayScale() {
        return payScale;
    }

    public void setPayScale(PayScale payScale) {
        this.payScale = payScale;
    }

    public EmployeeStatus getStatus() {
        return status;
    }

    public void setStatus(EmployeeStatus status) {
        this.status = status;
    }

    public boolean isGeofenceExempted() {
        return geofenceExempted;
    }

    public void setGeofenceExempted(boolean geofenceExempted) {
        this.geofenceExempted = geofenceExempted;
    }

    public boolean isNpsEligible() {
        return npsEligible;
    }

    public void setNpsEligible(boolean npsEligible) {
        this.npsEligible = npsEligible;
    }

    public boolean isEpsEligible() {
        return epsEligible;
    }

    public void setEpsEligible(boolean epsEligible) {
        this.epsEligible = epsEligible;
    }

    public boolean isEpsHigherPensionEligible() {
        return epsHigherPensionEligible;
    }

    public void setEpsHigherPensionEligible(boolean epsHigherPensionEligible) {
        this.epsHigherPensionEligible = epsHigherPensionEligible;
    }

    public String getPranNumber() {
        return pranNumber;
    }

    public void setPranNumber(String pranNumber) {
        this.pranNumber = pranNumber;
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

    @Override
    public String auditEntityName() {
        return "Employee";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("employeeCode", employeeCode);
        snapshot.put("firstName", firstName);
        snapshot.put("lastName", lastName);
        snapshot.put("personalEmail", personalEmail);
        snapshot.put("phone", phone);
        snapshot.put("dateOfBirth", dateOfBirth);
        snapshot.put("dateOfJoining", dateOfJoining);
        snapshot.put("departmentId", department != null ? department.getId() : null);
        snapshot.put("designationId", designation != null ? designation.getId() : null);
        snapshot.put("roId", regionalOffice != null ? regionalOffice.getId() : null);
        snapshot.put("dpcId", departmentalPurchaseCentre != null ? departmentalPurchaseCentre.getId() : null);
        snapshot.put("payScaleId", payScale != null ? payScale.getId() : null);
        snapshot.put("status", status);
        snapshot.put("geofenceExempted", geofenceExempted);
        snapshot.put("npsEligible", npsEligible);
        snapshot.put("epsEligible", epsEligible);
        snapshot.put("epsHigherPensionEligible", epsHigherPensionEligible);
        snapshot.put("pranNumber", pranNumber);
        snapshot.put("deletedAt", deletedAt);
        return snapshot;
    }
}
