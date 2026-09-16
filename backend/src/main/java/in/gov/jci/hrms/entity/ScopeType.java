package in.gov.jci.hrms.entity;

/**
 * Canonical data scopes (RBAC_SECURITY_REQUIREMENTS.md). OFFICE resolves against
 * Employee.regionalOffice.id (scope_value). REGION has no backing organizational entity in this
 * schema (RegionalOffice is the only org-unit tier - see RbacSecurity javadoc) and is modeled but
 * not resolvable - any REGION-scoped assignment denies rather than silently widening access. HO
 * resolves to employees whose own regionalOffice.officeType = HEAD_OFFICE. ALL_JCI has no
 * restriction.
 */
public enum ScopeType {
    SELF,
    OFFICE,
    REGION,
    HO,
    ALL_JCI
}
