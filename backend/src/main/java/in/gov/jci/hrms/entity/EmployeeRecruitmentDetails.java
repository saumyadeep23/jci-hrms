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

import java.time.Instant;
import java.time.LocalDate;

/** One row per employee - Step 6's "Date of Joining PSU, Appointment Letter Number & Date, Joining Letter Date". */
@Entity
@Table(name = "employee_recruitment_details")
public class EmployeeRecruitmentDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, unique = true)
    private Employee employee;

    @Column(name = "recruitment_id", length = 50)
    private String recruitmentId;

    @Column(name = "advertisement_no", length = 100)
    private String advertisementNo;

    @Column(name = "recruitment_year", nullable = false)
    private Integer recruitmentYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "recruitment_mode", nullable = false, length = 50)
    private RecruitmentMode recruitmentMode;

    @Column(name = "selection_method", nullable = false, length = 50)
    private String selectionMethod;

    @Column(name = "recruitment_agency", length = 100)
    private String recruitmentAgency;

    @Column(name = "appointment_letter_no", nullable = false, length = 100)
    private String appointmentLetterNo;

    @Column(name = "appointment_letter_date", nullable = false)
    private LocalDate appointmentLetterDate;

    @Column(name = "offer_letter_date")
    private LocalDate offerLetterDate;

    @Column(name = "joining_letter_date", nullable = false)
    private LocalDate joiningLetterDate;

    @Column(name = "date_of_joining_psu", nullable = false)
    private LocalDate dateOfJoiningPsu;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EmployeeRecruitmentDetails() {
    }

    public EmployeeRecruitmentDetails(Employee employee, Integer recruitmentYear, RecruitmentMode recruitmentMode,
                                       String selectionMethod, String appointmentLetterNo, LocalDate appointmentLetterDate,
                                       LocalDate joiningLetterDate, LocalDate dateOfJoiningPsu) {
        this.employee = employee;
        this.recruitmentYear = recruitmentYear;
        this.recruitmentMode = recruitmentMode;
        this.selectionMethod = selectionMethod;
        this.appointmentLetterNo = appointmentLetterNo;
        this.appointmentLetterDate = appointmentLetterDate;
        this.joiningLetterDate = joiningLetterDate;
        this.dateOfJoiningPsu = dateOfJoiningPsu;
    }

    public Long getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public String getRecruitmentId() {
        return recruitmentId;
    }

    public void setRecruitmentId(String recruitmentId) {
        this.recruitmentId = recruitmentId;
    }

    public String getAdvertisementNo() {
        return advertisementNo;
    }

    public void setAdvertisementNo(String advertisementNo) {
        this.advertisementNo = advertisementNo;
    }

    public Integer getRecruitmentYear() {
        return recruitmentYear;
    }

    public void setRecruitmentYear(Integer recruitmentYear) {
        this.recruitmentYear = recruitmentYear;
    }

    public RecruitmentMode getRecruitmentMode() {
        return recruitmentMode;
    }

    public void setRecruitmentMode(RecruitmentMode recruitmentMode) {
        this.recruitmentMode = recruitmentMode;
    }

    public String getSelectionMethod() {
        return selectionMethod;
    }

    public void setSelectionMethod(String selectionMethod) {
        this.selectionMethod = selectionMethod;
    }

    public String getRecruitmentAgency() {
        return recruitmentAgency;
    }

    public void setRecruitmentAgency(String recruitmentAgency) {
        this.recruitmentAgency = recruitmentAgency;
    }

    public String getAppointmentLetterNo() {
        return appointmentLetterNo;
    }

    public void setAppointmentLetterNo(String appointmentLetterNo) {
        this.appointmentLetterNo = appointmentLetterNo;
    }

    public LocalDate getAppointmentLetterDate() {
        return appointmentLetterDate;
    }

    public void setAppointmentLetterDate(LocalDate appointmentLetterDate) {
        this.appointmentLetterDate = appointmentLetterDate;
    }

    public LocalDate getOfferLetterDate() {
        return offerLetterDate;
    }

    public void setOfferLetterDate(LocalDate offerLetterDate) {
        this.offerLetterDate = offerLetterDate;
    }

    public LocalDate getJoiningLetterDate() {
        return joiningLetterDate;
    }

    public void setJoiningLetterDate(LocalDate joiningLetterDate) {
        this.joiningLetterDate = joiningLetterDate;
    }

    public LocalDate getDateOfJoiningPsu() {
        return dateOfJoiningPsu;
    }

    public void setDateOfJoiningPsu(LocalDate dateOfJoiningPsu) {
        this.dateOfJoiningPsu = dateOfJoiningPsu;
    }
}
