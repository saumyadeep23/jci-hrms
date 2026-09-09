package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.StatutoryParameterResponse;
import in.gov.jci.hrms.dto.StatutoryParameterReviseRequest;
import in.gov.jci.hrms.entity.PayrollStatutoryParameter;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.PayrollStatutoryParameterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class PayrollStatutoryParameterServiceImpl implements PayrollStatutoryParameterService {

    private final PayrollStatutoryParameterRepository statutoryParameterRepository;

    public PayrollStatutoryParameterServiceImpl(PayrollStatutoryParameterRepository statutoryParameterRepository) {
        this.statutoryParameterRepository = statutoryParameterRepository;
    }

    @Override
    public List<StatutoryParameterResponse> listCurrent() {
        return statutoryParameterRepository.findByEffectiveToIsNullOrderByParamKeyAsc().stream()
                .map(StatutoryParameterResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public StatutoryParameterResponse revise(String paramKey, StatutoryParameterReviseRequest request) {
        PayrollStatutoryParameter current = statutoryParameterRepository.findByParamKeyAndEffectiveToIsNull(paramKey)
                .orElseThrow(() -> new MasterDataNotFoundException("Statutory Parameter", paramKey));

        if (!request.newEffectiveFrom().isAfter(current.getEffectiveFrom())) {
            throw new BusinessRuleViolationException(
                    "newEffectiveFrom must be after the current version's effectiveFrom (" + current.getEffectiveFrom() + ")");
        }

        current.setEffectiveTo(request.newEffectiveFrom().minusDays(1));
        statutoryParameterRepository.saveAndFlush(current);

        PayrollStatutoryParameter revised = new PayrollStatutoryParameter(current.getParamKey(), current.getParamName(),
                request.newValue(), current.getValType(), request.newEffectiveFrom(),
                request.remarks() != null ? request.remarks() : current.getRemarks());
        return StatutoryParameterResponse.from(statutoryParameterRepository.saveAndFlush(revised));
    }
}
