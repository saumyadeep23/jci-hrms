package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.CpfPassbookSummaryResponse;
import in.gov.jci.hrms.dto.CpfPassbookTransactionDetailResponse;
import in.gov.jci.hrms.dto.CpfPassbookTransactionSummaryResponse;
import in.gov.jci.hrms.entity.CpfLedgerEntryType;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.security.SecurityUtils;
import in.gov.jci.hrms.service.CpfTrustPassbookService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * CPF Passbook V2 self-service (Parts 4/5/14/15) - GET-only, EMPLOYEE_ID ALWAYS DERIVED FROM THE
 * AUTHENTICATED JWT (never accepted as a request parameter), exactly like EssPayrollController's own
 * established pattern for "My Salary Slips". An employee can never view another employee's passbook by
 * changing a URL/query parameter - there is no parameter here that could be changed to do so (Part 21 IDOR
 * protection).
 *
 * Deliberately reuses CpfTrustPassbookService (extended with the new self-service methods, not a second
 * passbook service) and CpfContributionBreakdownService for EPS - see both classes' own javadoc.
 */
@RestController
@RequestMapping("/api/v1/ess/cpf/passbook")
@PreAuthorize("isAuthenticated()")
public class CpfSelfServicePassbookController {

    private final CpfTrustPassbookService passbookService;

    public CpfSelfServicePassbookController(CpfTrustPassbookService passbookService) {
        this.passbookService = passbookService;
    }

    @GetMapping("/summary")
    public CpfPassbookSummaryResponse summary(@RequestParam String finYear, Authentication authentication) {
        return passbookService.getSummary(requireCurrentEmployeeId(authentication), finYear);
    }

    @GetMapping("/transactions")
    public Page<CpfPassbookTransactionSummaryResponse> transactions(
            @RequestParam String finYear,
            @RequestParam(required = false) CpfLedgerEntryType type,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate toDate,
            Pageable pageable, Authentication authentication) {
        return passbookService.getTransactions(requireCurrentEmployeeId(authentication), finYear, type, fromDate, toDate, pageable);
    }

    @GetMapping("/transactions/{transactionId}")
    public CpfPassbookTransactionDetailResponse transactionDetail(@PathVariable Long transactionId, Authentication authentication) {
        return passbookService.getTransactionDetail(requireCurrentEmployeeId(authentication), transactionId);
    }

    private Long requireCurrentEmployeeId(Authentication authentication) {
        Long employeeId = SecurityUtils.currentEmployeeId(authentication);
        if (employeeId == null) {
            throw new BusinessRuleViolationException("Authenticated token has no employee_id claim");
        }
        return employeeId;
    }
}
