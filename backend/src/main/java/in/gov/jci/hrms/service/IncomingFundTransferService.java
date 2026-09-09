package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.CreditLedgerRequest;
import in.gov.jci.hrms.dto.IncomingFundTransferRequest;
import in.gov.jci.hrms.dto.IncomingFundTransferResponse;
import in.gov.jci.hrms.entity.CpfLedgerEntryType;
import in.gov.jci.hrms.entity.CpfTrustMemberLedgerEntry;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeIncomingFundTransfer;
import in.gov.jci.hrms.entity.EmployeePastServiceRecord;
import in.gov.jci.hrms.entity.EmployeeServiceBook;
import in.gov.jci.hrms.entity.IncomingTransferStatus;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.CpfTrustMemberLedgerEntryRepository;
import in.gov.jci.hrms.repository.EmployeeIncomingFundTransferRepository;
import in.gov.jci.hrms.repository.EmployeePastServiceRecordRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.EmployeeServiceBookRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Objects;

/**
 * ALMS-adjacent PIMS/PF task: recognizing a prior employer's/Trust's PF+Pension corpus for a newly-joined
 * employee. recordIncomingTransfer() is a pure paperwork step (SUBMITTED) - nothing touches the Trust
 * ledger or the employee's service book until a Trust officer actually verifies bank realization and
 * calls verifyAndCreditTrustLedger(), matching the real-world "money isn't recognized until it's landed"
 * rule this workflow exists to enforce.
 */
@Service
@Transactional(readOnly = true)
public class IncomingFundTransferService {

    private final EmployeeIncomingFundTransferRepository transferRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeePastServiceRecordRepository pastServiceRecordRepository;
    private final CpfTrustMemberLedgerEntryRepository ledgerRepository;
    private final EmployeeServiceBookRepository serviceBookRepository;

    public IncomingFundTransferService(EmployeeIncomingFundTransferRepository transferRepository, EmployeeRepository employeeRepository,
                                        EmployeePastServiceRecordRepository pastServiceRecordRepository,
                                        CpfTrustMemberLedgerEntryRepository ledgerRepository,
                                        EmployeeServiceBookRepository serviceBookRepository) {
        this.transferRepository = transferRepository;
        this.employeeRepository = employeeRepository;
        this.pastServiceRecordRepository = pastServiceRecordRepository;
        this.ledgerRepository = ledgerRepository;
        this.serviceBookRepository = serviceBookRepository;
    }

    @Transactional
    public IncomingFundTransferResponse recordIncomingTransfer(IncomingFundTransferRequest request, Long loggedInUserId) {
        Employee employee = employeeRepository.findById(request.employeeId())
                .orElseThrow(() -> new EmployeeNotFoundException(request.employeeId()));

        BigDecimal reconciledTotal = request.eeCpfPrincipal().add(request.eeCpfInterest())
                .add(request.erJcpfPrincipal()).add(request.erJcpfInterest())
                .add(request.vpfPrincipal()).add(request.vpfInterest());
        if (request.totalCpfTransferred().compareTo(reconciledTotal) != 0) {
            throw new BusinessRuleViolationException(
                    "totalCpfTransferred (" + request.totalCpfTransferred() + ") does not equal the sum of its components ("
                            + reconciledTotal + ")");
        }

        EmployeeIncomingFundTransfer transfer = new EmployeeIncomingFundTransfer(
                generateVoucherNumber(request.bankRealizationDate()), employee, request.sourceOrganizationName(),
                request.sourceOrganizationType(), request.transferType(), request.relievingDate(), request.jciJoiningDate(),
                request.paymentMode(), request.instrumentOrUtrNo(), request.instrumentDate(), request.bankRealizationDate(),
                request.bankAccountCode(), request.totalCpfTransferred(), request.pensionScheme());

        if (request.pastServiceRecordId() != null) {
            EmployeePastServiceRecord pastServiceRecord = pastServiceRecordRepository.findById(request.pastServiceRecordId())
                    .orElseThrow(() -> new MasterDataNotFoundException("Employee Past Service Record", request.pastServiceRecordId()));
            transfer.setPastServiceRecord(pastServiceRecord);
        }
        transfer.setEeCpfPrincipal(request.eeCpfPrincipal());
        transfer.setEeCpfInterest(request.eeCpfInterest());
        transfer.setErJcpfPrincipal(request.erJcpfPrincipal());
        transfer.setErJcpfInterest(request.erJcpfInterest());
        transfer.setVpfPrincipal(request.vpfPrincipal());
        transfer.setVpfInterest(request.vpfInterest());
        transfer.setPensionCorpusAmount(Objects.requireNonNullElse(request.pensionCorpusAmount(), BigDecimal.ZERO));
        transfer.setPranOrPpoNo(request.pranOrPpoNo());
        transfer.setPastQualifyingServiceYears(Objects.requireNonNullElse(request.pastQualifyingServiceYears(), 0));
        transfer.setPastQualifyingServiceDays(Objects.requireNonNullElse(request.pastQualifyingServiceDays(), 0));
        transfer.setGratuityTransferredAmount(Objects.requireNonNullElse(request.gratuityTransferredAmount(), BigDecimal.ZERO));
        transfer.setGratuityServiceCounted(request.gratuityServiceCounted());
        transfer.setAnnexureKDocRef(request.annexureKDocRef());
        transfer.setSanctionOrderNo(request.sanctionOrderNo());
        transfer.setSanctionDate(request.sanctionDate());

        Long createdById = request.createdByEmployeeId() != null ? request.createdByEmployeeId() : loggedInUserId;
        if (createdById != null) {
            employeeRepository.findById(createdById).ifPresent(transfer::setCreatedBy);
        }

        return IncomingFundTransferResponse.from(transferRepository.saveAndFlush(transfer));
    }

    @Transactional
    public IncomingFundTransferResponse verifyAndCreditTrustLedger(Long transferId, Long trustOfficerId, String remarks) {
        EmployeeIncomingFundTransfer transfer = findOrThrow(transferId);
        if (transfer.getStatus() != IncomingTransferStatus.SUBMITTED) {
            throw new BusinessRuleViolationException(
                    "Incoming fund transfer " + transferId + " must be SUBMITTED to credit but is " + transfer.getStatus());
        }
        if (transfer.getBankRealizationDate().isAfter(LocalDate.now())) {
            throw new BusinessRuleViolationException(
                    "Incoming fund transfer " + transferId + "'s bank realization date " + transfer.getBankRealizationDate()
                            + " is in the future - funds have not yet actually landed");
        }

        BigDecimal eeShareCredit = transfer.getEeCpfPrincipal().add(transfer.getEeCpfInterest());
        BigDecimal erShareCredit = transfer.getErJcpfPrincipal().add(transfer.getErJcpfInterest());
        BigDecimal vpfCredit = transfer.getVpfPrincipal().add(transfer.getVpfInterest());

        Employee employee = transfer.getEmployee();
        BigDecimal priorEe = BigDecimal.ZERO;
        BigDecimal priorEr = BigDecimal.ZERO;
        BigDecimal priorVpf = BigDecimal.ZERO;
        var priorEntry = ledgerRepository.findFirstByEmployee_IdOrderByValueDateDescIdDesc(employee.getId());
        if (priorEntry.isPresent()) {
            priorEe = priorEntry.get().getRunningEeBalance();
            priorEr = priorEntry.get().getRunningErBalance();
            priorVpf = priorEntry.get().getRunningVpfBalance();
        }

        CpfTrustMemberLedgerEntry entry = new CpfTrustMemberLedgerEntry(
                employee, financialYearFor(transfer.getBankRealizationDate()), transfer.getBankRealizationDate(),
                CpfLedgerEntryType.TRANSFER_IN, priorEe.add(eeShareCredit), priorEr.add(erShareCredit), priorVpf.add(vpfCredit),
                priorEe.add(eeShareCredit).add(priorEr).add(erShareCredit).add(priorVpf).add(vpfCredit));
        entry.setEeShareCredit(eeShareCredit);
        entry.setErShareCredit(erShareCredit);
        entry.setVpfCredit(vpfCredit);
        entry.setTotalCredit(eeShareCredit.add(erShareCredit).add(vpfCredit));
        entry.setTransfer(transfer);
        entry.setReferenceDocNo(transfer.getTransferReferenceNo());
        String baseRemark = "Transfer-in credited from " + transfer.getSourceOrganizationName();
        entry.setRemarks(remarks != null && !remarks.isBlank() ? baseRemark + " - " + remarks : baseRemark);
        ledgerRepository.save(entry);

        EmployeeServiceBook event = new EmployeeServiceBook(employee, transfer.getJciJoiningDate(), "PRIOR_SERVICE_CREDIT");
        event.setOrderNumber(transfer.getSanctionOrderNo());
        event.setOrderDate(transfer.getSanctionDate());
        event.setEventDescription("Prior service transfer-in recognized from " + transfer.getSourceOrganizationName()
                + " (PF & Pension corpus transferred).");
        event.setIncomingTransfer(transfer);
        event.setMigrated(false);
        serviceBookRepository.save(event);

        if (transfer.getPastQualifyingServiceDays() > 0) {
            employee.setPriorQualifyingServiceDays(employee.getPriorQualifyingServiceDays() + transfer.getPastQualifyingServiceDays());
        }

        transfer.setStatus(IncomingTransferStatus.CREDITED_TO_LEDGER);
        transfer.setCreditedAt(Instant.now());
        employeeRepository.findById(trustOfficerId).ifPresent(transfer::setCreditedBy);

        return IncomingFundTransferResponse.from(transfer);
    }

    /** Convenience overload matching the controller's own request shape. */
    @Transactional
    public IncomingFundTransferResponse verifyAndCreditTrustLedger(Long transferId, CreditLedgerRequest request) {
        return verifyAndCreditTrustLedger(transferId, request.trustOfficerId(), request.remarks());
    }

    public List<IncomingFundTransferResponse> findPending() {
        return transferRepository.findByStatus(IncomingTransferStatus.SUBMITTED).stream()
                .map(IncomingFundTransferResponse::from).toList();
    }

    public org.springframework.data.domain.Page<IncomingFundTransferResponse> findAll(IncomingTransferStatus status,
                                                                                        org.springframework.data.domain.Pageable pageable) {
        var page = status != null ? transferRepository.findByStatus(status, pageable) : transferRepository.findAll(pageable);
        return page.map(IncomingFundTransferResponse::from);
    }

    @Transactional
    public IncomingFundTransferResponse reject(Long transferId, String remarks) {
        EmployeeIncomingFundTransfer transfer = findOrThrow(transferId);
        if (transfer.getStatus() != IncomingTransferStatus.SUBMITTED) {
            throw new BusinessRuleViolationException(
                    "Incoming fund transfer " + transferId + " must be SUBMITTED to reject but is " + transfer.getStatus());
        }
        transfer.setStatus(IncomingTransferStatus.REJECTED);
        transfer.setRejectionRemarks(remarks);
        return IncomingFundTransferResponse.from(transfer);
    }

    private EmployeeIncomingFundTransfer findOrThrow(Long id) {
        return transferRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException("Incoming Fund Transfer", id));
    }

    /** "TRF-IN/{finYear}/{seq}" - seq is a simple per-finYear count+1, not advisory-lock-guarded like EmployeeCodeGeneratorService/CpfAcNoGeneratorService: this is a low-volume, human-paced Trust-officer workflow, not a high-concurrency onboarding path, so a rare duplicate on true simultaneous submission is an acceptable tradeoff against the added complexity. */
    private String generateVoucherNumber(LocalDate bankRealizationDate) {
        String finYear = financialYearFor(bankRealizationDate);
        String prefix = "TRF-IN/" + finYear + "/";
        long seq = transferRepository.countByTransferReferenceNoStartingWith(prefix) + 1;
        return prefix + seq;
    }

    /** April-March FY label ("2026-2027") matching the app-wide convention (see V66's employee_nps_declarations.financial_year). */
    static String financialYearFor(LocalDate date) {
        int startYear = date.getMonthValue() >= Month.APRIL.getValue() ? date.getYear() : date.getYear() - 1;
        return startYear + "-" + (startYear + 1);
    }
}
