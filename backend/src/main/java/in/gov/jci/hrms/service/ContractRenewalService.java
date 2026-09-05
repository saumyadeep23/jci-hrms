package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.ContractualEngagementResponse;
import in.gov.jci.hrms.dto.OutsourcedDeploymentResponse;
import in.gov.jci.hrms.dto.RenewContractRequest;
import in.gov.jci.hrms.dto.RenewDeploymentRequest;
import in.gov.jci.hrms.entity.ContractualEngagement;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.GradeScaleMaster;
import in.gov.jci.hrms.entity.OutsourcedDeployment;
import in.gov.jci.hrms.entity.VendorMaster;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.ContractualEngagementRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.GradeScaleMasterRepository;
import in.gov.jci.hrms.repository.OutsourcedDeploymentRepository;
import in.gov.jci.hrms.repository.VendorMasterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Renewal for CONTRACTUAL/OUTSOURCED employees (V50) - deactivates the employee's current
 * contractual_engagements/outsourced_deployments row and inserts the renewed terms as the new
 * current one, mirroring MovementOrderService's promotion pay-fixation history pattern for REGULAR
 * employees.
 */
@Service
@Transactional(readOnly = true)
public class ContractRenewalService {

    private final EmployeeRepository employeeRepository;
    private final ContractualEngagementRepository contractualEngagementRepository;
    private final OutsourcedDeploymentRepository outsourcedDeploymentRepository;
    private final GradeScaleMasterRepository gradeScaleMasterRepository;
    private final VendorMasterRepository vendorMasterRepository;

    public ContractRenewalService(EmployeeRepository employeeRepository,
                                   ContractualEngagementRepository contractualEngagementRepository,
                                   OutsourcedDeploymentRepository outsourcedDeploymentRepository,
                                   GradeScaleMasterRepository gradeScaleMasterRepository,
                                   VendorMasterRepository vendorMasterRepository) {
        this.employeeRepository = employeeRepository;
        this.contractualEngagementRepository = contractualEngagementRepository;
        this.outsourcedDeploymentRepository = outsourcedDeploymentRepository;
        this.gradeScaleMasterRepository = gradeScaleMasterRepository;
        this.vendorMasterRepository = vendorMasterRepository;
    }

    @Transactional
    public ContractualEngagementResponse renewContract(Long employeeId, RenewContractRequest request) {
        Employee employee = resolveEmployee(employeeId);
        contractualEngagementRepository.findByEmployeeIdAndCurrentTrue(employeeId)
                .ifPresent(previous -> previous.setCurrent(false));

        ContractualEngagement renewed = new ContractualEngagement(
                employee, request.monthlyLumpsum(), request.contractStartDate(), request.contractEndDate(), request.approvalRefNo());
        renewed.setEngagementTerms(request.engagementTerms());
        if (request.scaleCode() != null) {
            renewed.setGradeScale(resolveGradeScale(request.scaleCode()));
        }
        return ContractualEngagementResponse.from(contractualEngagementRepository.saveAndFlush(renewed));
    }

    @Transactional
    public OutsourcedDeploymentResponse renewDeployment(Long employeeId, RenewDeploymentRequest request) {
        Employee employee = resolveEmployee(employeeId);
        outsourcedDeploymentRepository.findByEmployeeIdAndCurrentTrue(employeeId)
                .ifPresent(previous -> previous.setCurrent(false));

        OutsourcedDeployment renewed = new OutsourcedDeployment(
                employee, request.monthlyCtc(), request.deploymentStartDate(), request.deploymentEndDate(), request.workOrderRef());
        renewed.setAgencyBillingRate(request.agencyBillingRate());
        if (request.vendorId() != null) {
            renewed.setVendor(resolveVendor(request.vendorId()));
        }
        if (request.scaleCode() != null) {
            renewed.setGradeScale(resolveGradeScale(request.scaleCode()));
        }
        return OutsourcedDeploymentResponse.from(outsourcedDeploymentRepository.saveAndFlush(renewed));
    }

    private Employee resolveEmployee(Long employeeId) {
        return employeeRepository.findById(employeeId).orElseThrow(() -> new EmployeeNotFoundException(employeeId));
    }

    private GradeScaleMaster resolveGradeScale(String scaleCode) {
        return gradeScaleMasterRepository.findByScaleCode(scaleCode)
                .orElseThrow(() -> new MasterDataNotFoundException("Grade Scale", scaleCode));
    }

    private VendorMaster resolveVendor(Long vendorId) {
        return vendorMasterRepository.findById(vendorId)
                .orElseThrow(() -> new MasterDataNotFoundException("Vendor", vendorId));
    }
}
