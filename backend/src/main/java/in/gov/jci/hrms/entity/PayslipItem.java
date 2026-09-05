package in.gov.jci.hrms.entity;

import in.gov.jci.hrms.audit.Auditable;
import in.gov.jci.hrms.audit.AuditableEntityListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "payslip_items")
@EntityListeners(AuditableEntityListener.class)
public class PayslipItem implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payslip_id", nullable = false)
    private Payslip payslip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "salary_head_id", nullable = false)
    private SalaryHeadMaster salaryHead;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "cause_remarks", length = 255)
    private String causeRemarks;

    protected PayslipItem() {
    }

    public PayslipItem(Payslip payslip, SalaryHeadMaster salaryHead, BigDecimal amount) {
        this.payslip = payslip;
        this.salaryHead = salaryHead;
        this.amount = amount;
    }

    public Long getId() {
        return id;
    }

    public Payslip getPayslip() {
        return payslip;
    }

    public SalaryHeadMaster getSalaryHead() {
        return salaryHead;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCauseRemarks() {
        return causeRemarks;
    }

    public void setCauseRemarks(String causeRemarks) {
        this.causeRemarks = causeRemarks;
    }

    @Override
    public String auditEntityName() {
        return "PayslipItem";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("payslipId", payslip != null ? payslip.getId() : null);
        snapshot.put("salaryHeadId", salaryHead != null ? salaryHead.getId() : null);
        snapshot.put("amount", amount);
        snapshot.put("causeRemarks", causeRemarks);
        return snapshot;
    }
}
