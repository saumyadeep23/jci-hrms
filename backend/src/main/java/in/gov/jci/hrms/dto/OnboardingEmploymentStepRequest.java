package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.EmploymentCategory;
import in.gov.jci.hrms.entity.IncrementCycle;
import in.gov.jci.hrms.entity.RecruitmentMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Onboarding wizard Step 6 (4-Tier Employment Category & Post Assignment) -
 * PIMS_SPEC.md. Which of the REGULAR/CASUAL/CONTRACTUAL/OUTSOURCED fields
 * are required depends on employmentCategory - see
 * EmployeeOnboardingService.validateEmploymentStep and the DB's own
 * chk_regular_data/chk_casual_data/chk_contractual_data/chk_outsourced_data
 * CHECK constraints (V29 migration), which are the final backstop.
 */
public record OnboardingEmploymentStepRequest(
        @NotNull EmploymentCategory employmentCategory,

        /**
         * Org attachment. For REGULAR this is derived from the selected
         * post instead (see postId) - department/designation authority
         * lives on the post, not the person, once they hold a sanctioned
         * seat - so these two are only required for CASUAL/CONTRACTUAL/
         * OUTSOURCED, who don't occupy a post.
         */
        Long departmentId,
        Long designationId,

        // REGULAR
        Long postId,
        Long payScaleId,
        BigDecimal regularBasicPay,

        // CASUAL
        BigDecimal dailyWageRate,
        String wageRevisionOrderNo,

        // CONTRACTUAL
        BigDecimal fixedLumpSumMonthly,
        LocalDate contractStartDate,
        LocalDate contractEndDate,
        String contractRefOrder,

        // OUTSOURCED
        Long vendorId,
        BigDecimal monthlyCtc,
        BigDecimal billingRateMonthly,
        String agencyEmployeeId,
        List<OutsourcedSalaryBreakdownEntry> ctcBreakdown,

        /**
         * Optional additive link into grade_scale_master (V48/V49) - lets REGULAR/CONTRACTUAL/
         * OUTSOURCED onboarding derive their pay band/lumpsum/CTC from the new Board/Executive/Staff
         * scale alongside (never instead of) the fields above. Null is fine - existing submissions
         * that predate this field, and CASUAL (which has no grade-scale concept), simply omit it.
         */
        String scaleCode,

        /** REGULAR only - which half-year cycle this employee's future annual increments fall on; defaults JULY (regular_pay_fixations.increment_cycle, V50) when null. */
        IncrementCycle incrementCycle,

        // Pension & Retirement Schemes (V55) - see EmployeeRequest's own javadoc for the eligibility rules this mirrors
        Boolean isNpsEligible,
        Boolean isEpsEligible,
        Boolean isEpsHigherPensionEligible,
        @Pattern(regexp = "^[0-9]{12}$") String pranNumber,

        // Recruitment metadata (employee_recruitment_details)
        @NotNull LocalDate dateOfJoiningPsu,
        @NotNull Integer recruitmentYear,
        @NotNull RecruitmentMode recruitmentMode,
        @NotBlank @Size(max = 50) String selectionMethod,
        String recruitmentAgency,
        String advertisementNo,
        @NotBlank @Size(max = 100) String appointmentLetterNo,
        @NotNull LocalDate appointmentLetterDate,
        LocalDate offerLetterDate,
        @NotNull LocalDate joiningLetterDate
) {
}
