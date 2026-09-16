package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.ContractualEngagementResponse;
import in.gov.jci.hrms.dto.OutsourcedDeploymentResponse;
import in.gov.jci.hrms.dto.RenewContractRequest;
import in.gov.jci.hrms.dto.RenewDeploymentRequest;
import in.gov.jci.hrms.service.ContractRenewalService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** CONTRACTUAL/OUTSOURCED contract & deployment renewal (V50) - see ContractRenewalService's javadoc. */
@RestController
@RequestMapping("/api/v1/employees/{id}")
@PreAuthorize("hasRole('HR_ADMIN')")
public class ContractRenewalController {

    private final ContractRenewalService contractRenewalService;

    public ContractRenewalController(ContractRenewalService contractRenewalService) {
        this.contractRenewalService = contractRenewalService;
    }

    @PostMapping("/renew-contract")
    public ContractualEngagementResponse renewContract(@PathVariable Long id, @Valid @RequestBody RenewContractRequest request) {
        return contractRenewalService.renewContract(id, request);
    }

    @PostMapping("/renew-deployment")
    public OutsourcedDeploymentResponse renewDeployment(@PathVariable Long id, @Valid @RequestBody RenewDeploymentRequest request) {
        return contractRenewalService.renewDeployment(id, request);
    }
}
