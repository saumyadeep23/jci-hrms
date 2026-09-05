package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.EmployeeAddressRequest;
import in.gov.jci.hrms.dto.EmployeeAddressResponse;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeAddress;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.repository.EmployeeAddressRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Upsert-by-type: each (employee, addressType) pair has at most one row - see uq_emp_address_type (V30). */
@Service
@Transactional(readOnly = true)
public class EmployeeAddressService {

    private final EmployeeAddressRepository addressRepository;
    private final EmployeeRepository employeeRepository;

    public EmployeeAddressService(EmployeeAddressRepository addressRepository, EmployeeRepository employeeRepository) {
        this.addressRepository = addressRepository;
        this.employeeRepository = employeeRepository;
    }

    public List<EmployeeAddressResponse> listByEmployee(Long employeeId) {
        resolveEmployee(employeeId);
        return addressRepository.findByEmployeeId(employeeId).stream()
                .map(EmployeeAddressResponse::from)
                .toList();
    }

    @Transactional
    public EmployeeAddressResponse upsert(Long employeeId, EmployeeAddressRequest request) {
        EmployeeAddress address = addressRepository.findByEmployeeIdAndAddressType(employeeId, request.addressType())
                .orElseGet(() -> new EmployeeAddress(
                        resolveEmployee(employeeId), request.addressType(), request.addressLine1(),
                        request.city(), request.district(), request.state(), request.pinCode()));

        address.setAddressLine1(request.addressLine1());
        address.setAddressLine2(request.addressLine2());
        address.setPostOffice(request.postOffice());
        address.setPoliceStation(request.policeStation());
        address.setCity(request.city());
        address.setDistrict(request.district());
        address.setState(request.state());
        address.setPinCode(request.pinCode());

        return EmployeeAddressResponse.from(addressRepository.saveAndFlush(address));
    }

    private Employee resolveEmployee(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
    }
}
