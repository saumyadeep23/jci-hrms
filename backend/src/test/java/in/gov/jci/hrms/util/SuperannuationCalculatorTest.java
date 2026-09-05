package in.gov.jci.hrms.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class SuperannuationCalculatorTest {

    @Test
    void regularEmployee_bornMidMonth_retiresLastDayOfBirthMonthAt58() {
        LocalDate dob = LocalDate.of(1970, 6, 15);

        LocalDate result = SuperannuationCalculator.calculateSuperannuationDate(dob, null, false);

        assertThat(result).isEqualTo(LocalDate.of(2028, 6, 30));
    }

    @Test
    void regularEmployee_bornOnFirst_retiresLastDayOfPrecedingMonthAt58() {
        LocalDate dob = LocalDate.of(1970, 6, 1);

        LocalDate result = SuperannuationCalculator.calculateSuperannuationDate(dob, null, false);

        assertThat(result).isEqualTo(LocalDate.of(2028, 5, 31));
    }

    @Test
    void director_fiveYearTermEarlierThanAge60_usesTermEndDate() {
        LocalDate dob = LocalDate.of(1970, 6, 15);
        LocalDate appointmentDate = LocalDate.of(2024, 1, 1);

        LocalDate result = SuperannuationCalculator.calculateSuperannuationDate(dob, appointmentDate, true);

        // Age-60 date would be 2030-06-30; 5-year term (2024-01-01 + 5y - 1d = 2028-12-31) is earlier.
        assertThat(result).isEqualTo(LocalDate.of(2028, 12, 31));
    }

    @Test
    void director_age60EarlierThanFiveYearTerm_usesAgeBasedDate() {
        LocalDate dob = LocalDate.of(1970, 6, 15);
        LocalDate appointmentDate = LocalDate.of(2028, 1, 1);

        LocalDate result = SuperannuationCalculator.calculateSuperannuationDate(dob, appointmentDate, true);

        assertThat(result).isEqualTo(LocalDate.of(2030, 6, 30));
    }

    @Test
    void director_withoutAppointmentDate_fallsBackToAge60Only() {
        LocalDate dob = LocalDate.of(1970, 6, 15);

        LocalDate result = SuperannuationCalculator.calculateSuperannuationDate(dob, null, true);

        assertThat(result).isEqualTo(LocalDate.of(2030, 6, 30));
    }
}
