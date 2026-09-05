package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.DocumentUploadResponse;
import in.gov.jci.hrms.entity.UploadCategory;
import in.gov.jci.hrms.exception.InvalidFilePayloadException;
import in.gov.jci.hrms.security.SecurityConfig;
import in.gov.jci.hrms.service.DocumentUploadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentUploadController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "EMPLOYEE")
class DocumentUploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentUploadService documentUploadService;

    @Test
    void upload_withValidFile_returns201WithKeyAndMetadata() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "order.pdf", "application/pdf", "content".getBytes());
        when(documentUploadService.upload(any(), eq(UploadCategory.APPOINTMENT_ORDER))).thenReturn(
                new DocumentUploadResponse("documents/2026/09/uuid_appointment_order_order.pdf",
                        UploadCategory.APPOINTMENT_ORDER, "order.pdf", "application/pdf", 7L, Instant.now()));

        mockMvc.perform(multipart("/api/v1/documents/upload")
                        .file(file)
                        .param("category", "APPOINTMENT_ORDER"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentCategory").value("APPOINTMENT_ORDER"))
                .andExpect(jsonPath("$.fileS3Key").value("documents/2026/09/uuid_appointment_order_order.pdf"));
    }

    @Test
    void upload_whenRejectedByValidation_returnsStandardizedErrorPayload() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "pan.txt", "text/plain", "x".getBytes());
        when(documentUploadService.upload(any(), eq(UploadCategory.PAN_CARD))).thenThrow(
                new InvalidFilePayloadException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "INVALID_FILE_PAYLOAD",
                        "Content-Type 'text/plain' is not permitted for category PAN_CARD", "file",
                        Set.of("application/pdf", "image/jpeg", "image/png"), null));

        mockMvc.perform(multipart("/api/v1/documents/upload")
                        .file(file)
                        .param("category", "PAN_CARD"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.errorCode").value("INVALID_FILE_PAYLOAD"))
                .andExpect(jsonPath("$.field").value("file"))
                .andExpect(jsonPath("$.allowedMimeTypes").isArray());
    }

    @Test
    @WithAnonymousUser
    void upload_withoutAuthentication_returns401() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "order.pdf", "application/pdf", "content".getBytes());

        mockMvc.perform(multipart("/api/v1/documents/upload")
                        .file(file)
                        .param("category", "APPOINTMENT_ORDER"))
                .andExpect(status().isUnauthorized());
    }
}
