package in.gov.jci.hrms.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Default DocumentStorageService - writes under ${hrms.storage.local-dir}
 * (PIMS_SPEC.md Section 3.C), reproducing the same documents/{YEAR}/{MONTH}/
 * {UUID}_{cleanFilename} key structure on local disk that a real object
 * store would use, so swapping in an S3-backed implementation later is a
 * drop-in change.
 */
@Service
public class LocalStorageServiceImpl implements DocumentStorageService {

    private final Path rootDir;

    public LocalStorageServiceImpl(@Value("${hrms.storage.local-dir:./uploads}") String localDir) {
        this.rootDir = Path.of(localDir).toAbsolutePath().normalize();
    }

    @Override
    public void store(String key, byte[] content) throws IOException {
        Path target = resolve(key);
        Files.createDirectories(target.getParent());
        Files.write(target, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    /** Resolves key under rootDir and rejects any attempt to escape it, even if DocumentUploadService's own sanitization were bypassed. */
    private Path resolve(String key) {
        Path target = rootDir.resolve(key).normalize();
        if (!target.startsWith(rootDir)) {
            throw new IllegalArgumentException("Refusing to store outside the configured storage root: " + key);
        }
        return target;
    }
}
