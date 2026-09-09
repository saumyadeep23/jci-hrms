package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.CompositeDependentEntry;
import in.gov.jci.hrms.dto.CompositeNomineeEntry;
import in.gov.jci.hrms.dto.EmployeeFamilyNomineeCompositeRequest;
import in.gov.jci.hrms.dto.EmployeeFamilyNomineeCompositeResponse;
import in.gov.jci.hrms.entity.CeaEligibilityStatus;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeDependent;
import in.gov.jci.hrms.entity.EmployeeNominee;
import in.gov.jci.hrms.entity.FamilyRelationshipType;
import in.gov.jci.hrms.entity.NominationType;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.repository.EmployeeDependentRepository;
import in.gov.jci.hrms.repository.EmployeeFamilyDetailsRepository;
import in.gov.jci.hrms.repository.EmployeeNomineeRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmployeeFamilyNomineeCompositeServiceTest {

    private static final Long EMPLOYEE_ID = 1L;

    @Mock private EmployeeRepository employeeRepository;
    @Mock private EmployeeFamilyDetailsRepository familyDetailsRepository;
    @Mock private EmployeeDependentRepository dependentRepository;
    @Mock private EmployeeNomineeRepository nomineeRepository;

    private EmployeeFamilyNomineeCompositeService service;
    private Employee employee;

    @BeforeEach
    void setUp() {
        service = new EmployeeFamilyNomineeCompositeService(employeeRepository, familyDetailsRepository, dependentRepository, nomineeRepository);

        Department department = new Department("ENG", "Engineering");
        Designation designation = new Designation("Manager");
        employee = new Employee("EMP-001", "Asha", "Rao", "asha.rao@example.com",
                LocalDate.of(2010, 1, 15), department, designation);
        ReflectionTestUtils.setField(employee, "id", EMPLOYEE_ID);

        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(familyDetailsRepository.findByEmployeeId(EMPLOYEE_ID)).thenReturn(Optional.empty());
        when(dependentRepository.findByEmployeeId(EMPLOYEE_ID)).thenReturn(List.of());
        when(nomineeRepository.findByEmployeeId(EMPLOYEE_ID)).thenReturn(List.of());
        when(dependentRepository.saveAndFlush(any(EmployeeDependent.class))).thenAnswer(inv -> inv.getArgument(0));
        when(nomineeRepository.saveAndFlush(any(EmployeeNominee.class))).thenAnswer(inv -> inv.getArgument(0));
        when(familyDetailsRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private CompositeDependentEntry sonEntry(String clientKey, LocalDate dob) {
        return new CompositeDependentEntry(clientKey, null, "Junior Rao", FamilyRelationshipType.SON, dob,
                null, true, false, false, null, false);
    }

    /** upsertFamily() now requires at least one FATHER row in every request (see its own javadoc) - included by default via requestWith() below so only the tests actually exercising that requirement need to omit it. */
    private CompositeDependentEntry fatherEntry() {
        return new CompositeDependentEntry("father-1", null, "Father Rao", FamilyRelationshipType.FATHER,
                LocalDate.of(1960, 1, 1), null, false, false, false, null, false);
    }

    private EmployeeFamilyNomineeCompositeRequest requestWith(List<CompositeDependentEntry> dependents,
                                                                List<CompositeNomineeEntry> pfNominees,
                                                                List<CompositeNomineeEntry> gratuityNominees) {
        List<CompositeDependentEntry> withFather = new java.util.ArrayList<>(dependents);
        withFather.add(0, fatherEntry());
        return new EmployeeFamilyNomineeCompositeRequest(withFather, pfNominees, gratuityNominees);
    }

    @Test
    void save_pfSharesNotSummingTo100_throwsAndSavesNothing() {
        EmployeeFamilyNomineeCompositeRequest request = requestWith(
                List.of(sonEntry("new-1", LocalDate.now().minusYears(10))),
                List.of(new CompositeNomineeEntry(null, "new-1", new BigDecimal("60.00"))),
                List.of());

        assertThatThrownBy(() -> service.save(EMPLOYEE_ID, request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("PF")
                .hasMessageContaining("100%");

        verify(dependentRepository, never()).saveAndFlush(any());
        verify(nomineeRepository, never()).saveAndFlush(any());
    }

    @Test
    void save_twoFatherEntries_throwsAndSavesNothing() {
        EmployeeFamilyNomineeCompositeRequest request = new EmployeeFamilyNomineeCompositeRequest(
                List.of(
                        new CompositeDependentEntry("father-1", null, "Father One", FamilyRelationshipType.FATHER,
                                LocalDate.of(1960, 1, 1), null, false, false, false, null, false),
                        new CompositeDependentEntry("father-2", null, "Father Two", FamilyRelationshipType.FATHER,
                                LocalDate.of(1961, 1, 1), null, false, false, false, null, false)),
                List.of(), List.of());

        assertThatThrownBy(() -> service.save(EMPLOYEE_ID, request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("FATHER");

        verify(dependentRepository, never()).saveAndFlush(any());
        verify(familyDetailsRepository, never()).saveAndFlush(any());
    }

    @Test
    void save_twoSpouseEntries_throwsAndSavesNothing() {
        EmployeeFamilyNomineeCompositeRequest request = requestWith(
                List.of(
                        new CompositeDependentEntry("spouse-1", null, "Spouse One", FamilyRelationshipType.SPOUSE,
                                LocalDate.of(1970, 1, 1), null, false, false, false, null, false),
                        new CompositeDependentEntry("spouse-2", null, "Spouse Two", FamilyRelationshipType.SPOUSE,
                                LocalDate.of(1971, 1, 1), null, false, false, false, null, false)),
                List.of(), List.of());

        assertThatThrownBy(() -> service.save(EMPLOYEE_ID, request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("SPOUSE");

        verify(dependentRepository, never()).saveAndFlush(any());
    }

    @Test
    void save_gratuitySharesNotSummingTo100_throwsAndSavesNothing() {
        EmployeeFamilyNomineeCompositeRequest request = requestWith(
                List.of(sonEntry("new-1", LocalDate.now().minusYears(10))),
                List.of(),
                List.of(new CompositeNomineeEntry(null, "new-1", new BigDecimal("50.00"))));

        assertThatThrownBy(() -> service.save(EMPLOYEE_ID, request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Gratuity")
                .hasMessageContaining("100%");
    }

    @Test
    void save_newDependentNominatedInSameRequest_linksByClientKeyWithoutRetyping() {
        EmployeeFamilyNomineeCompositeRequest request = requestWith(
                List.of(sonEntry("new-1", LocalDate.now().minusYears(10))),
                List.of(new CompositeNomineeEntry(null, "new-1", new BigDecimal("100.00"))),
                List.of());

        service.save(EMPLOYEE_ID, request);

        ArgumentCaptor<EmployeeNominee> captor = ArgumentCaptor.forClass(EmployeeNominee.class);
        verify(nomineeRepository).saveAndFlush(captor.capture());
        EmployeeNominee saved = captor.getValue();
        // Name/relationship came from the linked dependent, never re-typed on the nominee entry itself.
        assertThat(saved.getName()).isEqualTo("Junior Rao");
        assertThat(saved.getRelationship()).isEqualTo(FamilyRelationshipType.SON);
        assertThat(saved.getNomineeFor()).isEqualTo(NominationType.PF);
        assertThat(saved.getDependent()).isNotNull();
    }

    @Test
    void save_nomineeReferencingUnknownClientKey_throwsBusinessRuleViolationException() {
        EmployeeFamilyNomineeCompositeRequest request = requestWith(
                List.of(sonEntry("new-1", LocalDate.now().minusYears(10))),
                List.of(new CompositeNomineeEntry(null, "does-not-exist", new BigDecimal("100.00"))),
                List.of());

        assertThatThrownBy(() -> service.save(EMPLOYEE_ID, request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("does-not-exist");
    }

    @Test
    void save_existingDependentOmittedFromGrid_isSoftDeleted() {
        EmployeeDependent existing = new EmployeeDependent(employee, "Old Dependent", FamilyRelationshipType.DAUGHTER, true, false);
        ReflectionTestUtils.setField(existing, "id", 55L);
        when(dependentRepository.findByEmployeeId(EMPLOYEE_ID)).thenReturn(List.of(existing));

        EmployeeFamilyNomineeCompositeRequest request = requestWith(List.of(), List.of(), List.of());

        service.save(EMPLOYEE_ID, request);

        assertThat(existing.getDeletedAt()).isNotNull();
    }

    @Test
    void save_existingPfNomineeOmittedFromGrid_isSoftDeletedButGratuityUntouched() {
        EmployeeDependent dependent = new EmployeeDependent(employee, "Junior Rao", FamilyRelationshipType.SON, true, false);
        ReflectionTestUtils.setField(dependent, "id", 55L);
        when(dependentRepository.findByEmployeeId(EMPLOYEE_ID)).thenReturn(List.of(dependent));

        EmployeeNominee stalePf = new EmployeeNominee(employee, "Junior Rao", FamilyRelationshipType.SON, new BigDecimal("100.00"), NominationType.PF);
        ReflectionTestUtils.setField(stalePf, "id", 101L);
        EmployeeNominee gratuity = new EmployeeNominee(employee, "Junior Rao", FamilyRelationshipType.SON, new BigDecimal("100.00"), NominationType.GRATUITY);
        ReflectionTestUtils.setField(gratuity, "id", 102L);
        when(nomineeRepository.findByEmployeeId(EMPLOYEE_ID)).thenReturn(List.of(stalePf, gratuity));

        EmployeeFamilyNomineeCompositeRequest request = requestWith(
                List.of(new CompositeDependentEntry("55", 55L, "Junior Rao", FamilyRelationshipType.SON,
                        LocalDate.now().minusYears(10), null, true, false, false, null, false)),
                List.of(),
                List.of(new CompositeNomineeEntry(102L, "55", new BigDecimal("100.00"))));

        service.save(EMPLOYEE_ID, request);

        assertThat(stalePf.getDeletedAt()).isNotNull();
        assertThat(gratuity.getDeletedAt()).isNull();
    }

    @Test
    void get_dependentWithinCeaAgeLimit_returnsEligibleStandardBadge() {
        EmployeeDependent son = new EmployeeDependent(employee, "Junior Rao", FamilyRelationshipType.SON, true, false);
        son.setDateOfBirth(LocalDate.now().minusYears(10));
        when(dependentRepository.findByEmployeeId(EMPLOYEE_ID)).thenReturn(List.of(son));

        EmployeeFamilyNomineeCompositeResponse response = service.get(EMPLOYEE_ID);

        assertThat(response.dependents()).hasSize(1);
        assertThat(response.dependents().get(0).ceaEligibility()).isEqualTo(CeaEligibilityStatus.ELIGIBLE_STANDARD);
    }

    @Test
    void get_overageNonDivyangDependent_returnsIneligibleOverageBadge() {
        EmployeeDependent son = new EmployeeDependent(employee, "Junior Rao", FamilyRelationshipType.SON, true, false);
        son.setDateOfBirth(LocalDate.now().minusYears(25));
        when(dependentRepository.findByEmployeeId(EMPLOYEE_ID)).thenReturn(List.of(son));

        EmployeeFamilyNomineeCompositeResponse response = service.get(EMPLOYEE_ID);

        assertThat(response.dependents().get(0).ceaEligibility()).isEqualTo(CeaEligibilityStatus.INELIGIBLE_OVERAGE);
    }

    @Test
    void get_divyangDependentAged22_returnsEligibleDivyangBadgeEvenThoughOverStandardLimit() {
        EmployeeDependent son = new EmployeeDependent(employee, "Junior Rao", FamilyRelationshipType.SON, true, false);
        son.setDateOfBirth(LocalDate.now().minusYears(22));
        son.setDivyang(true);
        when(dependentRepository.findByEmployeeId(EMPLOYEE_ID)).thenReturn(List.of(son));

        EmployeeFamilyNomineeCompositeResponse response = service.get(EMPLOYEE_ID);

        assertThat(response.dependents().get(0).ceaEligibility()).isEqualTo(CeaEligibilityStatus.ELIGIBLE_DIVYANG);
    }

    @Test
    void get_spouseRow_hasNoCeaBadgeAtAll() {
        EmployeeDependent spouse = new EmployeeDependent(employee, "Jane Rao", FamilyRelationshipType.SPOUSE, false, true);
        spouse.setDateOfBirth(LocalDate.now().minusYears(30));
        when(dependentRepository.findByEmployeeId(EMPLOYEE_ID)).thenReturn(List.of(spouse));

        EmployeeFamilyNomineeCompositeResponse response = service.get(EMPLOYEE_ID);

        assertThat(response.dependents().get(0).ceaEligibility()).isNull();
    }
}
