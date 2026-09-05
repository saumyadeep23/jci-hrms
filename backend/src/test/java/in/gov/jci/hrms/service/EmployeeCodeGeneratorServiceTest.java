package in.gov.jci.hrms.service;

import in.gov.jci.hrms.repository.EmployeeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeCodeGeneratorServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Test
    void generateNext_padsToFourDigits() {
        when(employeeRepository.nextEmployeeCodeNumber()).thenReturn(47);
        EmployeeCodeGeneratorService generator = new EmployeeCodeGeneratorService(employeeRepository);

        String code = generator.generateNext();

        assertThat(code).isEqualTo("0047");
        verify(employeeRepository).acquireEmployeeCodeGenerationLock();
    }

    @Test
    void generateNext_neverTruncatesBeyondFourDigits() {
        when(employeeRepository.nextEmployeeCodeNumber()).thenReturn(1246);
        EmployeeCodeGeneratorService generator = new EmployeeCodeGeneratorService(employeeRepository);

        assertThat(generator.generateNext()).isEqualTo("1246");
    }

    @Test
    void generateNext_beyondFourDigits_widensRatherThanTruncating() {
        when(employeeRepository.nextEmployeeCodeNumber()).thenReturn(12345);
        EmployeeCodeGeneratorService generator = new EmployeeCodeGeneratorService(employeeRepository);

        assertThat(generator.generateNext()).isEqualTo("12345");
    }
}
