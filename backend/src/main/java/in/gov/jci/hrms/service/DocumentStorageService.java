package in.gov.jci.hrms.service;

import java.io.IOException;

/**
 * Pluggable binary storage for uploaded documents - PIMS_SPEC.md Section 3.C.
 * DocumentUploadService owns key generation/validation; implementations
 * just persist bytes at the given key and make them retrievable by it.
 * LocalStorageServiceImpl is the only implementation today; an S3-backed
 * one can be swapped in later without touching DocumentUploadService.
 */
public interface DocumentStorageService {

    void store(String key, byte[] content) throws IOException;
}
