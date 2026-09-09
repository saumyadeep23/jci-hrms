package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.CpfStatutoryInterestRateRequest;
import in.gov.jci.hrms.dto.CpfStatutoryInterestRateResponse;
import in.gov.jci.hrms.entity.CpfStatutoryInterestRate;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.repository.CpfStatutoryInterestRateRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The notification-entry engine behind the CPF Rate of Interest Entry GUI - creates the master rows
 * CpfRateResolutionService's Para 60(2) resolution (and so both the dynamic passbook read and interim
 * settlement crystallization) reads from. Every create() is picked up by AuditableEntityListener
 * (CpfStatutoryInterestRate implements Auditable) and lands in the generic /api/audit-logs trail
 * automatically - list() below is this module's own business-level notification history/log.
 */
@Service
@Transactional(readOnly = true)
public class CpfStatutoryInterestRateService {

    private static final String ENTITY_NAME = "CPF Statutory Interest Rate";

    private final CpfStatutoryInterestRateRepository rateRepository;

    public CpfStatutoryInterestRateService(CpfStatutoryInterestRateRepository rateRepository) {
        this.rateRepository = rateRepository;
    }

    @Transactional
    public CpfStatutoryInterestRateResponse create(CpfStatutoryInterestRateRequest request) {
        if (rateRepository.existsByFinYear(request.finYear())) {
            throw new MasterDataConflictException(ENTITY_NAME + " already has a notified entry for FY " + request.finYear());
        }

        CpfStatutoryInterestRate rate = new CpfStatutoryInterestRate(request.finYear(), request.baseCpfRate(),
                request.loanMarkupRate(), request.ministryOrderNo(), request.orderDate());

        try {
            return CpfStatutoryInterestRateResponse.from(rateRepository.saveAndFlush(rate));
        } catch (DataIntegrityViolationException ex) {
            throw new MasterDataConflictException(ENTITY_NAME + " already has a notified entry for FY " + request.finYear());
        }
    }

    public List<CpfStatutoryInterestRateResponse> list() {
        return rateRepository.findAllByOrderByFinYearDesc().stream().map(CpfStatutoryInterestRateResponse::from).toList();
    }
}
