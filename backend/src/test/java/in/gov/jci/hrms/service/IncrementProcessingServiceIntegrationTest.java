package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.IncrementDueEntry;
import in.gov.jci.hrms.dto.PimsReportFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-DB regression test for the DPE round-up-to-next-10 increment rule -
 * a HALF_UP ("round to nearest 10") regression would silently understate
 * employee 2913's (Shashank Pratap) increment from Rs. 690 to Rs. 680 (Rs.
 * 22,820 x 3% = 684.60 rounds to 68.46 -> 68 under HALF_UP, 69 under
 * CEILING) - see IncrementProcessingServiceTest.roundToNearestTen_roundsUpNotToNearest
 * for the isolated formula test.
 */
@SpringBootTest
class IncrementProcessingServiceIntegrationTest {

    @Autowired
    private IncrementProcessingService incrementProcessingService;

    @Test
    void dueList_employee2913_roundsIncrementUpToNextTen() {
        List<IncrementDueEntry> dueList = incrementProcessingService.dueList(PimsReportFilter.empty());

        Optional<IncrementDueEntry> entry = dueList.stream().filter(e -> "2913".equals(e.employeeCode())).findFirst();

        assertThat(entry).isPresent();
        assertThat(entry.get().currentBasicPay()).isEqualByComparingTo("22820.00");
        assertThat(entry.get().incrementAmount()).isEqualByComparingTo(new BigDecimal("690.00"));
        assertThat(entry.get().newBasicPay()).isEqualByComparingTo(new BigDecimal("23510.00"));
    }
}
