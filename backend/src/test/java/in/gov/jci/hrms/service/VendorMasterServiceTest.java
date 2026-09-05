package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.VendorMasterRequest;
import in.gov.jci.hrms.dto.VendorMasterResponse;
import in.gov.jci.hrms.entity.VendorMaster;
import in.gov.jci.hrms.exception.DuplicateResourceException;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataValidationException;
import in.gov.jci.hrms.repository.VendorMasterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VendorMasterServiceTest {

    @Mock
    private VendorMasterRepository vendorMasterRepository;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private VendorMasterService vendorMasterService;

    @BeforeEach
    void setUp() {
        lenient().when(jdbcTemplate.queryForObject(any(String.class), eq(Long.class), any(Object.class)))
                .thenReturn(0L);
        vendorMasterService = new VendorMasterService(vendorMasterRepository, new MasterDependencyService(jdbcTemplate),
                new VendorCodeGeneratorService(vendorMasterRepository));
    }

    private VendorMasterRequest validRequest(String gstin) {
        return new VendorMasterRequest("Acme Manpower Services", null, gstin, null, null, null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null, null, null, null, null, true);
    }

    private VendorMaster entityFrom(Long id, String vendorCode, VendorMasterRequest request) {
        VendorMaster vendor = new VendorMaster(vendorCode, request.vendorName(), request.contractStartDate(), request.contractEndDate());
        vendor.setGstin(request.gstin());
        ReflectionTestUtils.setField(vendor, "id", id);
        return vendor;
    }

    @Test
    void create_assignsSystemGeneratedSequentialVendorCode() {
        when(vendorMasterRepository.nextVendorCodeNumber()).thenReturn(1);
        when(vendorMasterRepository.saveAndFlush(any(VendorMaster.class)))
                .thenAnswer(invocation -> {
                    VendorMaster vendor = invocation.getArgument(0);
                    ReflectionTestUtils.setField(vendor, "id", 1L);
                    return vendor;
                });

        VendorMasterResponse response = vendorMasterService.create(validRequest(null));

        assertThat(response.vendorCode()).isEqualTo("VND-0001");
    }

    @Test
    void create_normalizedGstin_isPersistedUppercase() {
        when(vendorMasterRepository.nextVendorCodeNumber()).thenReturn(1);
        when(vendorMasterRepository.existsByGstinIgnoreCase("22AAAAA0000A1Z5")).thenReturn(false);
        when(vendorMasterRepository.saveAndFlush(any(VendorMaster.class)))
                .thenAnswer(invocation -> {
                    VendorMaster vendor = invocation.getArgument(0);
                    ReflectionTestUtils.setField(vendor, "id", 1L);
                    return vendor;
                });

        // VendorMasterRequest's own compact constructor normalizes a raw client value.
        VendorMasterRequest request = new VendorMasterRequest("Acme Manpower Services", null, " 22aaaaa0000a1z5 ", null, null, null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null, null, null, null, null, true);

        VendorMasterResponse response = vendorMasterService.create(request);

        assertThat(response.gstin()).isEqualTo("22AAAAA0000A1Z5");
    }

    @Test
    void create_whenGstinAlreadyRegistered_throwsDuplicateResourceExceptionWithGstinInMessage() {
        when(vendorMasterRepository.nextVendorCodeNumber()).thenReturn(1);
        when(vendorMasterRepository.existsByGstinIgnoreCase("22AAAAA0000A1Z5")).thenReturn(true);

        assertThatThrownBy(() -> vendorMasterService.create(validRequest("22AAAAA0000A1Z5")))
                .isInstanceOf(DuplicateResourceException.class)
                .isInstanceOf(MasterDataConflictException.class)
                .hasMessage("A vendor with GSTIN 22AAAAA0000A1Z5 already exists.");

        verify(vendorMasterRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_whenGstinFailsFormatCheck_throwsMasterDataValidationException() {
        when(vendorMasterRepository.nextVendorCodeNumber()).thenReturn(1);

        assertThatThrownBy(() -> vendorMasterService.create(validRequest("NOT-A-GSTIN")))
                .isInstanceOf(MasterDataValidationException.class);

        verify(vendorMasterRepository, never()).saveAndFlush(any());
    }

    @Test
    void update_whenGstinBelongsToAnotherVendor_throwsDuplicateResourceException() {
        VendorMaster existing = entityFrom(5L, "VND-0005", validRequest(null));
        when(vendorMasterRepository.findById(5L)).thenReturn(java.util.Optional.of(existing));
        when(vendorMasterRepository.existsByGstinIgnoreCaseAndIdNot("22AAAAA0000A1Z5", 5L)).thenReturn(true);

        assertThatThrownBy(() -> vendorMasterService.update(5L, validRequest("22AAAAA0000A1Z5")))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void update_neverChangesVendorCode() {
        VendorMaster existing = entityFrom(6L, "VND-0006", validRequest(null));
        when(vendorMasterRepository.findById(6L)).thenReturn(java.util.Optional.of(existing));
        when(vendorMasterRepository.saveAndFlush(any(VendorMaster.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VendorMasterResponse response = vendorMasterService.update(6L, validRequest(null));

        assertThat(response.vendorCode()).isEqualTo("VND-0006");
        verify(vendorMasterRepository, never()).nextVendorCodeNumber();
    }

    @Test
    void create_whenDatabaseRejectsSave_throwsMasterDataConflictException() {
        when(vendorMasterRepository.nextVendorCodeNumber()).thenReturn(1);
        when(vendorMasterRepository.saveAndFlush(any(VendorMaster.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> vendorMasterService.create(validRequest(null)))
                .isInstanceOf(MasterDataConflictException.class);
    }
}
