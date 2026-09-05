package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.PostMasterRequest;
import in.gov.jci.hrms.dto.PostMasterResponse;
import in.gov.jci.hrms.dto.PostStatusUpdateRequest;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.PostIncumbency;
import in.gov.jci.hrms.entity.PostMaster;
import in.gov.jci.hrms.entity.VacancyStatus;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataInUseException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.exception.MasterDataValidationException;
import in.gov.jci.hrms.repository.DepartmentRepository;
import in.gov.jci.hrms.repository.DepartmentalPurchaseCentreRepository;
import in.gov.jci.hrms.repository.DesignationRepository;
import in.gov.jci.hrms.repository.PostIncumbencyRepository;
import in.gov.jci.hrms.repository.PostMasterRepository;
import in.gov.jci.hrms.repository.RegionalOfficeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostMasterServiceTest {

    private static final Long DEPARTMENT_ID = 10L;
    private static final Long DESIGNATION_ID = 20L;

    @Mock
    private PostMasterRepository postMasterRepository;
    @Mock
    private PostIncumbencyRepository postIncumbencyRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private DesignationRepository designationRepository;
    @Mock
    private RegionalOfficeRepository regionalOfficeRepository;
    @Mock
    private DepartmentalPurchaseCentreRepository dpcRepository;

    private PostMasterService postMasterService;

    private Department department;
    private Designation designation;

    @BeforeEach
    void setUp() {
        postMasterService = new PostMasterService(postMasterRepository, postIncumbencyRepository,
                departmentRepository, designationRepository, regionalOfficeRepository, dpcRepository);

        department = new Department("ENG", "Engineering");
        ReflectionTestUtils.setField(department, "id", DEPARTMENT_ID);

        designation = new Designation("Manager");
        ReflectionTestUtils.setField(designation, "id", DESIGNATION_ID);
    }

    private PostMasterRequest validRequest() {
        return new PostMasterRequest("PC-001", "Chief Engineer", DEPARTMENT_ID, DESIGNATION_ID,
                null, null, null, null, null, true, true);
    }

    private PostMaster entityFrom(Long id, PostMasterRequest request) {
        PostMaster post = new PostMaster(request.postCode(), request.title(), department, designation, request.active());
        ReflectionTestUtils.setField(post, "id", id);
        return post;
    }

    @Test
    void create_savesAndReturnsResponse() {
        PostMasterRequest request = validRequest();
        when(departmentRepository.findById(DEPARTMENT_ID)).thenReturn(Optional.of(department));
        when(designationRepository.findById(DESIGNATION_ID)).thenReturn(Optional.of(designation));
        when(postMasterRepository.saveAndFlush(any(PostMaster.class))).thenReturn(entityFrom(1L, request));

        PostMasterResponse response = postMasterService.create(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.postCode()).isEqualTo("PC-001");
        assertThat(response.departmentName()).isEqualTo("Engineering");
    }

    @Test
    void create_whenReportingPostIsSelf_throwsMasterDataValidationException() {
        // Self-reference can only be caught on update (there's no id yet on create),
        // so this exercises resolveReportingPost's guard via update() instead.
        PostMaster existing = entityFrom(7L, validRequest());
        when(postMasterRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(departmentRepository.findById(DEPARTMENT_ID)).thenReturn(Optional.of(department));
        when(designationRepository.findById(DESIGNATION_ID)).thenReturn(Optional.of(designation));

        PostMasterRequest selfReporting = new PostMasterRequest("PC-001", "Chief Engineer", DEPARTMENT_ID, DESIGNATION_ID,
                null, null, 7L, null, null, true, true);

        assertThatThrownBy(() -> postMasterService.update(7L, selfReporting))
                .isInstanceOf(MasterDataValidationException.class);
    }

    @Test
    void create_whenCodeAlreadyInUse_throwsMasterDataConflictException() {
        when(departmentRepository.findById(DEPARTMENT_ID)).thenReturn(Optional.of(department));
        when(designationRepository.findById(DESIGNATION_ID)).thenReturn(Optional.of(designation));
        when(postMasterRepository.saveAndFlush(any(PostMaster.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> postMasterService.create(validRequest()))
                .isInstanceOf(MasterDataConflictException.class);
    }

    @Test
    void getById_whenMissing_throwsMasterDataNotFoundException() {
        when(postMasterRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postMasterService.getById(99L))
                .isInstanceOf(MasterDataNotFoundException.class)
                .hasMessageContaining("Post");
    }

    @Test
    void delete_whenActivelyOccupied_throwsMasterDataInUseException() {
        PostMaster post = entityFrom(2L, validRequest());
        when(postMasterRepository.findById(2L)).thenReturn(Optional.of(post));
        when(postIncumbencyRepository.existsByPostIdAndActiveTrue(2L)).thenReturn(true);

        assertThatThrownBy(() -> postMasterService.delete(2L))
                .isInstanceOf(MasterDataInUseException.class);
    }

    @Test
    void delete_whenReferencedByAnotherPostsReportingLine_throwsMasterDataInUseException() {
        PostMaster post = entityFrom(3L, validRequest());
        when(postMasterRepository.findById(3L)).thenReturn(Optional.of(post));
        when(postIncumbencyRepository.existsByPostIdAndActiveTrue(3L)).thenReturn(false);
        when(postMasterRepository.existsByOperationalReportingPostIdOrAdministrativeReportingPostId(3L, 3L)).thenReturn(true);

        assertThatThrownBy(() -> postMasterService.delete(3L))
                .isInstanceOf(MasterDataInUseException.class);
    }

    @Test
    void delete_whenUnreferenced_softDeletesAndDeactivates() {
        PostMaster post = entityFrom(4L, validRequest());
        when(postMasterRepository.findById(4L)).thenReturn(Optional.of(post));
        when(postIncumbencyRepository.existsByPostIdAndActiveTrue(4L)).thenReturn(false);
        when(postMasterRepository.existsByOperationalReportingPostIdOrAdministrativeReportingPostId(4L, 4L)).thenReturn(false);

        postMasterService.delete(4L);

        assertThat(post.getDeletedAt()).isNotNull();
        assertThat(post.isActive()).isFalse();
    }

    @Test
    void update_whenReportingHierarchyFormsDeeperCycle_throwsMasterDataValidationException() {
        // A (id 1) -> B (id 2) -> C (id 3) already exists (C's operational reports to B, B's to A);
        // now trying to set A's operational reporting post to C would close the loop C -> A.
        PostMaster postA = entityFrom(1L, validRequest());
        PostMaster postB = entityFrom(2L, validRequest());
        PostMaster postC = entityFrom(3L, validRequest());
        postB.setOperationalReportingPost(postA);
        postC.setOperationalReportingPost(postB);

        when(postMasterRepository.findById(1L)).thenReturn(Optional.of(postA));
        when(postMasterRepository.findById(3L)).thenReturn(Optional.of(postC));
        when(departmentRepository.findById(DEPARTMENT_ID)).thenReturn(Optional.of(department));
        when(designationRepository.findById(DESIGNATION_ID)).thenReturn(Optional.of(designation));

        PostMasterRequest cyclical = new PostMasterRequest("PC-001", "Chief Engineer", DEPARTMENT_ID, DESIGNATION_ID,
                null, null, 3L, null, null, true, true);

        assertThatThrownBy(() -> postMasterService.update(1L, cyclical))
                .isInstanceOf(MasterDataValidationException.class)
                .hasMessageContaining("circular");
    }

    @Test
    void updateStatus_toFrozen_setsVacancyStatusRegardlessOfOccupancy() {
        PostMaster post = entityFrom(5L, validRequest());
        post.setVacancyStatus(VacancyStatus.OCCUPIED);
        when(postMasterRepository.findById(5L)).thenReturn(Optional.of(post));

        PostMasterResponse response = postMasterService.updateStatus(5L, new PostStatusUpdateRequest(VacancyStatus.FROZEN, "Budget freeze"));

        assertThat(response.vacancyStatus()).isEqualTo(VacancyStatus.FROZEN);
    }

    @Test
    void updateStatus_toAbolished_whenActivelyOccupied_throwsMasterDataInUseException() {
        PostMaster post = entityFrom(6L, validRequest());
        when(postMasterRepository.findById(6L)).thenReturn(Optional.of(post));
        when(postIncumbencyRepository.existsByPostIdAndActiveTrue(6L)).thenReturn(true);

        assertThatThrownBy(() -> postMasterService.updateStatus(6L, new PostStatusUpdateRequest(VacancyStatus.ABOLISHED, null)))
                .isInstanceOf(MasterDataInUseException.class);
    }

    @Test
    void updateStatus_unfreezeWithActiveIncumbent_resolvesToOccupied() {
        PostMaster post = entityFrom(7L, validRequest());
        post.setVacancyStatus(VacancyStatus.FROZEN);
        when(postMasterRepository.findById(7L)).thenReturn(Optional.of(post));
        when(postIncumbencyRepository.existsByPostIdAndActiveTrue(7L)).thenReturn(true);

        PostMasterResponse response = postMasterService.updateStatus(7L, new PostStatusUpdateRequest(VacancyStatus.VACANT, "Unfreeze"));

        assertThat(response.vacancyStatus()).isEqualTo(VacancyStatus.OCCUPIED);
    }

    @Test
    void updateStatus_unfreezeWithoutActiveIncumbent_resolvesToVacant() {
        PostMaster post = entityFrom(8L, validRequest());
        post.setVacancyStatus(VacancyStatus.FROZEN);
        when(postMasterRepository.findById(8L)).thenReturn(Optional.of(post));
        when(postIncumbencyRepository.existsByPostIdAndActiveTrue(8L)).thenReturn(false);

        PostMasterResponse response = postMasterService.updateStatus(8L, new PostStatusUpdateRequest(VacancyStatus.VACANT, null));

        assertThat(response.vacancyStatus()).isEqualTo(VacancyStatus.VACANT);
    }

    @Test
    void updateStatus_unfreezeWhenNotCurrentlyFrozen_throwsMasterDataValidationException() {
        PostMaster post = entityFrom(9L, validRequest());
        post.setVacancyStatus(VacancyStatus.VACANT);
        when(postMasterRepository.findById(9L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> postMasterService.updateStatus(9L, new PostStatusUpdateRequest(VacancyStatus.VACANT, null)))
                .isInstanceOf(MasterDataValidationException.class);
    }

    @Test
    void summary_countsEachVacancyStatus() {
        when(postMasterRepository.count()).thenReturn(10L);
        when(postMasterRepository.countByVacancyStatus(VacancyStatus.OCCUPIED)).thenReturn(4L);
        when(postMasterRepository.countByVacancyStatus(VacancyStatus.VACANT)).thenReturn(3L);
        when(postMasterRepository.countByVacancyStatus(VacancyStatus.FROZEN)).thenReturn(2L);
        when(postMasterRepository.countByVacancyStatus(VacancyStatus.ABOLISHED)).thenReturn(1L);

        var summary = postMasterService.summary();

        assertThat(summary.totalSanctioned()).isEqualTo(10L);
        assertThat(summary.occupied()).isEqualTo(4L);
        assertThat(summary.vacant()).isEqualTo(3L);
        assertThat(summary.frozen()).isEqualTo(2L);
        assertThat(summary.abolished()).isEqualTo(1L);
    }

    @Test
    void incumbencyHistory_whenPostMissing_throwsMasterDataNotFoundException() {
        when(postMasterRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postMasterService.incumbencyHistory(42L))
                .isInstanceOf(MasterDataNotFoundException.class);
    }

    @Test
    void incumbencyHistory_returnsChronologicalLedger() {
        PostMaster post = entityFrom(11L, validRequest());
        when(postMasterRepository.findById(11L)).thenReturn(Optional.of(post));

        in.gov.jci.hrms.entity.Employee employee = new in.gov.jci.hrms.entity.Employee(
                "0001", "Asha", "Rao", "asha.rao@example.com", LocalDate.of(2024, 1, 15), department, designation);
        ReflectionTestUtils.setField(employee, "id", 100L);
        PostIncumbency incumbency = new PostIncumbency(post, employee, in.gov.jci.hrms.entity.AssignmentType.SUBSTANTIVE, LocalDate.of(2024, 1, 15));
        when(postIncumbencyRepository.findByPostIdOrderByStartDateDesc(11L)).thenReturn(List.of(incumbency));

        var history = postMasterService.incumbencyHistory(11L);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).employeeCode()).isEqualTo("0001");
    }
}
