package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.EmployeeBankAccountRequest;
import in.gov.jci.hrms.dto.EmployeeBankAccountResponse;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeBankAccount;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.repository.EmployeeBankAccountRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class EmployeeBankAccountService {

    private final EmployeeBankAccountRepository bankAccountRepository;
    private final EmployeeRepository employeeRepository;

    public EmployeeBankAccountService(EmployeeBankAccountRepository bankAccountRepository, EmployeeRepository employeeRepository) {
        this.bankAccountRepository = bankAccountRepository;
        this.employeeRepository = employeeRepository;
    }

    public List<EmployeeBankAccountResponse> listByEmployee(Long employeeId) {
        resolveEmployee(employeeId);
        return bankAccountRepository.findByEmployeeIdOrderByEffectiveFromDesc(employeeId).stream()
                .map(EmployeeBankAccountResponse::from)
                .toList();
    }

    /** Always inserts a new ACTIVE/primary row - the SCD Type-2 archival of the prior one is trigger-driven, see V30. */
    @Transactional
    public EmployeeBankAccountResponse addAccount(Long employeeId, EmployeeBankAccountRequest request) {
        EmployeeBankAccount account = new EmployeeBankAccount(
                resolveEmployee(employeeId), request.bankName(), request.bankBranch(),
                request.bankAccountNumber(), request.bankIfsc());
        account.setCancelledChequeS3Key(request.cancelledChequeS3Key());
        return EmployeeBankAccountResponse.from(bankAccountRepository.saveAndFlush(account));
    }

    private Employee resolveEmployee(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
    }
}
