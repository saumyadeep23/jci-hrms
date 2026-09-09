package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeFinancialYearId;
import in.gov.jci.hrms.entity.EmployeeTaxRegime;
import in.gov.jci.hrms.entity.PayrollStatutoryParameter;
import in.gov.jci.hrms.entity.PayrollTaxOverride;
import in.gov.jci.hrms.entity.PayrollTaxSlab;
import in.gov.jci.hrms.entity.TaxRegimeType;
import in.gov.jci.hrms.entity.ViewEmployeeTaxYtdAggregate;
import in.gov.jci.hrms.repository.EmployeeTaxRegimeRepository;
import in.gov.jci.hrms.repository.PayrollStatutoryParameterRepository;
import in.gov.jci.hrms.repository.PayrollTaxOverrideRepository;
import in.gov.jci.hrms.repository.PayrollTaxSlabRepository;
import in.gov.jci.hrms.repository.ViewEmployeeTaxYtdAggregateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * Section 192 TDS Engine - a genuinely separate algorithm (cumulative-actuals projection, regime
 * resolution, progressive slabs + cess, Sec 87A rebate, and the Bill Section override hook) from
 * PayrollComputationService's earnings/deductions computation, so it lives in its own class even
 * though PayrollComputationService.processBatch() is the single entry point that calls it - see that
 * class's own javadoc.
 *
 * <p>Simplifications made explicit rather than silently assumed (no richer data model exists to do
 * better):
 * <ul>
 *   <li>Sec 80CCD(2) employer-NPS exemption is estimated as 12 * (current month's Basic+DA) *
 *   NPS_EMPLOYER_RATE/100 for NPS-eligible employees - employer NPS contributions are not themselves
 *   added to gross_amount anywhere in this engine, so this is a taxable-income reduction on top of,
 *   not a netting-out of, an amount already in gross.</li>
 *   <li>Sec 87A rebate is the simple cliff-edge form (full rebate below the threshold, none above) -
 *   marginal relief just above the NEW-regime threshold is not modelled.</li>
 *   <li>Only Standard Deduction and Sec 80CCD(2) are applied - there is no data model for 80C/80D/HRA
 *   exemption/etc. under the OLD regime.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class PayrollTdsEngine {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal STANDARD_DEDUCTION_NEW = new BigDecimal("75000.00");
    private static final BigDecimal STANDARD_DEDUCTION_OLD = new BigDecimal("50000.00");
    private static final BigDecimal REBATE_THRESHOLD_NEW = new BigDecimal("700000.00");
    private static final BigDecimal REBATE_THRESHOLD_OLD = new BigDecimal("500000.00");
    private static final BigDecimal DEFAULT_NPS_EMPLOYER_RATE = new BigDecimal("10.00");

    private final ViewEmployeeTaxYtdAggregateRepository ytdAggregateRepository;
    private final EmployeeTaxRegimeRepository employeeTaxRegimeRepository;
    private final PayrollTaxSlabRepository payrollTaxSlabRepository;
    private final PayrollTaxOverrideRepository payrollTaxOverrideRepository;
    private final PayrollStatutoryParameterRepository payrollStatutoryParameterRepository;

    public PayrollTdsEngine(ViewEmployeeTaxYtdAggregateRepository ytdAggregateRepository,
                             EmployeeTaxRegimeRepository employeeTaxRegimeRepository,
                             PayrollTaxSlabRepository payrollTaxSlabRepository,
                             PayrollTaxOverrideRepository payrollTaxOverrideRepository,
                             PayrollStatutoryParameterRepository payrollStatutoryParameterRepository) {
        this.ytdAggregateRepository = ytdAggregateRepository;
        this.employeeTaxRegimeRepository = employeeTaxRegimeRepository;
        this.payrollTaxSlabRepository = payrollTaxSlabRepository;
        this.payrollTaxOverrideRepository = payrollTaxOverrideRepository;
        this.payrollStatutoryParameterRepository = payrollStatutoryParameterRepository;
    }

    public record TdsResult(
            BigDecimal calculatedAmount,
            BigDecimal finalAmount,
            boolean overridden,
            TaxRegimeType regime,
            int remainingMonths,
            BigDecimal projectedAnnualGross,
            BigDecimal projectedAnnualTax
    ) {
    }

    /** Jan-Mar: months left including the current one within Jan-Mar. Apr-Dec: months left to the following March, including the current one. */
    public int remainingMonths(int salMonth) {
        if (salMonth >= 1 && salMonth <= 3) {
            return 3 - salMonth + 1;
        }
        return 12 - salMonth + 4;
    }

    /**
     * currentGrossAmount and regularMonthlyGross are deliberately separate parameters: a one-time
     * amount folded into THIS month's gross (e.g. Head 20 leave encashment) must inflate this month's
     * own cumulative-actuals addition and therefore its own TDS pro-rata share, but must NOT also
     * inflate the projection of the remaining (N-1) months, which should keep projecting off the
     * employee's ordinary recurring gross. Pass the same value for both when there is no such one-time
     * component to exclude.
     */
    public TdsResult computeMonthlyTds(Employee employee, String financialYear, int salMonth, int salYear,
                                        BigDecimal currentGrossAmount, BigDecimal regularMonthlyGross,
                                        BigDecimal currentBasicPlusDa, boolean npsEligibleForEmployerExemption) {
        int remainingMonths = remainingMonths(salMonth);

        Optional<ViewEmployeeTaxYtdAggregate> ytd =
                ytdAggregateRepository.findById(new EmployeeFinancialYearId(employee.getId(), financialYear));
        BigDecimal cumulativeGross = ytd.map(ViewEmployeeTaxYtdAggregate::getTotalCumulativeGross).orElse(BigDecimal.ZERO);
        BigDecimal cumulativeTdsPaid = ytd.map(ViewEmployeeTaxYtdAggregate::getTotalCumulativeTdsPaid).orElse(BigDecimal.ZERO);

        BigDecimal projectedAnnualGross = cumulativeGross
                .add(currentGrossAmount)
                .add(regularMonthlyGross.multiply(BigDecimal.valueOf(Math.max(0, remainingMonths - 1))));

        TaxRegimeType regime = employeeTaxRegimeRepository.findByEmployee_IdAndFinancialYear(employee.getId(), financialYear)
                .map(EmployeeTaxRegime::getSelectedRegime)
                .orElse(TaxRegimeType.NEW);

        BigDecimal standardDeduction = regime == TaxRegimeType.NEW ? STANDARD_DEDUCTION_NEW : STANDARD_DEDUCTION_OLD;
        BigDecimal employerNpsExemption = npsEligibleForEmployerExemption
                ? round(currentBasicPlusDa.multiply(BigDecimal.valueOf(12)).multiply(resolveNpsEmployerRate()).divide(HUNDRED, 10, RoundingMode.HALF_UP))
                : BigDecimal.ZERO;

        BigDecimal taxableIncome = projectedAnnualGross.subtract(standardDeduction).subtract(employerNpsExemption).max(BigDecimal.ZERO);

        List<PayrollTaxSlab> slabs = payrollTaxSlabRepository.findByFinancialYearAndRegimeOrderBySlabMinAsc(financialYear, regime);
        BigDecimal slabTax = BigDecimal.ZERO;
        BigDecimal applicableCessRate = new BigDecimal("4.00");
        for (PayrollTaxSlab slab : slabs) {
            if (taxableIncome.compareTo(slab.getSlabMin()) <= 0) {
                continue;
            }
            BigDecimal bracketCeiling = slab.getSlabMax() != null ? slab.getSlabMax().min(taxableIncome) : taxableIncome;
            BigDecimal bracketAmount = bracketCeiling.subtract(slab.getSlabMin());
            if (bracketAmount.signum() > 0) {
                slabTax = slabTax.add(bracketAmount.multiply(slab.getTaxRate()).divide(HUNDRED, 10, RoundingMode.HALF_UP));
                applicableCessRate = slab.getCessRate();
            }
        }

        BigDecimal rebateThreshold = regime == TaxRegimeType.NEW ? REBATE_THRESHOLD_NEW : REBATE_THRESHOLD_OLD;
        if (taxableIncome.compareTo(rebateThreshold) <= 0) {
            slabTax = BigDecimal.ZERO;
        }

        BigDecimal cess = round(slabTax.multiply(applicableCessRate).divide(HUNDRED, 10, RoundingMode.HALF_UP));
        BigDecimal projectedAnnualTax = round(slabTax.add(cess));

        BigDecimal remainingTax = projectedAnnualTax.subtract(cumulativeTdsPaid).max(BigDecimal.ZERO);
        BigDecimal calculatedAmount = round(remainingTax.divide(BigDecimal.valueOf(remainingMonths), 10, RoundingMode.HALF_UP));

        Optional<PayrollTaxOverride> override =
                payrollTaxOverrideRepository.findByEmployee_IdAndPayrollYearAndPayrollMonth(employee.getId(), salYear, salMonth);
        BigDecimal finalAmount = override.map(PayrollTaxOverride::getOverriddenAmount).orElse(calculatedAmount);

        return new TdsResult(calculatedAmount, finalAmount, override.isPresent(), regime, remainingMonths,
                projectedAnnualGross, projectedAnnualTax);
    }

    private BigDecimal resolveNpsEmployerRate() {
        return payrollStatutoryParameterRepository.findByParamKeyAndEffectiveToIsNull("NPS_EMPLOYER_RATE")
                .map(PayrollStatutoryParameter::getParamValue)
                .orElse(DEFAULT_NPS_EMPLOYER_RATE);
    }

    private BigDecimal round(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
