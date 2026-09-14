package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.JciEccsMemberResponse;
import in.gov.jci.hrms.dto.JciEccsMemberStatusChangeRequest;
import in.gov.jci.hrms.entity.CityClass;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.JciEccsMember;
import in.gov.jci.hrms.entity.JciEccsMembershipStatus;
import in.gov.jci.hrms.entity.RegionalOffice;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.repository.DepartmentRepository;
import in.gov.jci.hrms.repository.DesignationRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.JciEccsMemberRepository;
import in.gov.jci.hrms.repository.RegionalOfficeRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Member Directory enrichment (employee full name + place of posting joined via employee_id, Bye-law 5
 * eligibility auditing) and the membership status-change governance action (Bye-laws 15/16: remarks
 * required for SUSPENDED/CLOSED, effective_to set on the non-ACTIVE transition).
 */
@SpringBootTest
@Transactional
class JciEccsMemberServiceTest {

    @Autowired private JciEccsMemberService memberService;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private DesignationRepository designationRepository;
    @Autowired private RegionalOfficeRepository regionalOfficeRepository;
    @Autowired private JciEccsMemberRepository memberRepository;
    @PersistenceContext private EntityManager entityManager;

    private Employee employee;
    private JciEccsMember member;

    @BeforeEach
    void setUp() {
        Department department = departmentRepository.save(new Department("JCIECCSMB", "JCIECCS Member Test Dept"));
        Designation designation = designationRepository.save(new Designation("JCIECCS Member Test Officer"));
        // ro_code has a real DB CHECK (ck_ro_master_ro_code_format: exactly 2 digits) - "91" avoids
        // colliding with any real seeded regional office code.
        RegionalOffice headOffice = regionalOfficeRepository.save(new RegionalOffice("91", "Head Office - Kolkata", "West Bengal", CityClass.X, true));

        Employee pendingEmployee = new Employee("EMP-JEM-1", "Member", "Tester", "jem1@example.com",
                LocalDate.now().minusYears(6), department, designation);
        pendingEmployee.setRegionalOffice(headOffice);
        employee = employeeRepository.save(pendingEmployee);
        // full_name is a DB GENERATED ALWAYS column (insertable=false/updatable=false) - never
        // populated on this still-managed entity within the same persistence context after INSERT
        // unless explicitly refreshed from the DB (same class of staleness as the total_due/
        // total_snapshot_amount generated columns hit earlier in this module).
        entityManager.refresh(employee);

        member = memberRepository.save(new JciEccsMember(employee.getId(), "JECCS-MB-0001", LocalDate.now().minusYears(3), LocalDate.now().minusYears(3)));
    }

    @Test
    void listMembers_enrichesWithEmployeeFullNameAndPlaceOfPosting() {
        List<JciEccsMemberResponse> members = memberService.listMembers();

        JciEccsMemberResponse response = members.stream().filter(m -> m.id().equals(member.getId())).findFirst().orElseThrow();
        assertThat(response.memberName()).isEqualTo("Member Tester");
        assertThat(response.employeeCode()).isEqualTo("EMP-JEM-1");
        assertThat(response.placeOfPosting()).isEqualTo("Head Office - Kolkata");
    }

    @Test
    void changeStatus_toSuspended_withoutRemarks_isRejected() {
        assertThatThrownBy(() -> memberService.changeStatus(member.getId(),
                new JciEccsMemberStatusChangeRequest(JciEccsMembershipStatus.SUSPENDED, LocalDate.now(), null), employee.getId()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Remarks are required");
    }

    @Test
    void changeStatus_toSuspended_withRemarks_setsStatusAndEffectiveTo() {
        LocalDate effectiveDate = LocalDate.now();
        JciEccsMemberResponse response = memberService.changeStatus(member.getId(),
                new JciEccsMemberStatusChangeRequest(JciEccsMembershipStatus.SUSPENDED, effectiveDate, "Disciplinary action per Bye-law 15"),
                employee.getId());

        assertThat(response.membershipStatus()).isEqualTo(JciEccsMembershipStatus.SUSPENDED);

        JciEccsMember reloaded = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(reloaded.getMembershipStatus()).isEqualTo(JciEccsMembershipStatus.SUSPENDED);
        assertThat(reloaded.getEffectiveTo()).isEqualTo(effectiveDate);
    }

    @Test
    void changeStatus_backToActive_clearsEffectiveTo() {
        memberService.changeStatus(member.getId(),
                new JciEccsMemberStatusChangeRequest(JciEccsMembershipStatus.SUSPENDED, LocalDate.now(), "Temporary suspension"), employee.getId());

        JciEccsMemberResponse reactivated = memberService.changeStatus(member.getId(),
                new JciEccsMemberStatusChangeRequest(JciEccsMembershipStatus.ACTIVE, LocalDate.now(), "Reinstated"), employee.getId());

        assertThat(reactivated.membershipStatus()).isEqualTo(JciEccsMembershipStatus.ACTIVE);
        JciEccsMember reloaded = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(reloaded.getEffectiveTo()).isNull();
    }

    @Test
    void changeStatus_toClosed_withoutRemarks_isRejected() {
        assertThatThrownBy(() -> memberService.changeStatus(member.getId(),
                new JciEccsMemberStatusChangeRequest(JciEccsMembershipStatus.CLOSED, LocalDate.now(), ""), employee.getId()))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void suspendedMember_isExcludedFromFutureCollectionSnapshots() {
        memberService.changeStatus(member.getId(),
                new JciEccsMemberStatusChangeRequest(JciEccsMembershipStatus.SUSPENDED, LocalDate.now(), "Bye-law 15 suspension"), employee.getId());

        List<JciEccsMember> activeMembers = memberRepository.findByMembershipStatus(JciEccsMembershipStatus.ACTIVE);
        assertThat(activeMembers).noneMatch(m -> m.getId().equals(member.getId()));
    }
}
