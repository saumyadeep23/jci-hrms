package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.DesignationRequest;
import in.gov.jci.hrms.dto.DesignationResponse;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataInUseException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.DesignationRepository;
import in.gov.jci.hrms.repository.GradeScaleMasterRepository;
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
class DesignationServiceTest {

    @Mock
    private DesignationRepository designationRepository;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private GradeScaleMasterRepository gradeScaleMasterRepository;

    private DesignationService designationService;

    @BeforeEach
    void setUp() {
        lenient().when(jdbcTemplate.queryForObject(any(String.class), org.mockito.ArgumentMatchers.eq(Long.class), any(Object.class)))
                .thenReturn(0L);
        designationService = new DesignationService(designationRepository, new MasterDependencyService(jdbcTemplate), gradeScaleMasterRepository);
    }

    private DesignationRequest validRequest() {
        return new DesignationRequest("Backend Developer", "Builds and operates backend services", null);
    }

    private Designation entityFrom(Long id, DesignationRequest request) {
        Designation designation = new Designation(request.title());
        designation.setDescription(request.description());
        ReflectionTestUtils.setField(designation, "id", id);
        return designation;
    }

    @Test
    void create_savesAndReturnsResponse() {
        DesignationRequest request = validRequest();
        when(designationRepository.saveAndFlush(any(Designation.class))).thenReturn(entityFrom(1L, request));

        DesignationResponse response = designationService.create(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.title()).isEqualTo("Backend Developer");
    }

    @Test
    void create_whenTitleAlreadyInUse_throwsMasterDataConflictException() {
        when(designationRepository.saveAndFlush(any(Designation.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> designationService.create(validRequest()))
                .isInstanceOf(MasterDataConflictException.class);
    }

    @Test
    void getById_whenMissing_throwsMasterDataNotFoundException() {
        when(designationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> designationService.getById(99L))
                .isInstanceOf(MasterDataNotFoundException.class)
                .hasMessageContaining("Designation");
    }

    @Test
    void delete_whenReferencedByActiveEmployee_throwsMasterDataInUseException() {
        Designation designation = entityFrom(2L, validRequest());
        when(designationRepository.findById(2L)).thenReturn(Optional.of(designation));
        when(jdbcTemplate.queryForObject(any(String.class), org.mockito.ArgumentMatchers.eq(Long.class), any(Object.class)))
                .thenReturn(1L);

        assertThatThrownBy(() -> designationService.delete(2L))
                .isInstanceOf(MasterDataInUseException.class);

        assertThat(designation.getDeletedAt()).isNull();
    }

    @Test
    void delete_whenNotReferenced_softDeletes() {
        Designation designation = entityFrom(3L, validRequest());
        when(designationRepository.findById(3L)).thenReturn(Optional.of(designation));

        designationService.delete(3L);

        assertThat(designation.getDeletedAt()).isNotNull();
        verify(designationRepository, never()).deleteById(any());
    }
}
