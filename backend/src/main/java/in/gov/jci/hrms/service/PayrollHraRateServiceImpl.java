package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.PayrollHraRateRequest;
import in.gov.jci.hrms.dto.PayrollHraRateResponse;
import in.gov.jci.hrms.entity.PayrollHraRate;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.PayrollHraRateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class PayrollHraRateServiceImpl implements PayrollHraRateService {

    private final PayrollHraRateRepository payrollHraRateRepository;

    public PayrollHraRateServiceImpl(PayrollHraRateRepository payrollHraRateRepository) {
        this.payrollHraRateRepository = payrollHraRateRepository;
    }

    @Override
    public List<PayrollHraRateResponse> listAll() {
        return payrollHraRateRepository.findAllByOrderByCityClassAscEffectiveFromDesc().stream()
                .map(PayrollHraRateResponse::from)
                .toList();
    }

    @Override
    public List<PayrollHraRateResponse> listActiveOn(LocalDate date) {
        return payrollHraRateRepository.findActiveOn(date).stream()
                .map(PayrollHraRateResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public PayrollHraRateResponse create(PayrollHraRateRequest request) {
        PayrollHraRate rate = new PayrollHraRate(request.cityClass(), request.ratePercentage(), request.minAmount(),
                request.effectiveFrom(), request.effectiveTo(), request.remarks());
        return PayrollHraRateResponse.from(payrollHraRateRepository.save(rate));
    }

    @Override
    @Transactional
    public PayrollHraRateResponse update(Long id, PayrollHraRateRequest request) {
        PayrollHraRate rate = payrollHraRateRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException("HRA Rate", id));
        rate.setCityClass(request.cityClass());
        rate.setRatePercentage(request.ratePercentage());
        rate.setMinAmount(request.minAmount());
        rate.setEffectiveFrom(request.effectiveFrom());
        rate.setEffectiveTo(request.effectiveTo());
        rate.setRemarks(request.remarks());
        return PayrollHraRateResponse.from(rate);
    }
}
