package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.TransferNature;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JoiningTimeCalculatorServiceTest {

    private final JoiningTimeCalculatorService service = new JoiningTimeCalculatorService();

    @Test
    void computeAdmissibleJtDays_ownRequest_isAlwaysZero() {
        assertThat(service.computeAdmissibleJtDays(TransferNature.OWN_REQUEST, 3000)).isZero();
        assertThat(service.computeAdmissibleJtDays(TransferNature.OWN_REQUEST, 5)).isZero();
    }

    @Test
    void computeAdmissibleJtDays_noRelocation_isZero() {
        assertThat(service.computeAdmissibleJtDays(TransferNature.ADMINISTRATIVE, 0)).isZero();
    }

    @Test
    void computeAdmissibleJtDays_sameStationUnder20km_isOneDay() {
        assertThat(service.computeAdmissibleJtDays(TransferNature.ADMINISTRATIVE, 19)).isEqualTo(1);
        assertThat(service.computeAdmissibleJtDays(TransferNature.MUTUAL, 1)).isEqualTo(1);
    }

    @Test
    void computeAdmissibleJtDays_upTo1000km_isTenDays() {
        assertThat(service.computeAdmissibleJtDays(TransferNature.ADMINISTRATIVE, 20)).isEqualTo(10);
        assertThat(service.computeAdmissibleJtDays(TransferNature.ADMINISTRATIVE, 1000)).isEqualTo(10);
    }

    @Test
    void computeAdmissibleJtDays_upTo2000km_isTwelveDays() {
        assertThat(service.computeAdmissibleJtDays(TransferNature.ADMINISTRATIVE, 1001)).isEqualTo(12);
        assertThat(service.computeAdmissibleJtDays(TransferNature.ADMINISTRATIVE, 2000)).isEqualTo(12);
    }

    @Test
    void computeAdmissibleJtDays_beyond2000km_isFifteenDays() {
        assertThat(service.computeAdmissibleJtDays(TransferNature.ADMINISTRATIVE, 2001)).isEqualTo(15);
        assertThat(service.computeAdmissibleJtDays(TransferNature.ADMINISTRATIVE, 4000)).isEqualTo(15);
    }

    @Test
    void computeUnavailedJtDays_neverNegative() {
        assertThat(service.computeUnavailedJtDays(10, 12)).isZero();
        assertThat(service.computeUnavailedJtDays(10, 4)).isEqualTo(6);
    }

    @Test
    void computeExcessTransitLwpDays_onlyWhenAvailedExceedsAdmissible() {
        assertThat(service.computeExcessTransitLwpDays(10, 4)).isZero();
        assertThat(service.computeExcessTransitLwpDays(10, 13)).isEqualTo(3);
    }
}
