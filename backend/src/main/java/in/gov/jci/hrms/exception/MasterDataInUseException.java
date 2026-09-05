package in.gov.jci.hrms.exception;

/**
 * FR-EMP.12: blocks deletion of a master record while any active
 * (non-soft-deleted) employee still references it.
 */
public class MasterDataInUseException extends RuntimeException {

    /** Object, not Long - StateMaster/DistrictMaster use UUID ids, every other master entity uses Long. */
    public MasterDataInUseException(String entityName, Object id) {
        super(entityName + " with id " + id + " is still referenced by active employees and cannot be deleted");
    }
}
