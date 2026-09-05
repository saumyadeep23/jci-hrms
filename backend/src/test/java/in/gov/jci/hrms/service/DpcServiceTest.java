package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.DpcRequest;
import in.gov.jci.hrms.dto.DpcResponse;
import in.gov.jci.hrms.entity.CityClass;
import in.gov.jci.hrms.entity.DepartmentalPurchaseCentre;
import in.gov.jci.hrms.entity.DpcType;
import in.gov.jci.hrms.entity.RegionalOffice;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataInUseException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.DepartmentalPurchaseCentreRepository;
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
class DpcServiceTest {

    private static final Long RO_ID = 1L;

    @Mock
    private DepartmentalPurchaseCentreRepository dpcRepository;
    @Mock
    private RegionalOfficeRepository regionalOfficeRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private DpcService dpcService;
    private RegionalOffice regionalOffice;

    @BeforeEach
    void setUp() {
        lenient().when(jdbcTemplate.queryForObject(any(String.class), org.mockito.ArgumentMatchers.eq(Long.class), any(Object.class)))
                .thenReturn(0L);
        dpcService = new DpcService(dpcRepository, regionalOfficeRepository, employeeRepository,
                new MasterDependencyService(jdbcTemplate), jdbcTemplate);
        regionalOffice = new RegionalOffice("RO-DEL", "Delhi RO", "Delhi", CityClass.X, true);
        ReflectionTestUtils.setField(regionalOffice, "id", RO_ID);
    }

    private DpcRequest validRequest() {
        return new DpcRequest(RO_ID, "0001", "Delhi DPC 1", "New Delhi", "Delhi", null, null, null, true,
                "DEL-1", DpcType.DPC, "DL-ND", CityClass.X);
    }

    private DepartmentalPurchaseCentre entityFrom(Long id, DpcRequest request) {
        DepartmentalPurchaseCentre dpc = new DepartmentalPurchaseCentre(
                regionalOffice, request.code(), request.name(), request.district(), request.state(), request.active());
        ReflectionTestUtils.setField(dpc, "id", id);
        return dpc;
    }

    @Test
    void create_savesAndReturnsResponse() {
        DpcRequest request = validRequest();
        when(regionalOfficeRepository.findById(RO_ID)).thenReturn(Optional.of(regionalOffice));
        when(dpcRepository.saveAndFlush(any(DepartmentalPurchaseCentre.class))).thenReturn(entityFrom(1L, request));

        DpcResponse response = dpcService.create(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.roId()).isEqualTo(RO_ID);
        assertThat(response.code()).isEqualTo("0001");
    }

    @Test
    void create_stateIsIndependentOfRegionalOfficeState() {
        // regionalOffice (set up in @BeforeEach) is in Delhi, but this DPC's own
        // state is different - a DPC's geography is never inherited from or
        // validated against its supervising RO's state.
        DpcRequest request = new DpcRequest(RO_ID, "0002", "Border DPC", "Gurugram", "Haryana", null, null, null, true,
                null, DpcType.SUB_DPC, null, CityClass.Z);
        when(regionalOfficeRepository.findById(RO_ID)).thenReturn(Optional.of(regionalOffice));
        when(dpcRepository.saveAndFlush(any(DepartmentalPurchaseCentre.class))).thenAnswer(inv -> inv.getArgument(0));

        DpcResponse response = dpcService.create(request);

        assertThat(response.state()).isEqualTo("Haryana");
        assertThat(response.dpcType()).isEqualTo(DpcType.SUB_DPC);
    }

    @Test
    void create_appliesDefaultGeofenceRadiusWhenOmitted() {
        DpcRequest request = validRequest();
        when(regionalOfficeRepository.findById(RO_ID)).thenReturn(Optional.of(regionalOffice));
        when(dpcRepository.saveAndFlush(any(DepartmentalPurchaseCentre.class))).thenAnswer(inv -> inv.getArgument(0));

        DpcResponse response = dpcService.create(request);

        assertThat(response.geofenceRadiusMeters()).isEqualTo(100);
    }

    @Test
    void create_whenRegionalOfficeMissing_throwsMasterDataNotFoundException() {
        when(regionalOfficeRepository.findById(RO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dpcService.create(validRequest()))
                .isInstanceOf(MasterDataNotFoundException.class)
                .hasMessageContaining("Regional Office");
    }

    @Test
    void create_whenCodeAlreadyInUse_throwsMasterDataConflictException() {
        when(regionalOfficeRepository.findById(RO_ID)).thenReturn(Optional.of(regionalOffice));
        when(dpcRepository.saveAndFlush(any(DepartmentalPurchaseCentre.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> dpcService.create(validRequest()))
                .isInstanceOf(MasterDataConflictException.class);
    }

    @Test
    void delete_whenReferencedByActiveEmployee_throwsMasterDataInUseException() {
        DepartmentalPurchaseCentre dpc = entityFrom(2L, validRequest());
        when(dpcRepository.findById(2L)).thenReturn(Optional.of(dpc));
        when(employeeRepository.existsByDepartmentalPurchaseCentreId(2L)).thenReturn(true);

        assertThatThrownBy(() -> dpcService.delete(2L, "No longer required", "admin"))
                .isInstanceOf(MasterDataInUseException.class);

        assertThat(dpc.getDeletedAt()).isNull();
    }

    @Test
    void delete_whenActivePostIncumbencyExists_throwsMasterDataInUseException() {
        DepartmentalPurchaseCentre dpc = entityFrom(4L, validRequest());
        when(dpcRepository.findById(4L)).thenReturn(Optional.of(dpc));
        when(employeeRepository.existsByDepartmentalPurchaseCentreId(4L)).thenReturn(false);
        when(jdbcTemplate.queryForObject(any(String.class), org.mockito.ArgumentMatchers.eq(Long.class),
                org.mockito.ArgumentMatchers.eq(4L))).thenReturn(1L);

        assertThatThrownBy(() -> dpcService.delete(4L, "No longer required", "admin"))
                .isInstanceOf(MasterDataInUseException.class);

        assertThat(dpc.getDeletedAt()).isNull();
    }

    @Test
    void delete_whenNotReferenced_softDeletesAndDeactivates() {
        DepartmentalPurchaseCentre dpc = entityFrom(3L, validRequest());
        when(dpcRepository.findById(3L)).thenReturn(Optional.of(dpc));
        when(employeeRepository.existsByDepartmentalPurchaseCentreId(3L)).thenReturn(false);

        dpcService.delete(3L, "Consolidated into RO-DEL", "admin@jci.gov.in");

        assertThat(dpc.getDeletedAt()).isNotNull();
        assertThat(dpc.isActive()).isFalse();
        assertThat(dpc.getDeletedBy()).isEqualTo("admin@jci.gov.in");
        assertThat(dpc.getDeletionReason()).isEqualTo("Consolidated into RO-DEL");
        verify(dpcRepository, never()).deleteById(any());
    }
}
