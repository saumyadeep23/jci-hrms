package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.DocumentUploadResponse;
import in.gov.jci.hrms.entity.UploadCategory;
import in.gov.jci.hrms.exception.InvalidFilePayloadException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DocumentUploadServiceTest {

    private static final byte[] PDF_BYTES = "%PDF-1.4 fake pdf content".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] JPEG_BYTES = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01, 0x02};

    @Mock
    private DocumentStorageService storageService;

    private DocumentUploadService uploadService;

    @BeforeEach
    void setUp() {
        uploadService = new DocumentUploadService(storageService);
    }

    @Test
    void upload_withValidPdfForAppointmentOrder_storesAndReturnsKey() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "order.pdf", "application/pdf", PDF_BYTES);

        DocumentUploadResponse response = uploadService.upload(file, UploadCategory.APPOINTMENT_ORDER);

        assertThat(response.fileS3Key()).startsWith("documents/").contains("appointment_order").endsWith("order.pdf");
        assertThat(response.documentCategory()).isEqualTo(UploadCategory.APPOINTMENT_ORDER);
        assertThat(response.mimeType()).isEqualTo("application/pdf");
        assertThat(response.fileSizeBytes()).isEqualTo(PDF_BYTES.length);
        verify(storageService).store(any(), any());
    }

    @Test
    void upload_withValidJpegForPhoto_succeeds() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", JPEG_BYTES);

        DocumentUploadResponse response = uploadService.upload(file, UploadCategory.PHOTO);

        assertThat(response.documentCategory()).isEqualTo(UploadCategory.PHOTO);
        verify(storageService).store(any(), any());
    }

    @Test
    void upload_whenDisallowedMimeTypeForCategory_throws415() {
        // PAN_CARD allows pdf/jpeg/png, not plain text.
        MockMultipartFile file = new MockMultipartFile("file", "pan.txt", "text/plain", "not a pan".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> uploadService.upload(file, UploadCategory.PAN_CARD))
                .isInstanceOfSatisfying(InvalidFilePayloadException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
                    assertThat(ex.getErrorCode()).isEqualTo("INVALID_FILE_PAYLOAD");
                });
    }

    @Test
    void upload_whenOnlyPdfAllowedButExtensionIsJpg_throws415() {
        // QUALIFICATION only allows PDF - a .jpg with a legit application/pdf content-type spoof should still be rejected on extension.
        MockMultipartFile file = new MockMultipartFile("file", "cert.jpg", "application/pdf", PDF_BYTES);

        assertThatThrownBy(() -> uploadService.upload(file, UploadCategory.QUALIFICATION))
                .isInstanceOf(InvalidFilePayloadException.class);
    }

    @Test
    void upload_whenFileExceedsCategoryMaxSize_throws400WithSizeDetails() {
        byte[] oversized = new byte[600 * 1024]; // SIGNATURE cap is 500KB
        System.arraycopy(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, 0, oversized, 0, 3);
        MockMultipartFile file = new MockMultipartFile("file", "signature.jpg", "image/jpeg", oversized);

        assertThatThrownBy(() -> uploadService.upload(file, UploadCategory.SIGNATURE))
                .isInstanceOfSatisfying(InvalidFilePayloadException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMaxAllowedSizeBytes()).isEqualTo(500 * 1024L);
                    assertThat(ex.getMessage()).contains("exceeds the maximum allowed limit");
                });
    }

    @Test
    void upload_whenMagicBytesDoNotMatchDeclaredMimeType_throws415() {
        // Declares application/pdf but the content doesn't start with %PDF-.
        MockMultipartFile file = new MockMultipartFile("file", "fake.pdf", "application/pdf", "not really a pdf".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> uploadService.upload(file, UploadCategory.APPOINTMENT_ORDER))
                .isInstanceOfSatisfying(InvalidFilePayloadException.class, ex ->
                        assertThat(ex.getMessage()).contains("does not match its declared Content-Type"));
    }

    @Test
    void upload_sanitizesPathTraversalAttemptsInFilename() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "../../etc/passwd.pdf", "application/pdf", PDF_BYTES);

        DocumentUploadResponse response = uploadService.upload(file, UploadCategory.OTHER);

        assertThat(response.fileS3Key()).doesNotContain("..").doesNotContain("/etc/");
    }

    @Test
    void upload_withEmptyFile_throwsBadRequest() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> uploadService.upload(file, UploadCategory.OTHER))
                .isInstanceOfSatisfying(InvalidFilePayloadException.class, ex ->
                        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
