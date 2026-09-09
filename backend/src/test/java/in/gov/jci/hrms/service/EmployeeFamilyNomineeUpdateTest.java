package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.CompositeDependentEntry;
import in.gov.jci.hrms.dto.CompositeNomineeEntry;
import in.gov.jci.hrms.dto.EmployeeFamilyNomineeCompositeRequest;
import in.gov.jci.hrms.dto.EmployeeFamilyNomineeCompositeResponse;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeDependent;
import in.gov.jci.hrms.entity.EmployeeNominee;
import in.gov.jci.hrms.entity.FamilyRelationshipType;
import in.gov.jci.hrms.entity.NominationType;
import in.gov.jci.hrms.repository.DepartmentRepository;
import in.gov.jci.hrms.repository.DesignationRepository;
import in.gov.jci.hrms.repository.EmployeeDependentRepository;
import in.gov.jci.hrms.repository.EmployeeNomineeRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression / verification test for the reported "saving Family &amp; Nominees wipes all existing
 * family records and fails to save nominees" bug.
 *
 * <p>The task that reported this bug assumed a codebase shape that doesn't exist here - there is no
 * {@code EmployeeUpdateDto}, {@code EmployeeFamilyService}, or {@code EmployeeFamilyMember} entity, and
 * the Family &amp; Nominees tab does not submit through the root employee PUT at all. It saves as its own
 * atomic unit via {@link EmployeeFamilyNomineeCompositeService#save}, backed by
 * {@code employee_dependents}/{@code employee_nominees} and the real {@link EmployeeDependent}/
 * {@link EmployeeNominee} entities - built and tested earlier as part of the Family &amp; Nominees tab
 * redesign (see {@code EmployeeFamilyNomineeCompositeServiceTest}, which covers the same upsert/
 * soft-delete-only-on-removal logic at the unit level with mocks).
 *
 * <p>This is a real-DB integration test (matching {@code SeparatedEmployeeDirectoryServiceIntegrationTest}'s
 * own pattern) proving the exact reported scenario end to end: three pre-existing family members, a DOB
 * update on each, a fourth appended in the same save (linked to two new 100%-share nominations via the
 * clientKey mechanism - the real equivalent of the reported "tempFamilyMemberId" idea), and confirms
 * nothing is wiped and the new nominee rows persist correctly.
 */
@SpringBootTest
@Transactional
class EmployeeFamilyNomineeUpdateTest {

    @Autowired private EmployeeFamilyNomineeCompositeService compositeService;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private DesignationRepository designationRepository;
    @Autowired private EmployeeDependentRepository dependentRepository;
    @Autowired private EmployeeNomineeRepository nomineeRepository;

    private Employee employee;
    private EmployeeDependent sanatanSaha;
    private EmployeeDependent latikaSaha;
    private EmployeeDependent asmitSaha;

    @BeforeEach
    void setUp() {
        Department department = departmentRepository.save(new Department("FAMTEST", "Family Update Test Dept"));
        Designation designation = designationRepository.save(new Designation("Family Test Officer"));
        employee = employeeRepository.save(new Employee("EMP-FAMTEST-1", "Debasish", "Saha",
                "debasish.saha.familytest@example.com", LocalDate.of(1985, 4, 12), department, designation));

        // Three pre-existing family members - already in the DB before this "edit session" begins,
        // exactly like the panel would have loaded them via GET .../family-nominees.
        sanatanSaha = dependentRepository.save(new EmployeeDependent(employee, "Sanatan Saha", FamilyRelationshipType.FATHER,
                false, false));
        sanatanSaha.setDateOfBirth(LocalDate.of(1955, 6, 1));
        latikaSaha = dependentRepository.save(new EmployeeDependent(employee, "Latika Saha", FamilyRelationshipType.MOTHER,
                false, false));
        latikaSaha.setDateOfBirth(LocalDate.of(1958, 3, 15));
        asmitSaha = dependentRepository.save(new EmployeeDependent(employee, "Asmit Saha", FamilyRelationshipType.SON,
                true, true));
        asmitSaha.setDateOfBirth(LocalDate.of(2015, 8, 20));
        dependentRepository.saveAllAndFlush(List.of(sanatanSaha, latikaSaha, asmitSaha));
    }

    @Test
    void save_updatesExistingDobsAndAppendsNewMemberWithNominees_withoutWipingAnything() {
        // Updated DOBs for the three existing members (simulating a correction made in the same edit session).
        LocalDate sanatanNewDob = LocalDate.of(1954, 6, 1);
        LocalDate latikaNewDob = LocalDate.of(1959, 3, 15);
        LocalDate asmitNewDob = LocalDate.of(2015, 8, 21);

        CompositeDependentEntry sanatanEntry = new CompositeDependentEntry(String.valueOf(sanatanSaha.getId()), sanatanSaha.getId(),
                "Sanatan Saha", FamilyRelationshipType.FATHER, sanatanNewDob, null, false, false, false, null, false);
        CompositeDependentEntry latikaEntry = new CompositeDependentEntry(String.valueOf(latikaSaha.getId()), latikaSaha.getId(),
                "Latika Saha", FamilyRelationshipType.MOTHER, latikaNewDob, null, false, false, false, null, false);
        CompositeDependentEntry asmitEntry = new CompositeDependentEntry(String.valueOf(asmitSaha.getId()), asmitSaha.getId(),
                "Asmit Saha", FamilyRelationshipType.SON, asmitNewDob, null, true, true, false, null, false);

        // A brand-new fourth member, not yet persisted - linked via a client-generated temp key exactly
        // like the panel's own `new-${n}` clientKey convention.
        String anamikaClientKey = "temp-uuid-1";
        CompositeDependentEntry anamikaEntry = new CompositeDependentEntry(anamikaClientKey, null,
                "Anamika Saha", FamilyRelationshipType.SPOUSE, LocalDate.of(1993, 1, 20), null, true, true, false, null, false);

        CompositeNomineeEntry pfNominee = new CompositeNomineeEntry(null, anamikaClientKey, new BigDecimal("100.00"));
        CompositeNomineeEntry gratuityNominee = new CompositeNomineeEntry(null, anamikaClientKey, new BigDecimal("100.00"));

        // fatherName/motherName are no longer submitted directly - EmployeeFamilyNomineeCompositeService
        // now derives employee_family_details' own columns from the FATHER/MOTHER rows below (Sanatan
        // and Latika Saha), which is exactly the redundant-input elimination this composite request was
        // updated for.
        EmployeeFamilyNomineeCompositeRequest request = new EmployeeFamilyNomineeCompositeRequest(
                List.of(sanatanEntry, latikaEntry, asmitEntry, anamikaEntry),
                List.of(pfNominee),
                List.of(gratuityNominee));

        EmployeeFamilyNomineeCompositeResponse response = compositeService.save(employee.getId(), request);

        // --- Nothing was wiped: all 4 dependents present, DOBs updated on the pre-existing 3. ---
        List<EmployeeDependent> persistedDependents = dependentRepository.findByEmployeeId(employee.getId());
        assertThat(persistedDependents).hasSize(4);
        Map<String, EmployeeDependent> byName = persistedDependents.stream()
                .collect(java.util.stream.Collectors.toMap(EmployeeDependent::getName, d -> d));

        assertThat(byName.get("Sanatan Saha").getDateOfBirth()).isEqualTo(sanatanNewDob);
        assertThat(byName.get("Latika Saha").getDateOfBirth()).isEqualTo(latikaNewDob);
        assertThat(byName.get("Asmit Saha").getDateOfBirth()).isEqualTo(asmitNewDob);

        EmployeeDependent anamika = byName.get("Anamika Saha");
        assertThat(anamika).isNotNull();
        assertThat(anamika.getId()).isNotNull();
        assertThat(anamika.getRelationship()).isEqualTo(FamilyRelationshipType.SPOUSE);
        assertThat(anamika.getDateOfBirth()).isEqualTo(LocalDate.of(1993, 1, 20));
        assertThat(anamika.isCoveredMedical()).isTrue();

        // --- employee_family_details' fatherName/motherName/spouseName/spouseDob were derived from the
        // register rows above, not submitted directly (those inputs were removed from the UI). ---
        assertThat(response.family()).isNotNull();
        assertThat(response.family().fatherName()).isEqualTo("Sanatan Saha");
        assertThat(response.family().motherName()).isEqualTo("Latika Saha");
        assertThat(response.family().spouseName()).isEqualTo("Anamika Saha");
        assertThat(response.family().spouseDob()).isEqualTo(LocalDate.of(1993, 1, 20));

        // --- Nominees correctly resolved against the newly generated id, not left dangling on the clientKey. ---
        List<EmployeeNominee> persistedNominees = nomineeRepository.findByEmployeeId(employee.getId());
        assertThat(persistedNominees).hasSize(2);
        assertThat(persistedNominees).allSatisfy(n -> assertThat(n.getDependent().getId()).isEqualTo(anamika.getId()));
        assertThat(persistedNominees).anySatisfy(n -> {
            assertThat(n.getNomineeFor()).isEqualTo(NominationType.PF);
            assertThat(n.getSharePercentage()).isEqualByComparingTo("100.00");
        });
        assertThat(persistedNominees).anySatisfy(n -> {
            assertThat(n.getNomineeFor()).isEqualTo(NominationType.GRATUITY);
            assertThat(n.getSharePercentage()).isEqualByComparingTo("100.00");
        });

        // --- Response reflects the same persisted state (what the panel would re-render after save). ---
        assertThat(response.dependents()).hasSize(4);
        assertThat(response.pfNominees()).hasSize(1);
        assertThat(response.gratuityNominees()).hasSize(1);
    }
}
