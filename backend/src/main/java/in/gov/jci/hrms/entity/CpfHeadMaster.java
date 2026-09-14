package in.gov.jci.hrms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * CPF Head Master (Part 7 of the CPF Trust Loan &amp; Advances rule-engine spec) - maps onto
 * cpf_head_master, a table ALREADY LIVE on the shared dev database with real seed data (3 rows: HEAD_A/
 * HEAD_B/HEAD_C) - built by other tooling ahead of any Java code or Flyway migration for this feature,
 * exactly the same situation V74's own header comment describes for cpf_trust_member_ledger_entries etc.
 * This entity binds to that EXISTING shape (UUID primary key - unlike every other entity in this codebase,
 * which uses BIGSERIAL/Long - see this class's own package for why that convention was kept rather than
 * fought here) rather than inventing a competing one.
 *
 * <p>Each row corresponds 1:1 to one of {@code cpf_trust_member_ledger_entries}'s own fixed
 * running_ee/vpf/er_balance columns - the ledger schema itself is not being generalized to an arbitrary
 * N-head model; see {@code CpfWithdrawalRuleEngine}'s own javadoc for the small, honest bridge between this
 * generic "head" concept and that fixed physical schema.
 */
@Entity
@Table(name = "cpf_head_master")
public class CpfHeadMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 32)
    private String code;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "classification", nullable = false, length = 32)
    private String classification;

    @Column(name = "is_interest_bearing", nullable = false)
    private boolean interestBearing = true;

    @Column(name = "withdrawal_allowed", nullable = false)
    private boolean withdrawalAllowed = true;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected CpfHeadMaster() {
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getClassification() {
        return classification;
    }

    public boolean isInterestBearing() {
        return interestBearing;
    }

    public boolean isWithdrawalAllowed() {
        return withdrawalAllowed;
    }

    public boolean isActive() {
        return active;
    }
}
