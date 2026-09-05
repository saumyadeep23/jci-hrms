package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.DependencyCheckResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MasterDependencyServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private MasterDependencyService dependencyService;

    @BeforeEach
    void setUp() {
        dependencyService = new MasterDependencyService(jdbcTemplate);
    }

    @Test
    void check_whenNoReferencingRows_returnsNoActiveDependencies() {
        when(jdbcTemplate.queryForObject(any(String.class), eq(Long.class), any(Object.class))).thenReturn(0L);

        DependencyCheckResponse response = dependencyService.check(
                List.of(new MasterDependencyService.DependencyProbe("employees", "department_id", "Employees", true)), 10L);

        assertThat(response.hasActiveDependencies()).isFalse();
        assertThat(response.usages()).isEmpty();
    }

    @Test
    void check_whenReferencingRowsExist_reportsUsagePerTable() {
        when(jdbcTemplate.queryForObject(org.mockito.ArgumentMatchers.contains("employees"), eq(Long.class), any(Object.class)))
                .thenReturn(3L);
        when(jdbcTemplate.queryForObject(org.mockito.ArgumentMatchers.contains("post_master"), eq(Long.class), any(Object.class)))
                .thenReturn(0L);

        DependencyCheckResponse response = dependencyService.check(List.of(
                new MasterDependencyService.DependencyProbe("employees", "department_id", "Employees", true),
                new MasterDependencyService.DependencyProbe("post_master", "department_id", "Sanctioned Posts", true)
        ), 10L);

        assertThat(response.hasActiveDependencies()).isTrue();
        assertThat(response.usages()).hasSize(1);
        assertThat(response.usages().get(0).table()).isEqualTo("employees");
        assertThat(response.usages().get(0).activeCount()).isEqualTo(3L);
    }
}
