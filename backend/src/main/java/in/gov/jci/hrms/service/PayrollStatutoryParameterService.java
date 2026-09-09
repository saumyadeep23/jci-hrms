package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.StatutoryParameterResponse;
import in.gov.jci.hrms.dto.StatutoryParameterReviseRequest;

import java.util.List;

/** Statutory Parameters console - versioned CPF/EPS/NPS/GIS/LWF rates and flat amounts. */
public interface PayrollStatutoryParameterService {

    /** One row per key - the current (effectiveTo IS NULL) version only. */
    List<StatutoryParameterResponse> listCurrent();

    /**
     * Closes out the current row's effectiveTo at (newEffectiveFrom - 1 day) and inserts the new
     * version, so there is never a gap or an overlap between versions of the same key.
     */
    StatutoryParameterResponse revise(String paramKey, StatutoryParameterReviseRequest request);
}
