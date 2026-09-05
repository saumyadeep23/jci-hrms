package in.gov.jci.hrms.service;

import in.gov.jci.hrms.repository.VendorMasterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VendorCodeGeneratorServiceTest {

    @Mock
    private VendorMasterRepository vendorMasterRepository;

    @Test
    void generateNext_padsToFourDigits() {
        when(vendorMasterRepository.nextVendorCodeNumber()).thenReturn(1);
        VendorCodeGeneratorService generator = new VendorCodeGeneratorService(vendorMasterRepository);

        String code = generator.generateNext();

        assertThat(code).isEqualTo("VND-0001");
        verify(vendorMasterRepository).acquireVendorCodeGenerationLock();
    }

    @Test
    void generateNext_secondVendor_incrementsSequence() {
        when(vendorMasterRepository.nextVendorCodeNumber()).thenReturn(2);
        VendorCodeGeneratorService generator = new VendorCodeGeneratorService(vendorMasterRepository);

        assertThat(generator.generateNext()).isEqualTo("VND-0002");
    }

    @Test
    void generateNext_beyondFourDigits_widensRatherThanTruncating() {
        when(vendorMasterRepository.nextVendorCodeNumber()).thenReturn(12345);
        VendorCodeGeneratorService generator = new VendorCodeGeneratorService(vendorMasterRepository);

        assertThat(generator.generateNext()).isEqualTo("VND-12345");
    }
}
