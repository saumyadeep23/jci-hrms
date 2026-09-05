package in.gov.jci.hrms.exception;

public class MasterDataNotFoundException extends RuntimeException {

    /** Object, not Long - StateMaster/DistrictMaster use UUID ids, every other master entity uses Long. */
    public MasterDataNotFoundException(String entityName, Object id) {
        super(entityName + " not found with id " + id);
    }
}
