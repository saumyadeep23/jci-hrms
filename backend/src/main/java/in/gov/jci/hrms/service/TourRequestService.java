package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.TourRequestRequest;
import in.gov.jci.hrms.dto.TourRequestResponse;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.TourRequest;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.TourRequestRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Minimal CRUD (create/get/list only, no approval workflow) - added because
 * TadaClaimService needs real, persisted tour requests to reference, not
 * because a full tour-request approval lifecycle was asked for this phase.
 */
@Service
@Transactional(readOnly = true)
public class TourRequestService {

    private static final String ENTITY_NAME = "Tour Request";

    private final TourRequestRepository tourRequestRepository;
    private final EmployeeRepository employeeRepository;

    public TourRequestService(TourRequestRepository tourRequestRepository, EmployeeRepository employeeRepository) {
        this.tourRequestRepository = tourRequestRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional
    public TourRequestResponse create(TourRequestRequest request) {
        Employee employee = employeeRepository.findById(request.employeeId())
                .orElseThrow(() -> new EmployeeNotFoundException(request.employeeId()));

        TourRequest tourRequest = new TourRequest(request.requestNumber(), employee, request.purpose(),
                request.origin(), request.destination(), request.startDate(), request.endDate(), request.isPostFacto());
        tourRequest.setDirectFlightCost(request.directFlightCost());
        tourRequest.setDirectHotelCost(request.directHotelCost());
        tourRequest.setDirectVehicleCost(request.directVehicleCost());

        return TourRequestResponse.from(save(tourRequest));
    }

    public TourRequestResponse getById(Long id) {
        return TourRequestResponse.from(findOrThrow(id));
    }

    public Page<TourRequestResponse> list(Pageable pageable) {
        return tourRequestRepository.findAll(pageable).map(TourRequestResponse::from);
    }

    private TourRequest findOrThrow(Long id) {
        return tourRequestRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException(ENTITY_NAME, id));
    }

    private TourRequest save(TourRequest tourRequest) {
        try {
            return tourRequestRepository.saveAndFlush(tourRequest);
        } catch (DataIntegrityViolationException ex) {
            throw new MasterDataConflictException(
                    ENTITY_NAME + " number already in use: " + tourRequest.getRequestNumber());
        }
    }
}
