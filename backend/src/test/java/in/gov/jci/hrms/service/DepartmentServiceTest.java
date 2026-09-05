package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.DepartmentRequest;
import in.gov.jci.hrms.dto.DepartmentResponse;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataInUseException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.DepartmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepartmentServiceTest {

    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private DepartmentService departmentService;

    @BeforeEach
    void setUp() {
        lenient().when(jdbcTemplate.queryForObject(any(String.class), org.mockito.ArgumentMatchers.eq(Long.class), any(Object.class)))
                .thenReturn(0L);
        departmentService = new DepartmentService(departmentRepository, new MasterDependencyService(jdbcTemplate));
    }

    private DepartmentRequest validRequest() {
        return new DepartmentRequest("ENG", "Engineering", "Builds and operates the platform");
    }

    private Department entityFrom(Long id, DepartmentRequest request) {
        Department department = new Department(request.code(), request.name());
        department.setDescription(request.description());
        ReflectionTestUtils.setField(department, "id", id);
        return department;
    }

    @Test
    void create_savesAndReturnsResponse() {
        DepartmentRequest request = validRequest();
        when(departmentRepository.saveAndFlush(any(Department.class))).thenReturn(entityFrom(1L, request));

        DepartmentResponse response = departmentService.create(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.code()).isEqualTo("ENG");
        assertThat(response.name()).isEqualTo("Engineering");
    }

    @Test
    void create_whenCodeAlreadyInUse_throwsMasterDataConflictException() {
        when(departmentRepository.saveAndFlush(any(Department.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> departmentService.create(validRequest()))
                .isInstanceOf(MasterDataConflictException.class);
    }

    @Test
    void getById_whenMissing_throwsMasterDataNotFoundException() {
        when(departmentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> departmentService.getById(99L))
                .isInstanceOf(MasterDataNotFoundException.class)
                .hasMessageContaining("Department");
    }

    @Test
    void delete_whenReferencedByActiveEmployee_throwsMasterDataInUseException() {
        Department department = entityFrom(2L, validRequest());
        when(departmentRepository.findById(2L)).thenReturn(Optional.of(department));
        when(jdbcTemplate.queryForObject(any(String.class), org.mockito.ArgumentMatchers.eq(Long.class), any(Object.class)))
                .thenReturn(1L);

        assertThatThrownBy(() -> departmentService.delete(2L))
                .isInstanceOf(MasterDataInUseException.class);

        assertThat(department.getDeletedAt()).isNull();
    }

    @Test
    void delete_whenNotReferenced_softDeletes() {
        Department department = entityFrom(3L, validRequest());
        when(departmentRepository.findById(3L)).thenReturn(Optional.of(department));

        departmentService.delete(3L);

        assertThat(department.getDeletedAt()).isNotNull();
        verify(departmentRepository, never()).deleteById(any());
    }
}
