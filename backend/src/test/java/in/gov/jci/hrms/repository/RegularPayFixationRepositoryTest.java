package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.Cadre;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.FixationReason;
import in.gov.jci.hrms.entity.GradeScaleMaster;
import in.gov.jci.hrms.entity.IncrementCycle;
import in.gov.jci.hrms.entity.RegularPayFixation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards two instances of the same drift on regular_pay_fixations: its increment_cycle and
 * fixation_reason CHECK constraints were both widened directly against the live dev DB with no
 * matching Flyway migration and no matching update to the Java enum. increment_cycle's version
 * actually crashed the EL encashment admin review queue (and would have crashed payroll compute,
 * LPC certificate generation, movement orders, increment processing, and terminal settlement too) -
 * "No enum constant IncrementCycle.<MONTH>" the moment Hibernate hydrated a non-JULY/JANUARY row.
 * fixation_reason's version was still dormant (every real row happened to use ANNUAL_INCREMENT,
 * valid on both sides) except in EmployeeOnboardingService, which set the stale INITIAL_APPOINTMENT
 * value - one onboarding away from failing its INSERT outright. V61/V62 plus the matching enum
 * widenings fixed both; these tests round-trip every enum value through a real save+flush+refetch so
 * the DB CHECK constraints and the Java enums can never silently drift apart again.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RegularPayFixationRepositoryTest {

    @Autowired
    private RegularPayFixationRepository regularPayFixationRepository;

    @Autowired
    private TestEntityManager entityManager;

    @ParameterizedTest
    @EnumSource(IncrementCycle.class)
    void everyIncrementCycleValueRoundTripsThroughTheDatabase(IncrementCycle cycle) {
        Department department = entityManager.persistAndFlush(new Department("ENG-" + cycle, "Engineering"));
        Designation designation = entityManager.persistAndFlush(new Designation("Engineer"));
        Employee employee = new Employee("EMP-" + cycle, "Test", "Employee", "test." + cycle + "@example.com",
                LocalDate.of(2015, 1, 1), department, designation);
        entityManager.persistAndFlush(employee);

        // hierarchy_level is unique and the real grade_scale_master seed data fully occupies 1-15
        // (this test runs against the real dev DB, not an isolated one) - 9000+ stays clear of it.
        // scale_code is VARCHAR(10), so a month's full name doesn't fit - use its ordinal instead.
        GradeScaleMaster gradeScale = new GradeScaleMaster("T" + cycle.ordinal(), Cadre.EXECUTIVE, 9000 + cycle.ordinal(), false,
                new BigDecimal("40000"), new BigDecimal("80000"));
        entityManager.persistAndFlush(gradeScale);

        RegularPayFixation fixation = new RegularPayFixation(employee, gradeScale, new BigDecimal("50000.00"), LocalDate.of(2026, 1, 1));
        fixation.setIncrementCycle(cycle);
        Long id = regularPayFixationRepository.saveAndFlush(fixation).getId();
        entityManager.clear();

        RegularPayFixation reloaded = regularPayFixationRepository.findById(id).orElseThrow();
        assertThat(reloaded.getIncrementCycle()).isEqualTo(cycle);
    }

    @ParameterizedTest
    @EnumSource(FixationReason.class)
    void everyFixationReasonValueRoundTripsThroughTheDatabase(FixationReason reason) {
        // Department.code is VARCHAR(20) and some FixationReason names (e.g. FINANCIAL_UPGRADATION)
        // don't fit alongside a prefix - use the ordinal for anything that needs to stay short.
        Department department = entityManager.persistAndFlush(new Department("FR-" + reason.ordinal(), "Engineering"));
        Designation designation = entityManager.persistAndFlush(new Designation("Engineer"));
        Employee employee = new Employee("EMPFR-" + reason.ordinal(), "Test", "Employee", "test.fr." + reason.ordinal() + "@example.com",
                LocalDate.of(2015, 1, 1), department, designation);
        entityManager.persistAndFlush(employee);

        // hierarchy_level/scale_code constraints - see the IncrementCycle test above for why 9200+/"R"+ordinal.
        GradeScaleMaster gradeScale = new GradeScaleMaster("R" + reason.ordinal(), Cadre.EXECUTIVE, 9200 + reason.ordinal(), false,
                new BigDecimal("40000"), new BigDecimal("80000"));
        entityManager.persistAndFlush(gradeScale);

        RegularPayFixation fixation = new RegularPayFixation(employee, gradeScale, new BigDecimal("50000.00"), LocalDate.of(2026, 1, 1));
        fixation.setFixationReason(reason);
        Long id = regularPayFixationRepository.saveAndFlush(fixation).getId();
        entityManager.clear();

        RegularPayFixation reloaded = regularPayFixationRepository.findById(id).orElseThrow();
        assertThat(reloaded.getFixationReason()).isEqualTo(reason);
    }

    @Test
    void defaultFixationReason_isAcceptedByTheDatabase() {
        Department department = entityManager.persistAndFlush(new Department("FR-DEFAULT", "Engineering"));
        Designation designation = entityManager.persistAndFlush(new Designation("Engineer"));
        Employee employee = new Employee("EMPFR-DEFAULT", "Test", "Employee", "test.fr.default@example.com",
                LocalDate.of(2015, 1, 1), department, designation);
        entityManager.persistAndFlush(employee);

        GradeScaleMaster gradeScale = new GradeScaleMaster("RDEFAULT", Cadre.EXECUTIVE, 9300, false,
                new BigDecimal("40000"), new BigDecimal("80000"));
        entityManager.persistAndFlush(gradeScale);

        // Deliberately never calls setFixationReason() - this is exactly the path
        // EmployeeOnboardingService used to hit with the stale INITIAL_APPOINTMENT default.
        RegularPayFixation fixation = new RegularPayFixation(employee, gradeScale, new BigDecimal("50000.00"), LocalDate.of(2026, 1, 1));

        assertThat(regularPayFixationRepository.saveAndFlush(fixation).getId()).isNotNull();
    }

    @Test
    void findByEmployeeIdInAndCurrentTrue_hydratesRowsRegardlessOfIncrementCycle() {
        Department department = entityManager.persistAndFlush(new Department("ENG-BATCH", "Engineering"));
        Designation designation = entityManager.persistAndFlush(new Designation("Engineer"));
        Employee employee = new Employee("EMP-BATCH", "Test", "Employee", "test.batch@example.com",
                LocalDate.of(2015, 1, 1), department, designation);
        entityManager.persistAndFlush(employee);

        GradeScaleMaster gradeScale = new GradeScaleMaster("TBATCH", Cadre.EXECUTIVE, 9100, false,
                new BigDecimal("40000"), new BigDecimal("80000"));
        entityManager.persistAndFlush(gradeScale);

        RegularPayFixation fixation = new RegularPayFixation(employee, gradeScale, new BigDecimal("50000.00"), LocalDate.of(2026, 1, 1));
        fixation.setIncrementCycle(IncrementCycle.MARCH); // the exact value that used to crash this query
        fixation.setFixationReason(FixationReason.ANNUAL_INCREMENT);
        Long employeeId = employee.getId();
        entityManager.persistAndFlush(fixation);
        entityManager.clear();

        var results = regularPayFixationRepository.findByEmployeeIdInAndCurrentTrue(java.util.List.of(employeeId));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getBasicPay()).isEqualByComparingTo("50000.00");
    }
}
