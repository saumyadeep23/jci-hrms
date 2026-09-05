package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.DependencyCheckResponse;
import in.gov.jci.hrms.dto.DpcRequest;
import in.gov.jci.hrms.dto.DpcResponse;
import in.gov.jci.hrms.entity.DepartmentalPurchaseCentre;
import in.gov.jci.hrms.entity.RegionalOffice;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataInUseException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.DepartmentalPurchaseCentreRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.RegionalOfficeRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class DpcService {

    private static final String ENTITY_NAME = "DPC";
    /** Applied when the request doesn't specify one - matches the shared dev database's own dpc_master.geofence_radius_meters column default. */
    private static final int DEFAULT_GEOFENCE_RADIUS_METERS = 100;

    private static final List<MasterDependencyService.DependencyProbe> DEPENDENCY_PROBES = List.of(
            new MasterDependencyService.DependencyProbe("employees", "dpc_id", "Employees", true),
            new MasterDependencyService.DependencyProbe("post_master", "dpc_id", "Sanctioned Posts", true)
    );

    private final DepartmentalPurchaseCentreRepository dpcRepository;
    private final RegionalOfficeRepository regionalOfficeRepository;
    private final EmployeeRepository employeeRepository;
    private final MasterDependencyService masterDependencyService;
    private final JdbcTemplate jdbcTemplate;

    public DpcService(DepartmentalPurchaseCentreRepository dpcRepository,
                       RegionalOfficeRepository regionalOfficeRepository,
                       EmployeeRepository employeeRepository,
                       MasterDependencyService masterDependencyService,
                       JdbcTemplate jdbcTemplate) {
        this.dpcRepository = dpcRepository;
        this.regionalOfficeRepository = regionalOfficeRepository;
        this.employeeRepository = employeeRepository;
        this.masterDependencyService = masterDependencyService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public DpcResponse create(DpcRequest request) {
        RegionalOffice regionalOffice = resolveRegionalOffice(request.roId());
        DepartmentalPurchaseCentre dpc = new DepartmentalPurchaseCentre(
                regionalOffice, request.code(), request.name(),
                request.district(), request.state(), request.active(),
                request.shortName(), request.dpcType(), request.districtCode(), request.cityClass());
        applyOptionalFields(dpc, request);
        return DpcResponse.from(save(dpc));
    }

    public DpcResponse getById(Long id) {
        return DpcResponse.from(findOrThrow(id));
    }

    public Page<DpcResponse> list(Pageable pageable) {
        return dpcRepository.findAll(pageable).map(DpcResponse::from);
    }

    @Transactional
    public DpcResponse update(Long id, DpcRequest request) {
        DepartmentalPurchaseCentre dpc = findOrThrow(id);
        RegionalOffice regionalOffice = resolveRegionalOffice(request.roId());
        dpc.setRegionalOffice(regionalOffice);
        dpc.setCode(request.code());
        dpc.setName(request.name());
        dpc.setDistrict(request.district());
        dpc.setState(request.state());
        dpc.setActive(request.active());
        dpc.setShortName(request.shortName());
        dpc.setDpcType(request.dpcType());
        dpc.setDistrictCode(request.districtCode());
        dpc.setCityClass(request.cityClass());
        applyOptionalFields(dpc, request);
        return DpcResponse.from(save(dpc));
    }

    /** Decommission: rejects when active staff (employees.dpc_id) or an active post_incumbency at one of this DPC's sanctioned posts still exists. */
    @Transactional
    public void delete(Long id, String reason, String deletedBy) {
        DepartmentalPurchaseCentre dpc = findOrThrow(id);
        if (employeeRepository.existsByDepartmentalPurchaseCentreId(id)) {
            throw new MasterDataInUseException(ENTITY_NAME, id);
        }
        if (hasActivePostIncumbency(id)) {
            throw new MasterDataInUseException(ENTITY_NAME, id);
        }
        dpc.setActive(false);
        dpc.setDeletedAt(Instant.now());
        dpc.setDeletedBy(deletedBy);
        dpc.setDeletionReason(reason);
    }

    private boolean hasActivePostIncumbency(Long dpcId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM post_incumbency pi JOIN post_master pm ON pi.post_id = pm.id "
                        + "WHERE pm.dpc_id = ? AND pi.is_active = true AND pi.deleted_at IS NULL AND pm.deleted_at IS NULL",
                Long.class, dpcId);
        return count != null && count > 0;
    }

    @Transactional
    public DpcResponse updateStatus(Long id, boolean active) {
        DepartmentalPurchaseCentre dpc = findOrThrow(id);
        if (!active) {
            DependencyCheckResponse dependencies = masterDependencyService.check(DEPENDENCY_PROBES, id);
            if (dependencies.hasActiveDependencies()) {
                throw new MasterDataInUseException(ENTITY_NAME, id);
            }
            dpc.setActive(false);
            dpc.setDeletedAt(Instant.now());
        } else {
            dpc.setActive(true);
            dpc.setDeletedAt(null);
        }
        return DpcResponse.from(dpc);
    }

    public DependencyCheckResponse dependencies(Long id) {
        findOrThrow(id);
        return masterDependencyService.check(DEPENDENCY_PROBES, id);
    }

    private void applyOptionalFields(DepartmentalPurchaseCentre dpc, DpcRequest request) {
        dpc.setLatitude(request.latitude());
        dpc.setLongitude(request.longitude());
        dpc.setGeofenceRadiusMeters(
                request.geofenceRadiusMeters() != null ? request.geofenceRadiusMeters() : DEFAULT_GEOFENCE_RADIUS_METERS);
    }

    private RegionalOffice resolveRegionalOffice(Long roId) {
        return regionalOfficeRepository.findById(roId)
                .orElseThrow(() -> new MasterDataNotFoundException("Regional Office", roId));
    }

    private DepartmentalPurchaseCentre findOrThrow(Long id) {
        return dpcRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException(ENTITY_NAME, id));
    }

    private DepartmentalPurchaseCentre save(DepartmentalPurchaseCentre dpc) {
        try {
            return dpcRepository.saveAndFlush(dpc);
        } catch (DataIntegrityViolationException ex) {
            throw new MasterDataConflictException(ENTITY_NAME + " code already in use: " + dpc.getCode());
        }
    }
}
