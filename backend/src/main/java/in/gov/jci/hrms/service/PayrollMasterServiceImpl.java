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
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.GradeScaleMaster;
import in.gov.jci.hrms.entity.ProcurementAllowanceRate;
import in.gov.jci.hrms.entity.PtaxSlab;
import in.gov.jci.hrms.entity.SalaryHead;
import in.gov.jci.hrms.entity.StatutoryHead;
import in.gov.jci.hrms.entity.TransportAllowanceRate;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.exception.MasterDataValidationException;
import in.gov.jci.hrms.repository.DesignationRepository;
import in.gov.jci.hrms.repository.GradeScaleMasterRepository;
import in.gov.jci.hrms.repository.ProcurementAllowanceRateRepository;
import in.gov.jci.hrms.repository.PtaxSlabRepository;
import in.gov.jci.hrms.repository.SalaryHeadRepository;
import in.gov.jci.hrms.repository.StateMasterRepository;
import in.gov.jci.hrms.repository.StatutoryHeadRepository;
import in.gov.jci.hrms.repository.TransportAllowanceRateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class PayrollMasterServiceImpl implements PayrollMasterService {

    private final TransportAllowanceRateRepository transportAllowanceRateRepository;
    private final ProcurementAllowanceRateRepository procurementAllowanceRateRepository;
    private final PtaxSlabRepository ptaxSlabRepository;
    private final SalaryHeadRepository salaryHeadRepository;
    private final StatutoryHeadRepository statutoryHeadRepository;
    private final GradeScaleMasterRepository gradeScaleMasterRepository;
    private final DesignationRepository designationRepository;
    private final StateMasterRepository stateMasterRepository;

    public PayrollMasterServiceImpl(TransportAllowanceRateRepository transportAllowanceRateRepository,
                                     ProcurementAllowanceRateRepository procurementAllowanceRateRepository,
                                     PtaxSlabRepository ptaxSlabRepository,
                                     SalaryHeadRepository salaryHeadRepository,
                                     StatutoryHeadRepository statutoryHeadRepository,
                                     GradeScaleMasterRepository gradeScaleMasterRepository,
                                     DesignationRepository designationRepository,
                                     StateMasterRepository stateMasterRepository) {
        this.transportAllowanceRateRepository = transportAllowanceRateRepository;
        this.procurementAllowanceRateRepository = procurementAllowanceRateRepository;
        this.ptaxSlabRepository = ptaxSlabRepository;
        this.salaryHeadRepository = salaryHeadRepository;
        this.statutoryHeadRepository = statutoryHeadRepository;
        this.gradeScaleMasterRepository = gradeScaleMasterRepository;
        this.designationRepository = designationRepository;
        this.stateMasterRepository = stateMasterRepository;
    }

    @Override
    public List<TransportAllowanceResponse> listTransportAllowances() {
        return transportAllowanceRateRepository.findAll().stream()
                .map(TransportAllowanceResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public TransportAllowanceResponse createTransportAllowance(TransportAllowanceRequest request) {
        GradeScaleMaster gradeScale = findGradeScaleOrThrow(request.gradeScaleId());
        TransportAllowanceRate rate = new TransportAllowanceRate(gradeScale, request.cityClass(),
                request.baseRate(), request.effectiveFrom());
        return TransportAllowanceResponse.from(transportAllowanceRateRepository.save(rate));
    }

    @Override
    @Transactional
    public TransportAllowanceResponse updateTransportAllowance(Long id, TransportAllowanceRequest request) {
        TransportAllowanceRate rate = transportAllowanceRateRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException("Transport Allowance Rate", id));
        rate.setGradeScale(findGradeScaleOrThrow(request.gradeScaleId()));
        rate.setCityClass(request.cityClass());
        rate.setBaseRate(request.baseRate());
        rate.setEffectiveFrom(request.effectiveFrom());
        return TransportAllowanceResponse.from(rate);
    }

    @Override
    public List<ProcurementAllowanceResponse> listProcurementAllowances() {
        return procurementAllowanceRateRepository.findAll().stream()
                .map(ProcurementAllowanceResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public ProcurementAllowanceResponse createProcurementAllowance(ProcurementAllowanceRequest request) {
        Designation designation = findDesignationOrThrow(request.designationId());
        ProcurementAllowanceRate rate = new ProcurementAllowanceRate(designation, request.monthlyAllowance(), request.effectiveFrom());
        return ProcurementAllowanceResponse.from(procurementAllowanceRateRepository.save(rate));
    }

    @Override
    @Transactional
    public ProcurementAllowanceResponse updateProcurementAllowance(Long id, ProcurementAllowanceRequest request) {
        ProcurementAllowanceRate rate = procurementAllowanceRateRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException("Procurement Allowance Rate", id));
        rate.setDesignation(findDesignationOrThrow(request.designationId()));
        rate.setMonthlyAllowance(request.monthlyAllowance());
        rate.setEffectiveFrom(request.effectiveFrom());
        return ProcurementAllowanceResponse.from(rate);
    }

    @Override
    public List<PtaxSlabResponse> listPtaxSlabs() {
        return ptaxSlabRepository.findAll().stream()
                .map(PtaxSlabResponse::from)
                .toList();
    }

    @Override
    public List<PtaxSlabResponse> listPtaxSlabsByState(String stateCode) {
        return ptaxSlabRepository.findByStateCodeOrderBySlabMinAsc(stateCode).stream()
                .map(PtaxSlabResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public PtaxSlabResponse createPtaxSlab(PtaxSlabRequest request) {
        validateSlabOrder(request.slabMin(), request.slabMax());
        findStateOrThrow(request.stateCode());
        PtaxSlab slab = new PtaxSlab(request.stateCode(), request.slabMin(), request.slabMax(), request.taxAmount(),
                request.specialMonth(), request.specialMonthTax(), request.effectiveFrom());
        return PtaxSlabResponse.from(ptaxSlabRepository.save(slab));
    }

    @Override
    @Transactional
    public PtaxSlabResponse updatePtaxSlab(Long id, PtaxSlabRequest request) {
        validateSlabOrder(request.slabMin(), request.slabMax());
        findStateOrThrow(request.stateCode());
        PtaxSlab slab = ptaxSlabRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException("P-Tax Slab", id));
        slab.setStateCode(request.stateCode());
        slab.setSlabMin(request.slabMin());
        slab.setSlabMax(request.slabMax());
        slab.setTaxAmount(request.taxAmount());
        slab.setSpecialMonth(request.specialMonth());
        slab.setSpecialMonthTax(request.specialMonthTax());
        slab.setEffectiveFrom(request.effectiveFrom());
        return PtaxSlabResponse.from(slab);
    }

    @Override
    public List<SalaryHeadResponse> listSalaryHeads() {
        return salaryHeadRepository.findAllByOrderByHeadCountAsc().stream()
                .map(SalaryHeadResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public SalaryHeadResponse updateSalaryHead(Integer headCount, SalaryHeadUpdateRequest request) {
        SalaryHead head = salaryHeadRepository.findById(headCount)
                .orElseThrow(() -> new MasterDataNotFoundException("Salary Head", headCount));
        head.setDescription(request.description());
        head.setShortName(request.shortName());
        head.setEffectType(request.effectType());
        head.setVariable(request.isVariable());
        head.setApplicableFor(request.applicableFor());
        head.setSalSlipVis(request.salSlipVis());
        head.setBasicDependent(request.basicDependent());
        head.setRefAccountCode(request.refAccountCode());
        return SalaryHeadResponse.from(head);
    }

    @Override
    public List<StatutoryHeadResponse> listStatutoryHeads() {
        return statutoryHeadRepository.findAllByOrderByStatHeadCountAsc().stream()
                .map(StatutoryHeadResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public StatutoryHeadResponse updateStatutoryHead(Integer statHeadCount, StatutoryHeadUpdateRequest request) {
        StatutoryHead head = statutoryHeadRepository.findById(statHeadCount)
                .orElseThrow(() -> new MasterDataNotFoundException("Statutory Head", statHeadCount));
        head.setStatHeadDescr(request.description());
        head.setStatHeadShortName(request.shortName());
        return StatutoryHeadResponse.from(head);
    }

    private void validateSlabOrder(BigDecimal slabMin, BigDecimal slabMax) {
        if (slabMax != null && slabMax.compareTo(slabMin) <= 0) {
            throw new MasterDataValidationException("slabMax must be greater than slabMin");
        }
    }

    private GradeScaleMaster findGradeScaleOrThrow(Long id) {
        return gradeScaleMasterRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException("Grade Scale", id));
    }

    private Designation findDesignationOrThrow(Long id) {
        return designationRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException("Designation", id));
    }

    private void findStateOrThrow(String stateCode) {
        stateMasterRepository.findByStateCode(stateCode)
                .orElseThrow(() -> new MasterDataNotFoundException("State", stateCode));
    }
}
