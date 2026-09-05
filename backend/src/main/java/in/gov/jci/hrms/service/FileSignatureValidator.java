package in.gov.jci.hrms.service;

/**
 * Magic-byte header validation - PIMS_SPEC.md Section 3.C. A client-declared
 * Content-Type header is trivial to spoof, so the declared MIME type is only
 * accepted after the file's own leading bytes confirm it.
 */
final class FileSignatureValidator {

    private static final byte[] PDF_SIGNATURE = {'%', 'P', 'D', 'F', '-'};
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_SIGNATURE =
            {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private FileSignatureValidator() {
    }

    /** True if content's leading bytes match the signature expected for declaredMimeType; unrecognized MIME types are rejected outright. */
    static boolean matchesDeclaredType(byte[] content, String declaredMimeType) {
        return switch (declaredMimeType) {
            case "application/pdf" -> startsWith(content, PDF_SIGNATURE);
            case "image/jpeg" -> startsWith(content, JPEG_SIGNATURE);
            case "image/png" -> startsWith(content, PNG_SIGNATURE);
            default -> false;
        };
    }

    private static boolean startsWith(byte[] content, byte[] signature) {
        if (content.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (content[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
