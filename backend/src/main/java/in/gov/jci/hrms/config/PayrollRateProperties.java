package in.gov.jci.hrms.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * HRA percentages and the Transport Allowance base amount are JCI-specific
 * wage-settlement figures, not public statutory rates (unlike EPF/EPS,
 * which PayrollComputationService hardcodes as real law). Nothing in the
 * SRS excerpt this payroll engine was built against gave these numbers, so
 * the defaults below are placeholders only - a commonly-seen government/PSU
 * HRA pattern (X/Y/Z tiers) and a commonly-referenced flat TA base, NOT
 * confirmed JCI policy. Override via payroll.rates.* in application.yml
 * once the real wage-revision circular figures are known; no code change
 * needed to do so.
 */
@Component
@ConfigurationProperties(prefix = "payroll.rates")
public class PayrollRateProperties {

    private BigDecimal hraPercentX = new BigDecimal("24.00");
    private BigDecimal hraPercentY = new BigDecimal("16.00");
    private BigDecimal hraPercentZ = new BigDecimal("8.00");
    private BigDecimal transportAllowanceBase = new BigDecimal("1600.00");

    public BigDecimal getHraPercentX() {
        return hraPercentX;
    }

    public void setHraPercentX(BigDecimal hraPercentX) {
        this.hraPercentX = hraPercentX;
    }

    public BigDecimal getHraPercentY() {
        return hraPercentY;
    }

    public void setHraPercentY(BigDecimal hraPercentY) {
        this.hraPercentY = hraPercentY;
    }

    public BigDecimal getHraPercentZ() {
        return hraPercentZ;
    }

    public void setHraPercentZ(BigDecimal hraPercentZ) {
        this.hraPercentZ = hraPercentZ;
    }

    public BigDecimal getTransportAllowanceBase() {
        return transportAllowanceBase;
    }

    public void setTransportAllowanceBase(BigDecimal transportAllowanceBase) {
        this.transportAllowanceBase = transportAllowanceBase;
    }
}
