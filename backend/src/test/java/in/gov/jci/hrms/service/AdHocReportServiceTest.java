package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.AdHocReportRequest;
import in.gov.jci.hrms.dto.PimsReportFilter;
import in.gov.jci.hrms.dto.TabularReportResponse;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdHocReportServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private AdHocReportService service;

    @BeforeEach
    void setUp() {
        service = new AdHocReportService(jdbcTemplate);
    }

    @Test
    void generate_whenColumnNotInAllowlist_throwsBusinessRuleViolationException() {
        assertThatThrownBy(() -> service.generate(new AdHocReportRequest(List.of("employee_code", "DROP TABLE employees"), null, 0, 10)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Unknown or disallowed column");
    }

    @Test
    void generate_whenNoColumnsSelected_throwsBusinessRuleViolationException() {
        assertThatThrownBy(() -> service.generate(new AdHocReportRequest(List.of(), null, 0, 10)))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void generate_whenColumnsAllowed_queriesWithOrderedColumns() {
        when(jdbcTemplate.queryForObject(any(String.class), org.mockito.ArgumentMatchers.eq(Long.class), any(Object[].class)))
                .thenReturn(1L);
        when(jdbcTemplate.queryForList(any(String.class), any(Object[].class)))
                .thenReturn(List.of(Map.of("employee_code", "0001", "full_name", "Test Employee")));

        TabularReportResponse response = service.generate(new AdHocReportRequest(List.of("employee_code", "full_name"), PimsReportFilter.empty(), 0, 10));

        assertThat(response.columns()).containsExactly("employee_code", "full_name");
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.rows()).hasSize(1);
    }
}
