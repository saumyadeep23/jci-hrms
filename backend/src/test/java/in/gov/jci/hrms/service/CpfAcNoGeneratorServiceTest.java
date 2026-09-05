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
class CpfAcNoGeneratorServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    /** nextCpfAcNoNumber() itself already returns MAX+1 (see its own query) - 3341 being the highest existing numeric cpf_ac_no means the repository reports 3342. */
    @Test
    void generateNext_returnsOneMoreThanCurrentMax() {
        when(employeeRepository.nextCpfAcNoNumber()).thenReturn(3342L);
        CpfAcNoGeneratorService generator = new CpfAcNoGeneratorService(employeeRepository);

        String next = generator.generateNext();

        assertThat(next).isEqualTo("3342");
        verify(employeeRepository).acquireCpfAcNoGenerationLock();
    }

    @Test
    void generateNext_withNoExistingNumericValues_startsAtOne() {
        when(employeeRepository.nextCpfAcNoNumber()).thenReturn(1L);
        CpfAcNoGeneratorService generator = new CpfAcNoGeneratorService(employeeRepository);

        assertThat(generator.generateNext()).isEqualTo("1");
    }
}
