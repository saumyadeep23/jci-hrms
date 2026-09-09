package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.PayrollHraRateRequest;
import in.gov.jci.hrms.dto.PayrollHraRateResponse;

import java.time.LocalDate;
import java.util.List;

/** HRA Rate Master - CRUD for the city-class (X/Y/Z) HRA percentage + minimum-floor slabs, versioned by effective date range. */
public interface PayrollHraRateService {

    List<PayrollHraRateResponse> listAll();

    List<PayrollHraRateResponse> listActiveOn(LocalDate date);

    PayrollHraRateResponse create(PayrollHraRateRequest request);

    PayrollHraRateResponse update(Long id, PayrollHraRateRequest request);
}
