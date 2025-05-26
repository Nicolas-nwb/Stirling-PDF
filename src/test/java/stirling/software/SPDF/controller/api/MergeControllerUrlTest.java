package stirling.software.SPDF.controller.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import stirling.software.SPDF.model.api.general.MergePdfsRequest;
import stirling.software.SPDF.service.CustomPDFDocumentFactory;
import stirling.software.SPDF.service.PdfMetadataService;
import stirling.software.SPDF.utils.GeneralUtils;

@ExtendWith(MockitoExtension.class)
class MergeControllerUrlTest {

    @TempDir Path tempDir;

    private MergeController mergeController;

    @BeforeEach
    void setUp() {
        PdfMetadataService metadataService = mock(PdfMetadataService.class);
        CustomPDFDocumentFactory pdfFactory = new CustomPDFDocumentFactory(metadataService);
        mergeController = new MergeController(pdfFactory);
    }

    @Test
    void mergePdfFromUrlsDeletesTempFiles() throws Exception {
        File pdf1 = tempDir.resolve("one.pdf").toFile();
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.save(pdf1);
        }
        File pdf2 = tempDir.resolve("two.pdf").toFile();
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.save(pdf2);
        }

        MergePdfsRequest request = new MergePdfsRequest();
        request.setUrlInputs(new String[] {"https://example.com/one.pdf", "https://example.com/two.pdf"});
        request.setRemoveCertSign(false);

        try (MockedStatic<GeneralUtils> utilities = mockStatic(GeneralUtils.class)) {
            utilities.when(() -> GeneralUtils.isValidURL(anyString())).thenReturn(true);
            utilities.when(() -> GeneralUtils.isURLReachable(anyString())).thenReturn(true);
            utilities.when(() -> GeneralUtils.downloadFileFromURL("https://example.com/one.pdf"))
                    .thenReturn(pdf1);
            utilities.when(() -> GeneralUtils.downloadFileFromURL("https://example.com/two.pdf"))
                    .thenReturn(pdf2);

            ResponseEntity<byte[]> response = mergeController.mergePdfs(request);

            assertEquals(200, response.getStatusCode().value());
            assertEquals("application/pdf", response.getHeaders().getContentType().toString());
            assertNotNull(response.getBody());

            try (PDDocument merged = PDDocument.load(response.getBody())) {
                assertEquals(2, merged.getNumberOfPages());
            }
        }

        assertFalse(Files.exists(pdf1.toPath()), "First temp file should be deleted");
        assertFalse(Files.exists(pdf2.toPath()), "Second temp file should be deleted");
    }
}
