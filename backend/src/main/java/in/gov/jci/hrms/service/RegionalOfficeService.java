package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.DependencyCheckResponse;
import in.gov.jci.hrms.dto.DependencyUsage;
import in.gov.jci.hrms.dto.RegionalOfficeRequest;
import in.gov.jci.hrms.dto.RegionalOfficeResponse;
import in.gov.jci.hrms.entity.RegionalOffice;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataInUseException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.RegionalOfficeRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class RegionalOfficeService {

    private static final String ENTITY_NAME = "Regional Office";

    private static final List<MasterDependencyService.DependencyProbe> ID_KEYED_PROBES = List.of(
            new MasterDependencyService.DependencyProbe("employees", "ro_id", "Employees", true),
            new MasterDependencyService.DependencyProbe("post_master", "ro_id", "Sanctioned Posts", true)
    );

    private final RegionalOfficeRepository regionalOfficeRepository;
    private final EmployeeRepository employeeRepository;
    private final MasterDependencyService masterDependencyService;
    private final JdbcTemplate jdbcTemplate;

    public RegionalOfficeService(RegionalOfficeRepository regionalOfficeRepository, EmployeeRepository employeeRepository,
                                  MasterDependencyService masterDependencyService, JdbcTemplate jdbcTemplate) {
        this.regionalOfficeRepository = regionalOfficeRepository;
        this.employeeRepository = employeeRepository;
        this.masterDependencyService = masterDependencyService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public RegionalOfficeResponse create(RegionalOfficeRequest request) {
        RegionalOffice regionalOffice = new RegionalOffice(
                request.code(), request.name(), request.state(), request.cityClass(), request.active(),
                request.officeType(), request.addressLine(), request.city(), request.district(), request.districtCode(),
                request.pinCode(), request.latitude(), request.longitude(), request.geofenceRadiusMeters(),
                request.recreationClubDeduction(), request.procurementAllowanceApplicable());
        return RegionalOfficeResponse.from(save(regionalOffice));
    }

    public RegionalOfficeResponse getById(Long id) {
        return RegionalOfficeResponse.from(findOrThrow(id));
    }

    public Page<RegionalOfficeResponse> list(Pageable pageable) {
        return regionalOfficeRepository.findAll(pageable).map(RegionalOfficeResponse::from);
    }

    @Transactional
    public RegionalOfficeResponse update(Long id, RegionalOfficeRequest request) {
        RegionalOffice regionalOffice = findOrThrow(id);
        regionalOffice.setCode(request.code());
        regionalOffice.setName(request.name());
        regionalOffice.setState(request.state());
        regionalOffice.setCityClass(request.cityClass());
        regionalOffice.setActive(request.active());
        regionalOffice.setOfficeType(request.officeType());
        regionalOffice.setAddressLine(request.addressLine());
        regionalOffice.setCity(request.city());
        regionalOffice.setDistrict(request.district());
        regionalOffice.setDistrictCode(request.districtCode());
        regionalOffice.setPinCode(request.pinCode());
        regionalOffice.setLatitude(request.latitude());
        regionalOffice.setLongitude(request.longitude());
        if (request.geofenceRadiusMeters() != null) regionalOffice.setGeofenceRadiusMeters(request.geofenceRadiusMeters());
        if (request.recreationClubDeduction() != null) regionalOffice.setRecreationClubDeduction(request.recreationClubDeduction());
        regionalOffice.setProcurementAllowanceApplicable(request.procurementAllowanceApplicable());
        return RegionalOfficeResponse.from(save(regionalOffice));
    }

    @Transactional
    public RegionalOfficeResponse updateStatus(Long id, boolean active) {
        RegionalOffice regionalOffice = findOrThrow(id);
        if (!active) {
            DependencyCheckResponse dependencies = dependencies(id);
            if (dependencies.hasActiveDependencies()) {
                throw new MasterDataInUseException(ENTITY_NAME, id);
            }
            regionalOffice.setActive(false);
            regionalOffice.setDeletedAt(Instant.now());
        } else {
            regionalOffice.setActive(true);
            regionalOffice.setDeletedAt(null);
        }
        return RegionalOfficeResponse.from(regionalOffice);
    }

    /** dpc_master references its parent RO by ro_code (a natural key), not ro_id, so it can't go through the generic id-keyed probe list. */
    public DependencyCheckResponse dependencies(Long id) {
        RegionalOffice regionalOffice = findOrThrow(id);
        DependencyCheckResponse idKeyed = masterDependencyService.check(ID_KEYED_PROBES, id);
        List<DependencyUsage> usages = new ArrayList<>(idKeyed.usages());
        long dpcCount = activeDpcCount(regionalOffice.getCode());
        if (dpcCount > 0) {
            usages.add(new DependencyUsage("dpc_master", "DPCs", dpcCount));
        }
        return new DependencyCheckResponse(!usages.isEmpty(), usages);
    }

    /** Decommission: rejects when active staff (employees.ro_id) or active attached DPCs still reference this RO. */
    @Transactional
    public void delete(Long id, String reason, String deletedBy) {
        RegionalOffice regionalOffice = findOrThrow(id);
        if (employeeRepository.existsByRegionalOfficeId(id)) {
            throw new MasterDataInUseException(ENTITY_NAME, id);
        }
        if (activeDpcCount(regionalOffice.getCode()) > 0) {
            throw new MasterDataInUseException(ENTITY_NAME, id);
        }
        regionalOffice.setActive(false);
        regionalOffice.setDeletedAt(Instant.now());
        regionalOffice.setDeletedBy(deletedBy);
        regionalOffice.setDeletionReason(reason);
    }

    private long activeDpcCount(String roCode) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dpc_master WHERE ro_code = ? AND deleted_at IS NULL", Long.class, roCode);
        return count != null ? count : 0;
    }

    private RegionalOffice findOrThrow(Long id) {
        return regionalOfficeRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException(ENTITY_NAME, id));
    }

    private RegionalOffice save(RegionalOffice regionalOffice) {
        try {
            return regionalOfficeRepository.saveAndFlush(regionalOffice);
        } catch (DataIntegrityViolationException ex) {
            throw new MasterDataConflictException(ENTITY_NAME + " code already in use: " + regionalOffice.getCode());
        }
    }
}
