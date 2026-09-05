package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.HolidayBulkUploadResult;
import in.gov.jci.hrms.dto.HolidayMasterCreateRequest;
import in.gov.jci.hrms.dto.HolidayMasterRow;
import in.gov.jci.hrms.dto.HolidayMasterUpdateRequest;
import in.gov.jci.hrms.entity.AttendancePayrollCutoff;
import in.gov.jci.hrms.entity.Holiday;
import in.gov.jci.hrms.entity.HolidayType;
import in.gov.jci.hrms.entity.StateMaster;
import in.gov.jci.hrms.entity.StateType;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.exception.MasterDataValidationException;
import in.gov.jci.hrms.repository.AttendancePayrollCutoffRepository;
import in.gov.jci.hrms.repository.HolidayRepository;
import in.gov.jci.hrms.repository.StateMasterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HolidayMasterServiceTest {

    @Mock
    private HolidayRepository holidayRepository;
    @Mock
    private StateMasterRepository stateMasterRepository;
    @Mock
    private AttendancePayrollCutoffRepository attendancePayrollCutoffRepository;

    private HolidayMasterService service;

    private StateMaster wb;
    private StateMaster br;
    private StateMaster as;

    @BeforeEach
    void setUp() {
        service = new HolidayMasterService(holidayRepository, stateMasterRepository, attendancePayrollCutoffRepository);
        wb = new StateMaster("WB", "West Bengal", StateType.STATE, true);
        br = new StateMaster("BR", "Bihar", StateType.STATE, true);
        as = new StateMaster("AS", "Assam", StateType.STATE, true);
    }

    private Holiday savedHolidayWithId(Holiday holiday, Long id) {
        ReflectionTestUtils.setField(holiday, "id", id);
        return holiday;
    }

    @Test
    void create_withAllStateCode_persistsOneNationalRow() {
        when(stateMasterRepository.findByActiveTrueOrderByStateNameAsc()).thenReturn(List.of(wb, br));
        when(holidayRepository.saveAndFlush(any(Holiday.class))).thenAnswer(inv -> savedHolidayWithId(inv.getArgument(0), 1L));

        HolidayMasterCreateRequest request = new HolidayMasterCreateRequest(
                "Republic Day", LocalDate.of(2026, 1, 26), HolidayType.GAZETTED, List.of("ALL"), null);

        List<HolidayMasterRow> result = service.create(request);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).stateCode()).isEqualTo("ALL");
        assertThat(result.get(0).stateName()).isEqualTo("All India / Central");
        assertThat(result.get(0).isRestricted()).isFalse();
    }

    @Test
    void create_withMultipleSpecificStates_fansOutOneRowPerState() {
        // A 3rd active state (AS) not selected below, so WB+BR is a genuine partial selection, not "every active state".
        when(stateMasterRepository.findByActiveTrueOrderByStateNameAsc()).thenReturn(List.of(wb, br, as));
        when(stateMasterRepository.findByStateCode("WB")).thenReturn(Optional.of(wb));
        when(stateMasterRepository.findByStateCode("BR")).thenReturn(Optional.of(br));
        when(stateMasterRepository.findByStateNameIgnoreCase("West Bengal")).thenReturn(Optional.of(wb));
        when(stateMasterRepository.findByStateNameIgnoreCase("Bihar")).thenReturn(Optional.of(br));
        when(holidayRepository.saveAndFlush(any(Holiday.class))).thenAnswer(inv -> savedHolidayWithId(inv.getArgument(0), 2L));

        HolidayMasterCreateRequest request = new HolidayMasterCreateRequest(
                "Regional RH", LocalDate.of(2026, 10, 2), HolidayType.RESTRICTED, List.of("WB", "BR"), "Durga Puja adjunct");

        List<HolidayMasterRow> result = service.create(request);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(HolidayMasterRow::stateCode).containsExactlyInAnyOrder("WB", "BR");
        assertThat(result).allMatch(HolidayMasterRow::isRestricted);
    }

    @Test
    void create_withUnknownStateCode_throwsMasterDataValidationException() {
        when(stateMasterRepository.findByActiveTrueOrderByStateNameAsc()).thenReturn(List.of(wb, br));
        when(stateMasterRepository.findByStateCode("ZZ")).thenReturn(Optional.empty());

        HolidayMasterCreateRequest request = new HolidayMasterCreateRequest(
                "Bad State", LocalDate.of(2026, 5, 1), HolidayType.RESTRICTED, List.of("ZZ"), null);

        assertThatThrownBy(() -> service.create(request)).isInstanceOf(MasterDataValidationException.class);
    }

    @Test
    void create_whenAllActiveStatesSelectedIndividually_stillPersistsAsNational() {
        when(stateMasterRepository.findByActiveTrueOrderByStateNameAsc()).thenReturn(List.of(wb, br));
        when(holidayRepository.saveAndFlush(any(Holiday.class))).thenAnswer(inv -> savedHolidayWithId(inv.getArgument(0), 3L));

        HolidayMasterCreateRequest request = new HolidayMasterCreateRequest(
                "Effectively National", LocalDate.of(2026, 8, 15), HolidayType.GAZETTED, List.of("WB", "BR"), null);
        // Selecting every active state (WB + BR, the only 2 mocked as active) collapses to one national row.
        List<HolidayMasterRow> result = service.create(request);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).stateCode()).isEqualTo("ALL");
    }

    @Test
    void update_changesFieldsAndTranslatesStateCode() {
        Holiday existing = new Holiday(LocalDate.of(2026, 1, 26), "Old Name", HolidayType.GAZETTED, null);
        ReflectionTestUtils.setField(existing, "id", 5L);
        when(holidayRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(stateMasterRepository.findByStateCode("WB")).thenReturn(Optional.of(wb));
        when(stateMasterRepository.findByStateNameIgnoreCase("West Bengal")).thenReturn(Optional.of(wb));
        when(holidayRepository.saveAndFlush(any(Holiday.class))).thenAnswer(inv -> inv.getArgument(0));

        HolidayMasterUpdateRequest request = new HolidayMasterUpdateRequest(
                "New Name", LocalDate.of(2026, 1, 27), HolidayType.RESTRICTED, "WB", "Updated notes");

        HolidayMasterRow row = service.update(5L, request);

        assertThat(row.holidayName()).isEqualTo("New Name");
        assertThat(row.holidayDate()).isEqualTo(LocalDate.of(2026, 1, 27));
        assertThat(row.stateCode()).isEqualTo("WB");
        assertThat(row.description()).isEqualTo("Updated notes");
    }

    @Test
    void update_whenMissing_throwsMasterDataNotFoundException() {
        when(holidayRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, new HolidayMasterUpdateRequest("X", LocalDate.now(), HolidayType.GAZETTED, "ALL", null)))
                .isInstanceOf(MasterDataNotFoundException.class);
    }

    @Test
    void delete_whenNotInAFrozenCutoff_softDeletes() {
        Holiday holiday = new Holiday(LocalDate.of(2026, 3, 10), "Holi", HolidayType.GAZETTED, null);
        ReflectionTestUtils.setField(holiday, "id", 7L);
        when(holidayRepository.findById(7L)).thenReturn(Optional.of(holiday));
        when(attendancePayrollCutoffRepository.findFirstByPeriodStartLessThanEqualAndPeriodEndGreaterThanEqualAndFrozenTrue(
                holiday.getHolidayDate(), holiday.getHolidayDate())).thenReturn(Optional.empty());

        service.delete(7L);

        assertThat(holiday.getDeletedAt()).isNotNull();
    }

    @Test
    void delete_whenWithinAFrozenPayrollCycle_throwsBusinessRuleViolationException() {
        Holiday holiday = new Holiday(LocalDate.of(2026, 3, 10), "Holi", HolidayType.GAZETTED, null);
        ReflectionTestUtils.setField(holiday, "id", 8L);
        AttendancePayrollCutoff cutoff = org.mockito.Mockito.mock(AttendancePayrollCutoff.class);
        when(holidayRepository.findById(8L)).thenReturn(Optional.of(holiday));
        when(attendancePayrollCutoffRepository.findFirstByPeriodStartLessThanEqualAndPeriodEndGreaterThanEqualAndFrozenTrue(
                holiday.getHolidayDate(), holiday.getHolidayDate())).thenReturn(Optional.of(cutoff));

        assertThatThrownBy(() -> service.delete(8L)).isInstanceOf(BusinessRuleViolationException.class);
        assertThat(holiday.getDeletedAt()).isNull();
    }

    @Test
    void list_withAllOrBlankStateFilter_appliesNoStateFilterAtAll() {
        Holiday national = new Holiday(LocalDate.of(2026, 1, 26), "Republic Day", HolidayType.GAZETTED, null);
        Holiday wbSpecific = new Holiday(LocalDate.of(2026, 4, 15), "Poila Boishakh", HolidayType.RESTRICTED, "West Bengal");
        when(holidayRepository.findByHolidayDateBetween(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
                .thenReturn(List.of(national, wbSpecific));

        List<HolidayMasterRow> result = service.list(2026, "ALL", null);

        assertThat(result).extracting(HolidayMasterRow::holidayName).containsExactly("Republic Day", "Poila Boishakh");
    }

    @Test
    void list_withSpecificStateFilter_includesThatStatesRowsAndEveryNationalRow() {
        Holiday national = new Holiday(LocalDate.of(2026, 1, 26), "Republic Day", HolidayType.GAZETTED, null);
        Holiday wbSpecific = new Holiday(LocalDate.of(2026, 4, 15), "Poila Boishakh", HolidayType.RESTRICTED, "West Bengal");
        Holiday brSpecific = new Holiday(LocalDate.of(2026, 3, 22), "Bihar Diwas", HolidayType.RESTRICTED, "Bihar");
        when(holidayRepository.findByHolidayDateBetween(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
                .thenReturn(List.of(national, wbSpecific, brSpecific));
        when(stateMasterRepository.findByStateCode("WB")).thenReturn(Optional.of(wb));

        List<HolidayMasterRow> wbFiltered = service.list(2026, "WB", null);

        assertThat(wbFiltered).extracting(HolidayMasterRow::holidayName).containsExactly("Republic Day", "Poila Boishakh");
    }

    @Test
    void list_combinesStateAndTypeFilters() {
        Holiday national = new Holiday(LocalDate.of(2026, 1, 26), "Republic Day", HolidayType.GAZETTED, null);
        Holiday wbSpecific = new Holiday(LocalDate.of(2026, 4, 15), "Poila Boishakh", HolidayType.RESTRICTED, "West Bengal");
        when(holidayRepository.findByHolidayDateBetween(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
                .thenReturn(List.of(national, wbSpecific));
        when(stateMasterRepository.findByStateCode("WB")).thenReturn(Optional.of(wb));

        List<HolidayMasterRow> wbRestrictedOnly = service.list(2026, "WB", "RESTRICTED");

        assertThat(wbRestrictedOnly).extracting(HolidayMasterRow::holidayName).containsExactly("Poila Boishakh");
    }

    @Test
    void bulkUpload_importsValidRowsAndReportsInvalidOnesWithoutFailingTheWholeFile() {
        when(stateMasterRepository.findByActiveTrueOrderByStateNameAsc()).thenReturn(List.of(wb, br));
        when(stateMasterRepository.findByStateCode("WB")).thenReturn(Optional.of(wb));
        when(holidayRepository.saveAndFlush(any(Holiday.class))).thenAnswer(inv -> savedHolidayWithId(inv.getArgument(0), 9L));

        String csv = "holidayDate,holidayName,holidayType,stateCodes,isRestricted\n"
                + "2026-01-26,Republic Day,GAZETTED,ALL,false\n"
                + "2026-04-15,Poila Boishakh,RESTRICTED,WB,true\n"
                + "not-a-date,Bad Row,GAZETTED,WB,false\n";
        MultipartFile file = new MockMultipartFile("file", "gazette.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        HolidayBulkUploadResult result = service.bulkUpload(file);

        assertThat(result.totalRows()).isEqualTo(3);
        assertThat(result.createdRows()).isEqualTo(2);
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0)).contains("Line 4");
    }
}
