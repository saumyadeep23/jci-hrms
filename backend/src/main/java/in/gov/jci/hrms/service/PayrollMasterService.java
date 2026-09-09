package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.ProcurementAllowanceRequest;
import in.gov.jci.hrms.dto.ProcurementAllowanceResponse;
import in.gov.jci.hrms.dto.PtaxSlabRequest;
import in.gov.jci.hrms.dto.PtaxSlabResponse;
import in.gov.jci.hrms.dto.SalaryHeadResponse;
import in.gov.jci.hrms.dto.SalaryHeadUpdateRequest;
import in.gov.jci.hrms.dto.StatutoryHeadResponse;
import in.gov.jci.hrms.dto.StatutoryHeadUpdateRequest;
import in.gov.jci.hrms.dto.TransportAllowanceRequest;
import in.gov.jci.hrms.dto.TransportAllowanceResponse;

import java.util.List;

/** CRUD/listing for the JCI Payroll Engine's five simple master configuration datasets. NPS declarations have their own service - see NpsDeclarationService. */
public interface PayrollMasterService {

    List<TransportAllowanceResponse> listTransportAllowances();

    TransportAllowanceResponse createTransportAllowance(TransportAllowanceRequest request);

    TransportAllowanceResponse updateTransportAllowance(Long id, TransportAllowanceRequest request);

    List<ProcurementAllowanceResponse> listProcurementAllowances();

    ProcurementAllowanceResponse createProcurementAllowance(ProcurementAllowanceRequest request);

    ProcurementAllowanceResponse updateProcurementAllowance(Long id, ProcurementAllowanceRequest request);

    List<PtaxSlabResponse> listPtaxSlabs();

    List<PtaxSlabResponse> listPtaxSlabsByState(String stateCode);

    PtaxSlabResponse createPtaxSlab(PtaxSlabRequest request);

    PtaxSlabResponse updatePtaxSlab(Long id, PtaxSlabRequest request);

    List<SalaryHeadResponse> listSalaryHeads();

    SalaryHeadResponse updateSalaryHead(Integer headCount, SalaryHeadUpdateRequest request);

    List<StatutoryHeadResponse> listStatutoryHeads();

    StatutoryHeadResponse updateStatutoryHead(Integer statHeadCount, StatutoryHeadUpdateRequest request);
}
