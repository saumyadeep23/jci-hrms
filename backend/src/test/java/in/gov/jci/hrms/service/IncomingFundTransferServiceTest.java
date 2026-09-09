package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.IncomingFundTransferRequest;
import in.gov.jci.hrms.dto.IncomingFundTransferResponse;
import in.gov.jci.hrms.entity.CpfLedgerEntryType;
import in.gov.jci.hrms.entity.CpfTrustMemberLedgerEntry;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeServiceBook;
import in.gov.jci.hrms.entity.IncomingTransferPaymentMode;
import in.gov.jci.hrms.entity.IncomingTransferStatus;
import in.gov.jci.hrms.entity.IncomingTransferType;
import in.gov.jci.hrms.entity.PastServiceOrganizationType;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.repository.CpfTrustMemberLedgerEntryRepository;
import in.gov.jci.hrms.repository.DepartmentRepository;
import in.gov.jci.hrms.repository.DesignationRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.EmployeeServiceBookRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real-DB integration test (matching EmployeeFamilyNomineeUpdateTest's own pattern) - proves the two-step
 * SUBMITTED -> CREDITED_TO_LEDGER workflow actually posts a TRANSFER_IN cpf_trust_member_ledger_entries
 * row and a PRIOR_SERVICE_CREDIT employee_service_book row, and increments the employee's own
 * prior_qualifying_service_days, exactly as IncomingFundTransferService.verifyAndCreditTrustLedger()'s
 * own javadoc describes.
 */
@SpringBootTest
@Transactional
class IncomingFundTransferServiceTest {

    @Autowired private IncomingFundTransferService incomingFundTransferService;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private DesignationRepository designationRepository;
    @Autowired private CpfTrustMemberLedgerEntryRepository ledgerRepository;
    @Autowired private EmployeeServiceBookRepository serviceBookRepository;

    private Employee employee;

    @BeforeEach
    void setUp() {
        Department department = departmentRepository.save(new Department("TRFTEST", "Transfer Test Dept"));
        Designation designation = designationRepository.save(new Designation("Transfer Test Officer"));
        employee = employeeRepository.save(new Employee("EMP-TRFTEST-1", "Nabin", "Ghosh",
                "nabin.ghosh.trftest@example.com", LocalDate.of(1988, 3, 10), department, designation));
    }

    private IncomingFundTransferRequest requestWith(BigDecimal eePrincipal, BigDecimal eeInterest, BigDecimal erPrincipal,
                                                      BigDecimal erInterest, BigDecimal vpfPrincipal, BigDecimal vpfInterest,
                                                      BigDecimal total, LocalDate bankRealizationDate, Integer qualifyingDays) {
        return new IncomingFundTransferRequest(
                employee.getId(), null, "Damodar Valley Corporation", PastServiceOrganizationType.CENTRAL_PSU,
                IncomingTransferType.PF_AND_PENSION, LocalDate.of(2025, 3, 31), LocalDate.of(2025, 4, 1),
                IncomingTransferPaymentMode.NEFT, "UTR12345", LocalDate.of(2025, 4, 5), bankRealizationDate, "SBI-CPF-001",
                eePrincipal, eeInterest, erPrincipal, erInterest, vpfPrincipal, vpfInterest, total,
                "EPS-95", new BigDecimal("50000.00"), "PPO123456", 8, qualifyingDays,
                new BigDecimal("25000.00"), true, "ANNEX-K-001", "SANC/2025/001", LocalDate.of(2025, 4, 10), null);
    }

    @Test
    void recordIncomingTransfer_matchingTotal_savesAsSubmitted() {
        IncomingFundTransferRequest request = requestWith(
                new BigDecimal("100000.00"), new BigDecimal("5000.00"), new BigDecimal("100000.00"), new BigDecimal("5000.00"),
                new BigDecimal("20000.00"), new BigDecimal("1000.00"), new BigDecimal("231000.00"),
                LocalDate.now().minusDays(5), 2190);

        IncomingFundTransferResponse response = incomingFundTransferService.recordIncomingTransfer(request, null);

        assertThat(response.status()).isEqualTo(IncomingTransferStatus.SUBMITTED);
        assertThat(response.transferReferenceNo()).startsWith("TRF-IN/");
        assertThat(response.employeeId()).isEqualTo(employee.getId());
    }

    @Test
    void recordIncomingTransfer_mismatchedTotal_throws() {
        IncomingFundTransferRequest request = requestWith(
                new BigDecimal("100000.00"), new BigDecimal("5000.00"), new BigDecimal("100000.00"), new BigDecimal("5000.00"),
                new BigDecimal("20000.00"), new BigDecimal("1000.00"),
                new BigDecimal("999999.00"), // deliberately wrong total
                LocalDate.now().minusDays(5), 2190);

        assertThatThrownBy(() -> incomingFundTransferService.recordIncomingTransfer(request, null))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void verifyAndCreditTrustLedger_submittedTransfer_postsLedgerEntryAndServiceBookEventAndQualifyingDays() {
        IncomingFundTransferRequest request = requestWith(
                new BigDecimal("100000.00"), new BigDecimal("5000.00"), new BigDecimal("100000.00"), new BigDecimal("5000.00"),
                new BigDecimal("20000.00"), new BigDecimal("1000.00"), new BigDecimal("231000.00"),
                LocalDate.now().minusDays(5), 2190);
        IncomingFundTransferResponse submitted = incomingFundTransferService.recordIncomingTransfer(request, null);

        IncomingFundTransferResponse credited = incomingFundTransferService.verifyAndCreditTrustLedger(submitted.id(), 999L, "Verified against bank statement");

        assertThat(credited.status()).isEqualTo(IncomingTransferStatus.CREDITED_TO_LEDGER);
        assertThat(credited.creditedAt()).isNotNull();

        List<CpfTrustMemberLedgerEntry> ledgerEntries = ledgerRepository.findByEmployee_IdOrderByValueDateAscIdAsc(employee.getId());
        assertThat(ledgerEntries).hasSize(1);
        CpfTrustMemberLedgerEntry entry = ledgerEntries.get(0);
        assertThat(entry.getEntryType()).isEqualTo(CpfLedgerEntryType.TRANSFER_IN);
        assertThat(entry.getEeShareCredit()).isEqualByComparingTo("105000.00");
        assertThat(entry.getErShareCredit()).isEqualByComparingTo("105000.00");
        assertThat(entry.getVpfCredit()).isEqualByComparingTo("21000.00");
        assertThat(entry.getRunningEeBalance()).isEqualByComparingTo("105000.00");
        assertThat(entry.getRunningErBalance()).isEqualByComparingTo("105000.00");
        assertThat(entry.getRunningVpfBalance()).isEqualByComparingTo("21000.00");
        assertThat(entry.getRunningTotalBalance()).isEqualByComparingTo("231000.00");

        List<EmployeeServiceBook> serviceBookEntries = serviceBookRepository.findByEmployeeIdOrderByEventDateAscIdAsc(employee.getId());
        assertThat(serviceBookEntries).hasSize(1);
        assertThat(serviceBookEntries.get(0).getEventType()).isEqualTo("PRIOR_SERVICE_CREDIT");
        assertThat(serviceBookEntries.get(0).getIncomingTransfer()).isNotNull();
        assertThat(serviceBookEntries.get(0).isMigrated()).isFalse();

        Employee reloaded = employeeRepository.findById(employee.getId()).orElseThrow();
        assertThat(reloaded.getPriorQualifyingServiceDays()).isEqualTo(2190);
    }

    @Test
    void verifyAndCreditTrustLedger_alreadyCredited_throws() {
        IncomingFundTransferRequest request = requestWith(
                new BigDecimal("50000.00"), BigDecimal.ZERO, new BigDecimal("50000.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100000.00"), LocalDate.now().minusDays(1), 0);
        IncomingFundTransferResponse submitted = incomingFundTransferService.recordIncomingTransfer(request, null);
        incomingFundTransferService.verifyAndCreditTrustLedger(submitted.id(), 999L, null);

        assertThatThrownBy(() -> incomingFundTransferService.verifyAndCreditTrustLedger(submitted.id(), 999L, null))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void verifyAndCreditTrustLedger_futureBankRealizationDate_throws() {
        IncomingFundTransferRequest request = requestWith(
                new BigDecimal("50000.00"), BigDecimal.ZERO, new BigDecimal("50000.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100000.00"), LocalDate.now().plusDays(3), 0);
        IncomingFundTransferResponse submitted = incomingFundTransferService.recordIncomingTransfer(request, null);

        assertThatThrownBy(() -> incomingFundTransferService.verifyAndCreditTrustLedger(submitted.id(), 999L, null))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void reject_submittedTransfer_setsRejectedWithRemarks() {
        IncomingFundTransferRequest request = requestWith(
                new BigDecimal("50000.00"), BigDecimal.ZERO, new BigDecimal("50000.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100000.00"), LocalDate.now().minusDays(1), 0);
        IncomingFundTransferResponse submitted = incomingFundTransferService.recordIncomingTransfer(request, null);

        IncomingFundTransferResponse rejected = incomingFundTransferService.reject(submitted.id(), "Duplicate submission");

        assertThat(rejected.status()).isEqualTo(IncomingTransferStatus.REJECTED);
        assertThat(rejected.rejectionRemarks()).isEqualTo("Duplicate submission");
    }

    @Test
    void reject_alreadyCreditedTransfer_throws() {
        IncomingFundTransferRequest request = requestWith(
                new BigDecimal("50000.00"), BigDecimal.ZERO, new BigDecimal("50000.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100000.00"), LocalDate.now().minusDays(1), 0);
        IncomingFundTransferResponse submitted = incomingFundTransferService.recordIncomingTransfer(request, null);
        incomingFundTransferService.verifyAndCreditTrustLedger(submitted.id(), 999L, null);

        assertThatThrownBy(() -> incomingFundTransferService.reject(submitted.id(), "Too late"))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void findAll_filtersByStatus() {
        IncomingFundTransferRequest request = requestWith(
                new BigDecimal("50000.00"), BigDecimal.ZERO, new BigDecimal("50000.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100000.00"), LocalDate.now().minusDays(1), 0);
        IncomingFundTransferResponse submitted = incomingFundTransferService.recordIncomingTransfer(request, null);

        var page = incomingFundTransferService.findAll(IncomingTransferStatus.SUBMITTED, org.springframework.data.domain.PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(IncomingFundTransferResponse::id).contains(submitted.id());
    }
}
