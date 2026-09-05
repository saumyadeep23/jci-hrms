package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.LeaveTypeMasterResponse;
import in.gov.jci.hrms.dto.LeaveTypeMasterUpdateRequest;
import in.gov.jci.hrms.dto.LeaveTypeRequest;
import in.gov.jci.hrms.dto.LeaveTypeResponse;
import in.gov.jci.hrms.entity.EmploymentCategory;
import in.gov.jci.hrms.entity.LeaveType;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataInUseException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.LeaveBalanceRepository;
import in.gov.jci.hrms.repository.LeaveTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaveTypeServiceTest {

    @Mock
    private LeaveTypeRepository leaveTypeRepository;
    @Mock
    private LeaveBalanceRepository leaveBalanceRepository;

    private LeaveTypeService leaveTypeService;

    @BeforeEach
    void setUp() {
        leaveTypeService = new LeaveTypeService(leaveTypeRepository, leaveBalanceRepository);
    }

    private LeaveTypeRequest validRequest() {
        return new LeaveTypeRequest("CL", "Casual Leave", BigDecimal.valueOf(8.0), null, false, null, true);
    }

    private LeaveType entityFrom(Long id, LeaveTypeRequest request) {
        LeaveType leaveType = new LeaveType(request.code(), request.name(), request.annualQuota(),
                request.isEncashable(), request.active());
        leaveType.setMaxAccumulationDays(request.maxAccumulationDays());
        leaveType.setCareerLimitDays(request.careerLimitDays());
        ReflectionTestUtils.setField(leaveType, "id", id);
        return leaveType;
    }

    @Test
    void create_savesAndReturnsResponse() {
        LeaveTypeRequest request = validRequest();
        when(leaveTypeRepository.saveAndFlush(any(LeaveType.class))).thenReturn(entityFrom(1L, request));

        LeaveTypeResponse response = leaveTypeService.create(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.code()).isEqualTo("CL");
    }

    @Test
    void create_whenCodeAlreadyInUse_throwsMasterDataConflictException() {
        when(leaveTypeRepository.saveAndFlush(any(LeaveType.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> leaveTypeService.create(validRequest()))
                .isInstanceOf(MasterDataConflictException.class);
    }

    @Test
    void getById_whenMissing_throwsMasterDataNotFoundException() {
        when(leaveTypeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leaveTypeService.getById(99L))
                .isInstanceOf(MasterDataNotFoundException.class)
                .hasMessageContaining("Leave Type");
    }

    @Test
    void delete_whenReferencedByLeaveBalance_throwsMasterDataInUseException() {
        LeaveType leaveType = entityFrom(2L, validRequest());
        when(leaveTypeRepository.findById(2L)).thenReturn(Optional.of(leaveType));
        when(leaveBalanceRepository.existsByLeaveTypeId(2L)).thenReturn(true);

        assertThatThrownBy(() -> leaveTypeService.delete(2L))
                .isInstanceOf(MasterDataInUseException.class);

        assertThat(leaveType.getDeletedAt()).isNull();
    }

    @Test
    void delete_whenNotReferenced_softDeletes() {
        LeaveType leaveType = entityFrom(3L, validRequest());
        when(leaveTypeRepository.findById(3L)).thenReturn(Optional.of(leaveType));
        when(leaveBalanceRepository.existsByLeaveTypeId(3L)).thenReturn(false);

        leaveTypeService.delete(3L);

        assertThat(leaveType.getDeletedAt()).isNotNull();
    }

    // ---- Leave Type Master (ALMS operational gap #2) ----

    @Test
    void listForMaster_mapsEligibleCategoriesAndDerivesIsAccumulative() {
        LeaveType el = entityFrom(4L, new LeaveTypeRequest("EL", "Earned Leave", BigDecimal.valueOf(30.0), 300, true, null, true));
        el.setEligibleCategories(Set.of(EmploymentCategory.REGULAR));
        LeaveType cl = entityFrom(5L, validRequest());
        when(leaveTypeRepository.findAll(any(Sort.class))).thenReturn(List.of(el, cl));

        List<LeaveTypeMasterResponse> result = leaveTypeService.listForMaster();

        LeaveTypeMasterResponse elResponse = result.stream().filter(r -> r.code().equals("EL")).findFirst().orElseThrow();
        assertThat(elResponse.isAccumulative()).isTrue();
        assertThat(elResponse.maxAccumulationCap()).isEqualTo(300);
        assertThat(elResponse.eligibleCategories()).containsExactly(EmploymentCategory.REGULAR);

        LeaveTypeMasterResponse clResponse = result.stream().filter(r -> r.code().equals("CL")).findFirst().orElseThrow();
        assertThat(clResponse.isAccumulative()).isFalse();
    }

    @Test
    void updateMaster_updatesCapEncashableAndEligibility() {
        LeaveType leaveType = entityFrom(6L, validRequest());
        when(leaveTypeRepository.findById(6L)).thenReturn(Optional.of(leaveType));
        when(leaveTypeRepository.saveAndFlush(any(LeaveType.class))).thenAnswer(inv -> inv.getArgument(0));
        LeaveTypeMasterUpdateRequest request = new LeaveTypeMasterUpdateRequest(15, true,
                Set.of(EmploymentCategory.REGULAR, EmploymentCategory.CONTRACTUAL));

        LeaveTypeMasterResponse response = leaveTypeService.updateMaster(6L, request);

        assertThat(response.maxAccumulationCap()).isEqualTo(15);
        assertThat(response.isEncashable()).isTrue();
        assertThat(response.eligibleCategories()).containsExactlyInAnyOrder(EmploymentCategory.REGULAR, EmploymentCategory.CONTRACTUAL);
    }

    @Test
    void updateMaster_whenMissing_throwsMasterDataNotFoundException() {
        when(leaveTypeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leaveTypeService.updateMaster(99L, new LeaveTypeMasterUpdateRequest(null, false, Set.of(EmploymentCategory.REGULAR))))
                .isInstanceOf(MasterDataNotFoundException.class);
    }
}
