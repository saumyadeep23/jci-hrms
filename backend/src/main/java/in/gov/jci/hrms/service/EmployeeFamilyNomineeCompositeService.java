package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.CompositeDependentEntry;
import in.gov.jci.hrms.dto.CompositeNomineeEntry;
import in.gov.jci.hrms.dto.DependentResponse;
import in.gov.jci.hrms.dto.EmployeeFamilyNomineeCompositeRequest;
import in.gov.jci.hrms.dto.EmployeeFamilyNomineeCompositeResponse;
import in.gov.jci.hrms.dto.FamilyDetailsResponse;
import in.gov.jci.hrms.dto.NomineeResponse;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeDependent;
import in.gov.jci.hrms.entity.EmployeeFamilyDetails;
import in.gov.jci.hrms.entity.EmployeeNominee;
import in.gov.jci.hrms.entity.FamilyRelationshipType;
import in.gov.jci.hrms.entity.NominationType;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.EmployeeDependentRepository;
import in.gov.jci.hrms.repository.EmployeeFamilyDetailsRepository;
import in.gov.jci.hrms.repository.EmployeeNomineeRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Backs the Family & Nominees edit tab's single "Save Changes" action - the whole Family Register
 * (dependents) and both Nomination Master tabs (PF/Gratuity) are read and written as one unit,
 * replacing the old three-separate-endpoints/three-separate-save-buttons flow
 * (EmployeeFamilyController/EmployeeDependentController/EmployeeNomineeController, all kept as-is for
 * any other caller). See CompositeDependentEntry/CompositeNomineeEntry for the clientKey linking
 * mechanism that lets a brand-new family member be nominated in the same save.
 */
@Service
@Transactional(readOnly = true)
public class EmployeeFamilyNomineeCompositeService {

    private static final BigDecimal FULL_SHARE = new BigDecimal("100.00");

    private final EmployeeRepository employeeRepository;
    private final EmployeeFamilyDetailsRepository familyDetailsRepository;
    private final EmployeeDependentRepository dependentRepository;
    private final EmployeeNomineeRepository nomineeRepository;

    public EmployeeFamilyNomineeCompositeService(EmployeeRepository employeeRepository,
                                                  EmployeeFamilyDetailsRepository familyDetailsRepository,
                                                  EmployeeDependentRepository dependentRepository,
                                                  EmployeeNomineeRepository nomineeRepository) {
        this.employeeRepository = employeeRepository;
        this.familyDetailsRepository = familyDetailsRepository;
        this.dependentRepository = dependentRepository;
        this.nomineeRepository = nomineeRepository;
    }

    public EmployeeFamilyNomineeCompositeResponse get(Long employeeId) {
        resolveEmployee(employeeId);
        return buildResponse(employeeId);
    }

    @Transactional
    public EmployeeFamilyNomineeCompositeResponse save(Long employeeId, EmployeeFamilyNomineeCompositeRequest request) {
        Employee employee = resolveEmployee(employeeId);
        validateShares(request.pfNominees(), "PF");
        validateShares(request.gratuityNominees(), "Gratuity");
        validateSingleInstanceRelationships(request.dependents());

        upsertFamily(employee, request);
        Map<String, EmployeeDependent> dependentsByClientKey = syncDependents(employee, request.dependents());
        syncNominees(employee, NominationType.PF, request.pfNominees(), dependentsByClientKey);
        syncNominees(employee, NominationType.GRATUITY, request.gratuityNominees(), dependentsByClientKey);

        familyDetailsRepository.findByEmployeeId(employeeId)
                .ifPresent(details -> details.setDependentCount(dependentRepository.findByEmployeeId(employeeId).size()));

        return buildResponse(employeeId);
    }

    private void validateShares(List<CompositeNomineeEntry> nominees, String label) {
        if (nominees.isEmpty()) {
            return;
        }
        BigDecimal total = nominees.stream().map(CompositeNomineeEntry::sharePercentage).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(FULL_SHARE) != 0) {
            throw new BusinessRuleViolationException(
                    label + " nominee share percentages must sum to exactly 100% (got " + total + ")");
        }
    }

    private static final List<FamilyRelationshipType> SINGLE_INSTANCE_RELATIONSHIPS =
            List.of(FamilyRelationshipType.FATHER, FamilyRelationshipType.MOTHER, FamilyRelationshipType.SPOUSE);

    /**
     * Government single-relationship rule: at most one Father, one Mother, one Spouse per employee.
     * The frontend already hides a taken relationship from every other row's dropdown (see
     * EmployeeFamilyNomineesPanel's own relationshipOptionsFor()), so reaching this in practice means
     * that client-side guard was bypassed somehow - this is the authoritative check. A plain
     * BusinessRuleViolationException (400), same as every other request-shape violation in this
     * service - there's no dedicated exception type for this in the codebase, and one specific to a
     * single field isn't warranted (unlike, say, InsufficientLeaveBalanceException, which maps to a
     * genuinely different HTTP status).
     */
    private void validateSingleInstanceRelationships(List<CompositeDependentEntry> entries) {
        for (FamilyRelationshipType relationship : SINGLE_INSTANCE_RELATIONSHIPS) {
            long count = entries.stream().filter(e -> e.relationship() == relationship).count();
            if (count > 1) {
                throw new BusinessRuleViolationException(
                        "Only one " + relationship + " entry is allowed in the Family & Dependent Register (found " + count + ")");
            }
        }
    }

    /**
     * employee_family_details.father_name/mother_name/spouse_name/spouse_dob are no longer taken
     * directly off the request - they're derived from whichever FATHER/MOTHER/SPOUSE row is present in
     * the submitted Family &amp; Dependent Register, since the tab's own separate "Father's Name"/
     * "Mother's Name"/"Spouse's Name"/"Spouse's Date of Birth" inputs were redundant with those same
     * rows and have been removed from the UI. father_name is NOT NULL at the DB level, and a Father's
     * Name was always a required field on the old form - that requirement is now enforced here instead:
     * the register must include at least one FATHER row. Mother/spouse remain optional, matching their
     * nullable columns.
     */
    private void upsertFamily(Employee employee, EmployeeFamilyNomineeCompositeRequest request) {
        String fatherName = firstByRelationship(request.dependents(), FamilyRelationshipType.FATHER)
                .map(CompositeDependentEntry::name)
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "The Family & Dependent Register must include a Father entry"));
        String motherName = firstByRelationship(request.dependents(), FamilyRelationshipType.MOTHER)
                .map(CompositeDependentEntry::name)
                .orElse(null);
        CompositeDependentEntry spouseEntry = firstByRelationship(request.dependents(), FamilyRelationshipType.SPOUSE)
                .orElse(null);

        EmployeeFamilyDetails details = familyDetailsRepository.findByEmployeeId(employee.getId())
                .orElseGet(() -> new EmployeeFamilyDetails(employee, fatherName));
        details.setFatherName(fatherName);
        details.setMotherName(motherName);
        details.setSpouseName(spouseEntry != null ? spouseEntry.name() : null);
        details.setSpouseDob(spouseEntry != null ? spouseEntry.dateOfBirth() : null);
        familyDetailsRepository.saveAndFlush(details);
    }

    /** findFirst() is safe here - validateSingleInstanceRelationships() has already rejected more than one FATHER/MOTHER/SPOUSE row by the time this runs. */
    private Optional<CompositeDependentEntry> firstByRelationship(List<CompositeDependentEntry> entries, FamilyRelationshipType relationship) {
        return entries.stream().filter(e -> e.relationship() == relationship).findFirst();
    }

    /** Returns a clientKey -> persisted-entity map covering every dependent in the request (new and existing), for syncNominees() to resolve against. */
    private Map<String, EmployeeDependent> syncDependents(Employee employee, List<CompositeDependentEntry> entries) {
        List<EmployeeDependent> existing = dependentRepository.findByEmployeeId(employee.getId());
        Map<Long, EmployeeDependent> existingById = new HashMap<>();
        existing.forEach(d -> existingById.put(d.getId(), d));

        Set<Long> keepIds = new HashSet<>();
        Map<String, EmployeeDependent> byClientKey = new HashMap<>();

        for (CompositeDependentEntry entry : entries) {
            EmployeeDependent dependent;
            if (entry.id() != null) {
                dependent = existingById.get(entry.id());
                if (dependent == null || !dependent.getEmployee().getId().equals(employee.getId())) {
                    throw new MasterDataNotFoundException("Dependent", entry.id());
                }
                keepIds.add(entry.id());
            } else {
                dependent = new EmployeeDependent(employee, entry.name(), entry.relationship(), entry.isDependent(), entry.isCoveredMedical());
            }
            dependent.setName(entry.name());
            dependent.setRelationship(entry.relationship());
            dependent.setDateOfBirth(entry.dateOfBirth());
            dependent.setDependent(entry.isDependent());
            dependent.setCoveredMedical(entry.isCoveredMedical());
            dependent.setGender(entry.gender());
            dependent.setDivyang(entry.isDivyang());
            dependent.setDisabilityPercentage(entry.disabilityPercentage());
            dependent.setMultipleBirthSecondDelivery(entry.isMultipleBirthSecondDelivery());
            dependent = dependentRepository.saveAndFlush(dependent);
            byClientKey.put(entry.clientKey(), dependent);
        }

        for (EmployeeDependent old : existing) {
            if (!keepIds.contains(old.getId())) {
                old.setDeletedAt(Instant.now());
            }
        }
        return byClientKey;
    }

    private void syncNominees(Employee employee, NominationType type, List<CompositeNomineeEntry> entries,
                               Map<String, EmployeeDependent> dependentsByClientKey) {
        List<EmployeeNominee> existingOfType = nomineeRepository.findByEmployeeId(employee.getId()).stream()
                .filter(n -> n.getNomineeFor() == type)
                .toList();
        Map<Long, EmployeeNominee> existingById = new HashMap<>();
        existingOfType.forEach(n -> existingById.put(n.getId(), n));

        Set<Long> keepIds = new HashSet<>();

        for (CompositeNomineeEntry entry : entries) {
            EmployeeDependent dependent = dependentsByClientKey.get(entry.dependentClientKey());
            if (dependent == null) {
                throw new BusinessRuleViolationException(
                        type + " nominee references a family register entry that isn't in this request: " + entry.dependentClientKey());
            }
            EmployeeNominee nominee;
            if (entry.id() != null) {
                nominee = existingById.get(entry.id());
                if (nominee == null || !nominee.getEmployee().getId().equals(employee.getId())) {
                    throw new MasterDataNotFoundException("Nominee", entry.id());
                }
                keepIds.add(entry.id());
            } else {
                nominee = new EmployeeNominee(employee, dependent.getName(), dependent.getRelationship(), entry.sharePercentage(), type);
            }
            nominee.setName(dependent.getName());
            nominee.setRelationship(dependent.getRelationship());
            nominee.setSharePercentage(entry.sharePercentage());
            nominee.setNomineeFor(type);
            nominee.setDependent(dependent);
            nomineeRepository.saveAndFlush(nominee);
        }

        for (EmployeeNominee old : existingOfType) {
            if (!keepIds.contains(old.getId())) {
                old.setDeletedAt(Instant.now());
            }
        }
    }

    private EmployeeFamilyNomineeCompositeResponse buildResponse(Long employeeId) {
        FamilyDetailsResponse family = familyDetailsRepository.findByEmployeeId(employeeId)
                .map(FamilyDetailsResponse::from)
                .orElse(null);
        List<DependentResponse> dependents = dependentRepository.findByEmployeeId(employeeId).stream()
                .map(DependentResponse::from)
                .toList();
        List<EmployeeNominee> nominees = nomineeRepository.findByEmployeeId(employeeId);
        List<NomineeResponse> pfNominees = nominees.stream()
                .filter(n -> n.getNomineeFor() == NominationType.PF)
                .map(NomineeResponse::from)
                .toList();
        List<NomineeResponse> gratuityNominees = nominees.stream()
                .filter(n -> n.getNomineeFor() == NominationType.GRATUITY)
                .map(NomineeResponse::from)
                .toList();
        return new EmployeeFamilyNomineeCompositeResponse(family, dependents, pfNominees, gratuityNominees);
    }

    private Employee resolveEmployee(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
    }
}
