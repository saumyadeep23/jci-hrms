package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.StateMaster;
import in.gov.jci.hrms.entity.StateType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-DB regression test for the GET /states 500: the shared dev database
 * seeds Delhi with state_type = 'NCT', a value StateTypeConverter didn't
 * originally recognize, so hydrating that row threw IllegalArgumentException
 * and took down the whole paginated list. findAll() must load every row.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class StateMasterRepositoryTest {

    @Autowired
    private StateMasterRepository stateMasterRepository;

    @Test
    @DisplayName("findAll(size=100) loads every seeded row, including Delhi's NCT state_type, without throwing")
    void findAll_loadsAllSeededRowsIncludingNationalCapitalTerritory() {
        List<StateMaster> states = stateMasterRepository.findAll(PageRequest.of(0, 100)).getContent();

        assertThat(states).isNotEmpty();
        assertThat(states)
                .filteredOn(state -> "DL".equals(state.getStateCode()))
                .singleElement()
                .satisfies(delhi -> assertThat(delhi.getStateType()).isEqualTo(StateType.NATIONAL_CAPITAL_TERRITORY));
    }
}
