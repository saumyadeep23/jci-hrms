package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.DependencyCheckResponse;
import in.gov.jci.hrms.dto.VendorMasterRequest;
import in.gov.jci.hrms.dto.VendorMasterResponse;
import in.gov.jci.hrms.entity.VendorMaster;
import in.gov.jci.hrms.exception.DuplicateResourceException;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataInUseException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.exception.MasterDataValidationException;
import in.gov.jci.hrms.repository.VendorMasterRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

/** PIMS_SPEC.md Section 1.A.7 (Manpower Vendors). */
@Service
@Transactional(readOnly = true)
public class VendorMasterService {

    private static final String ENTITY_NAME = "Vendor";

    private static final Pattern GSTIN_PATTERN =
            Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$");

    private static final List<MasterDependencyService.DependencyProbe> DEPENDENCY_PROBES = List.of(
            new MasterDependencyService.DependencyProbe("employee_employment_categories", "vendor_id", "Employment Categories", true)
    );

    private final VendorMasterRepository vendorMasterRepository;
    private final MasterDependencyService masterDependencyService;
    private final VendorCodeGeneratorService vendorCodeGeneratorService;

    public VendorMasterService(VendorMasterRepository vendorMasterRepository, MasterDependencyService masterDependencyService,
                                VendorCodeGeneratorService vendorCodeGeneratorService) {
        this.vendorMasterRepository = vendorMasterRepository;
        this.masterDependencyService = masterDependencyService;
        this.vendorCodeGeneratorService = vendorCodeGeneratorService;
    }

    @Transactional
    public VendorMasterResponse create(VendorMasterRequest request) {
        VendorMaster vendor = new VendorMaster(vendorCodeGeneratorService.generateNext(), request.vendorName(),
                request.contractStartDate(), request.contractEndDate());
        applyOptionalFields(vendor, request, null);
        return VendorMasterResponse.from(save(vendor));
    }

    public VendorMasterResponse getById(Long id) {
        return VendorMasterResponse.from(findOrThrow(id));
    }

    public Page<VendorMasterResponse> list(Pageable pageable) {
        return vendorMasterRepository.findAll(pageable).map(VendorMasterResponse::from);
    }

    @Transactional
    public VendorMasterResponse update(Long id, VendorMasterRequest request) {
        VendorMaster vendor = findOrThrow(id);
        // vendorCode is system-generated at creation and never changes on update.
        vendor.setVendorName(request.vendorName());
        vendor.setContractStartDate(request.contractStartDate());
        vendor.setContractEndDate(request.contractEndDate());
        applyOptionalFields(vendor, request, id);
        return VendorMasterResponse.from(save(vendor));
    }

    private void applyOptionalFields(VendorMaster vendor, VendorMasterRequest request, Long excludeId) {
        vendor.setTradeName(request.tradeName());
        vendor.setGstin(validateAndCheckGstinUniqueness(request.gstin(), excludeId));
        vendor.setPanNumber(request.panNumber());
        vendor.setEpfRegistrationNo(request.epfRegistrationNo());
        vendor.setEsicRegistrationNo(request.esicRegistrationNo());
        vendor.setContactPerson(request.contactPerson());
        vendor.setContactPhone(request.contactPhone());
        vendor.setContactEmail(request.contactEmail());
        vendor.setOfficeAddress(request.officeAddress());
        if (request.serviceChargePercentage() != null) {
            vendor.setServiceChargePercentage(request.serviceChargePercentage());
        }
        vendor.setActive(request.active());
    }

    /**
     * request.gstin() is already trimmed/uppercased by VendorMasterRequest's compact
     * constructor, so this only needs to (re-)validate the format defensively and
     * enforce uniqueness - the DB's vendor_master_gstin_key unique constraint is a
     * last-resort safety net against the race in save() below, not the primary check.
     */
    private String validateAndCheckGstinUniqueness(String gstin, Long excludeId) {
        if (gstin == null) {
            return null;
        }
        if (!GSTIN_PATTERN.matcher(gstin).matches()) {
            throw new MasterDataValidationException("Invalid GSTIN format.");
        }
        boolean duplicate = excludeId == null
                ? vendorMasterRepository.existsByGstinIgnoreCase(gstin)
                : vendorMasterRepository.existsByGstinIgnoreCaseAndIdNot(gstin, excludeId);
        if (duplicate) {
            throw new DuplicateResourceException("A vendor with GSTIN " + gstin + " already exists.");
        }
        return gstin;
    }

    @Transactional
    public VendorMasterResponse updateStatus(Long id, boolean active) {
        VendorMaster vendor = findOrThrow(id);
        if (!active) {
            DependencyCheckResponse dependencies = masterDependencyService.check(DEPENDENCY_PROBES, id);
            if (dependencies.hasActiveDependencies()) {
                throw new MasterDataInUseException(ENTITY_NAME, id);
            }
            vendor.setActive(false);
            vendor.setDeletedAt(Instant.now());
        } else {
            vendor.setActive(true);
            vendor.setDeletedAt(null);
        }
        return VendorMasterResponse.from(vendor);
    }

    public DependencyCheckResponse dependencies(Long id) {
        findOrThrow(id);
        return masterDependencyService.check(DEPENDENCY_PROBES, id);
    }

    @Transactional
    public void delete(Long id) {
        updateStatus(id, false);
    }

    private VendorMaster findOrThrow(Long id) {
        return vendorMasterRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException(ENTITY_NAME, id));
    }

    private VendorMaster save(VendorMaster vendor) {
        try {
            return vendorMasterRepository.saveAndFlush(vendor);
        } catch (DataIntegrityViolationException ex) {
            throw new MasterDataConflictException(ENTITY_NAME + " code or GSTIN already in use: " + vendor.getVendorCode());
        }
    }
}
