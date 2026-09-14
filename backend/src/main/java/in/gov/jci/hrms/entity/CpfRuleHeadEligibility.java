package in.gov.jci.hrms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * One (rule detail, head) eligibility + debit/re-credit priority row - binds to the already-live
 * cpf_rule_head_eligibility (21 seeded rows, unique(detail_id, head_id)). Parts 8-10 of the spec:
 * CpfWithdrawalRuleEngine reads every is_eligible=true row for a rule ordered by debit_priority to allocate
 * a withdrawal across heads, and (for repaymentCreditMethod=ORIGINAL_DEBIT_HEAD) recredit_priority to
 * re-credit a recovery - never a hardcoded "if (head == VPF)" branch in application code.
 */
@Entity
@Table(name = "cpf_rule_head_eligibility")
public class CpfRuleHeadEligibility {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "detail_id", nullable = false)
    private CpfWithdrawalRuleDetail detail;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "head_id", nullable = false)
    private CpfHeadMaster head;

    @Column(name = "is_eligible", nullable = false)
    private boolean eligible = true;

    @Column(name = "debit_priority", nullable = false)
    private int debitPriority;

    @Column(name = "recredit_priority", nullable = false)
    private int recreditPriority;

    protected CpfRuleHeadEligibility() {
    }

    public CpfRuleHeadEligibility(CpfWithdrawalRuleDetail detail, CpfHeadMaster head, boolean eligible, int debitPriority, int recreditPriority) {
        this.detail = detail;
        this.head = head;
        this.eligible = eligible;
        this.debitPriority = debitPriority;
        this.recreditPriority = recreditPriority;
    }

    public UUID getId() {
        return id;
    }

    public CpfWithdrawalRuleDetail getDetail() {
        return detail;
    }

    public CpfHeadMaster getHead() {
        return head;
    }

    public boolean isEligible() {
        return eligible;
    }

    public int getDebitPriority() {
        return debitPriority;
    }

    public int getRecreditPriority() {
        return recreditPriority;
    }
}
