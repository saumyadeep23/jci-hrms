package in.gov.jci.hrms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Statutory Heads catalog (JCI Payroll Engine) - the fixed 15-head list of statutory
 * deductions/contributions, mapping the pre-existing payroll_statutory_heads table (see V66's own
 * comment). stat_head_count is the table's own primary key - payroll_monthly_statutory_items already
 * FKs to it directly, so it is never reassigned once a head is seeded. Description and short name are
 * editable via PUT /api/v1/payroll/masters/statutory-heads/{statHeadCount} - see StatutoryHeadUpdateRequest.
 */
@Entity
@Table(name = "payroll_statutory_heads")
public class StatutoryHead {

    @Id
    @Column(name = "stat_head_count")
    private Integer statHeadCount;

    @Column(name = "stat_head_descr", nullable = false, length = 150)
    private String statHeadDescr;

    @Column(name = "stat_head_short_name", nullable = false, length = 50)
    private String statHeadShortName;

    protected StatutoryHead() {
    }

    public Integer getStatHeadCount() {
        return statHeadCount;
    }

    public String getStatHeadDescr() {
        return statHeadDescr;
    }

    public void setStatHeadDescr(String statHeadDescr) {
        this.statHeadDescr = statHeadDescr;
    }

    public String getStatHeadShortName() {
        return statHeadShortName;
    }

    public void setStatHeadShortName(String statHeadShortName) {
        this.statHeadShortName = statHeadShortName;
    }
}
