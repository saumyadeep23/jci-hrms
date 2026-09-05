package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.RegionalOfficeRequest;
import in.gov.jci.hrms.dto.RegionalOfficeResponse;
import in.gov.jci.hrms.entity.CityClass;
import in.gov.jci.hrms.entity.OfficeType;
import in.gov.jci.hrms.entity.RegionalOffice;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataInUseException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.RegionalOfficeRepository;
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
class RegionalOfficeServiceTest {

    @Mock
    private RegionalOfficeRepository regionalOfficeRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private RegionalOfficeService regionalOfficeService;

    @BeforeEach
    void setUp() {
        lenient().when(jdbcTemplate.queryForObject(any(String.class), org.mockito.ArgumentMatchers.eq(Long.class), any(Object.class)))
                .thenReturn(0L);
        regionalOfficeService = new RegionalOfficeService(regionalOfficeRepository, employeeRepository,
                new MasterDependencyService(jdbcTemplate), jdbcTemplate);
    }

    private RegionalOfficeRequest validRequest() {
        return new RegionalOfficeRequest("01", "Delhi RO", "Delhi", CityClass.X, true, OfficeType.REGIONAL_OFFICE,
                "1 Connaught Place", "New Delhi", "New Delhi", "ND-01", "110001",
                new java.math.BigDecimal("28.6139"), new java.math.BigDecimal("77.2090"),
                new java.math.BigDecimal("50.00"), java.math.BigDecimal.ZERO, true);
    }

    private RegionalOffice entityFrom(Long id, RegionalOfficeRequest request) {
        RegionalOffice regionalOffice = new RegionalOffice(
                request.code(), request.name(), request.state(), request.cityClass(), request.active());
        ReflectionTestUtils.setField(regionalOffice, "id", id);
        return regionalOffice;
    }

    @Test
    void create_savesAndReturnsResponse() {
        RegionalOfficeRequest request = validRequest();
        when(regionalOfficeRepository.saveAndFlush(any(RegionalOffice.class))).thenReturn(entityFrom(1L, request));

        RegionalOfficeResponse response = regionalOfficeService.create(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.code()).isEqualTo("01");
        assertThat(response.cityClass()).isEqualTo(CityClass.X);
    }

    @Test
    void create_whenCodeAlreadyInUse_throwsMasterDataConflictException() {
        when(regionalOfficeRepository.saveAndFlush(any(RegionalOffice.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> regionalOfficeService.create(validRequest()))
                .isInstanceOf(MasterDataConflictException.class);
    }

    @Test
    void getById_whenMissing_throwsMasterDataNotFoundException() {
        when(regionalOfficeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> regionalOfficeService.getById(99L))
                .isInstanceOf(MasterDataNotFoundException.class)
                .hasMessageContaining("Regional Office");
    }

    @Test
    void delete_whenReferencedByActiveEmployee_throwsMasterDataInUseException() {
        RegionalOffice regionalOffice = entityFrom(2L, validRequest());
        when(regionalOfficeRepository.findById(2L)).thenReturn(Optional.of(regionalOffice));
        when(employeeRepository.existsByRegionalOfficeId(2L)).thenReturn(true);

        assertThatThrownBy(() -> regionalOfficeService.delete(2L, "No longer required", "admin"))
                .isInstanceOf(MasterDataInUseException.class);

        assertThat(regionalOffice.getDeletedAt()).isNull();
    }

    @Test
    void delete_whenActiveDpcsAttached_throwsMasterDataInUseException() {
        RegionalOffice regionalOffice = entityFrom(5L, validRequest());
        when(regionalOfficeRepository.findById(5L)).thenReturn(Optional.of(regionalOffice));
        when(employeeRepository.existsByRegionalOfficeId(5L)).thenReturn(false);
        when(jdbcTemplate.queryForObject(any(String.class), org.mockito.ArgumentMatchers.eq(Long.class),
                org.mockito.ArgumentMatchers.eq(regionalOffice.getCode()))).thenReturn(2L);

        assertThatThrownBy(() -> regionalOfficeService.delete(5L, "No longer required", "admin"))
                .isInstanceOf(MasterDataInUseException.class);

        assertThat(regionalOffice.getDeletedAt()).isNull();
    }

    @Test
    void delete_whenNotReferenced_softDeletesAndDeactivates() {
        RegionalOffice regionalOffice = entityFrom(3L, validRequest());
        when(regionalOfficeRepository.findById(3L)).thenReturn(Optional.of(regionalOffice));
        when(employeeRepository.existsByRegionalOfficeId(3L)).thenReturn(false);

        regionalOfficeService.delete(3L, "Consolidated into RO-02", "admin@jci.gov.in");

        assertThat(regionalOffice.getDeletedAt()).isNotNull();
        assertThat(regionalOffice.isActive()).isFalse();
        assertThat(regionalOffice.getDeletedBy()).isEqualTo("admin@jci.gov.in");
        assertThat(regionalOffice.getDeletionReason()).isEqualTo("Consolidated into RO-02");
        verify(regionalOfficeRepository, never()).deleteById(any());
    }
}
