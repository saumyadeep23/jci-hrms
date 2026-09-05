package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.GradeScaleCreateRequest;
import in.gov.jci.hrms.dto.GradeScaleMasterResponse;
import in.gov.jci.hrms.dto.GradeScaleUpdateRequest;
import in.gov.jci.hrms.entity.Cadre;
import in.gov.jci.hrms.entity.GradeScaleMaster;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.GradeScaleMasterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GradeScaleMasterServiceTest {

    @Mock
    private GradeScaleMasterRepository gradeScaleMasterRepository;

    private GradeScaleMaster grade(String code, Cadre cadre, int level, boolean board, String min, String max) {
        GradeScaleMaster g = new GradeScaleMaster(code, cadre, level, board, new BigDecimal(min), new BigDecimal(max));
        ReflectionTestUtils.setField(g, "id", (long) level);
        return g;
    }

    private GradeScaleCreateRequest createRequest(String code, int level) {
        return new GradeScaleCreateRequest(code, Cadre.EXECUTIVE, level, false, new BigDecimal("40000"), new BigDecimal("140000"),
                new BigDecimal("3.00"), LocalDate.of(2026, 1, 1), null, null, true);
    }

    @Test
    void list_returnsOrderedByHierarchyLevel() {
        GradeScaleMasterService svc = new GradeScaleMasterService(gradeScaleMasterRepository);
        when(gradeScaleMasterRepository.findAllByOrderByHierarchyLevelAsc()).thenReturn(List.of(
                grade("E9", Cadre.BOARD, 1, true, "160000", "290000"),
                grade("E8", Cadre.BOARD, 2, true, "120000", "280000"),
                grade("S1", Cadre.STAFF, 15, false, "19000", "76500")));

        List<GradeScaleMasterResponse> result = svc.list();

        assertThat(result).extracting(GradeScaleMasterResponse::scaleCode).containsExactly("E9", "E8", "S1");
        assertThat(result.get(0).boardLevel()).isTrue();
        assertThat(result.get(2).idaScaleLabel()).contains("19,000").contains("76,500");
    }

    @Test
    void create_savesNewGradeScaleWithAllFields() {
        GradeScaleMasterService svc = new GradeScaleMasterService(gradeScaleMasterRepository);
        when(gradeScaleMasterRepository.saveAndFlush(any())).thenAnswer(inv -> {
            GradeScaleMaster g = inv.getArgument(0);
            ReflectionTestUtils.setField(g, "id", 16L);
            return g;
        });

        GradeScaleMasterResponse response = svc.create(new GradeScaleCreateRequest("E10", Cadre.EXECUTIVE, 16, false,
                new BigDecimal("25000"), new BigDecimal("100000"), new BigDecimal("3.00"), LocalDate.of(2026, 4, 1),
                new BigDecimal("30000.00"), new BigDecimal("35000.00"), true));

        assertThat(response.scaleCode()).isEqualTo("E10");
        assertThat(response.hierarchyLevel()).isEqualTo(16);
        assertThat(response.contractualLumpsum()).isEqualByComparingTo("30000.00");
        assertThat(response.outsourcedCtc()).isEqualByComparingTo("35000.00");
        assertThat(response.active()).isTrue();
    }

    @Test
    void create_duplicateScaleCode_throwsConflict() {
        GradeScaleMasterService svc = new GradeScaleMasterService(gradeScaleMasterRepository);
        when(gradeScaleMasterRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> svc.create(createRequest("E9", 1)))
                .isInstanceOf(MasterDataConflictException.class);
    }

    @Test
    void update_byScaleCode_updatesEveryEditableField() {
        GradeScaleMasterService svc = new GradeScaleMasterService(gradeScaleMasterRepository);
        GradeScaleMaster e0 = grade("E0", Cadre.EXECUTIVE, 10, false, "30000", "120000");
        when(gradeScaleMasterRepository.findByScaleCode("E0")).thenReturn(Optional.of(e0));
        when(gradeScaleMasterRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        GradeScaleMasterResponse response = svc.update("E0", new GradeScaleUpdateRequest(Cadre.EXECUTIVE, 9, false,
                new BigDecimal("32000.00"), new BigDecimal("125000.00"), new BigDecimal("3.50"), LocalDate.of(2026, 4, 1),
                new BigDecimal("45000.00"), new BigDecimal("60000.00"), false));

        assertThat(response.hierarchyLevel()).isEqualTo(9);
        assertThat(response.minimumBasic()).isEqualByComparingTo("32000.00");
        assertThat(response.maximumBasic()).isEqualByComparingTo("125000.00");
        assertThat(response.incrementRate()).isEqualByComparingTo("3.50");
        assertThat(response.contractualLumpsum()).isEqualByComparingTo("45000.00");
        assertThat(response.outsourcedCtc()).isEqualByComparingTo("60000.00");
        assertThat(response.active()).isFalse();
    }

    @Test
    void update_unknownScaleCode_throws() {
        GradeScaleMasterService svc = new GradeScaleMasterService(gradeScaleMasterRepository);
        when(gradeScaleMasterRepository.findByScaleCode("ZZ")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> svc.update("ZZ", new GradeScaleUpdateRequest(Cadre.STAFF, 15, false,
                BigDecimal.ZERO, BigDecimal.ZERO, null, null, null, null, true)))
                .isInstanceOf(MasterDataNotFoundException.class);
    }
}
