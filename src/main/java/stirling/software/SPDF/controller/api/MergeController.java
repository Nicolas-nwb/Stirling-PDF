package stirling.software.SPDF.controller.api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import stirling.software.SPDF.model.api.general.MergePdfsRequest;
import stirling.software.common.service.CustomPDFDocumentFactory;
import org.apache.pdfbox.Loader;

@RestController
@Slf4j
@RequestMapping("/api/v1/general")
@Tag(name = "General", description = "General APIs")
@RequiredArgsConstructor
public class MergeController {

    private final CustomPDFDocumentFactory pdfDocumentFactory;

    // Merges a list of PDDocument objects into a single PDDocument
    public PDDocument mergeDocuments(List<PDDocument> documents) throws IOException {
        PDDocument mergedDoc = pdfDocumentFactory.createNewDocument();
        for (PDDocument doc : documents) {
            for (PDPage page : doc.getPages()) {
                mergedDoc.addPage(page);
            }
        }
        return mergedDoc;
    }

    // Returns a comparator for sorting MultipartFile arrays based on the given sort type
    private Comparator<MultipartFile> getSortComparator(String sortType) {
        switch (sortType) {
            case "byFileName":
                return Comparator.comparing(MultipartFile::getOriginalFilename);
            case "byDateModified":
                return (file1, file2) -> {
                    try {
                        BasicFileAttributes attr1 =
                                Files.readAttributes(
                                        Paths.get(file1.getOriginalFilename()),
                                        BasicFileAttributes.class);
                        BasicFileAttributes attr2 =
                                Files.readAttributes(
                                        Paths.get(file2.getOriginalFilename()),
                                        BasicFileAttributes.class);
                        return attr1.lastModifiedTime().compareTo(attr2.lastModifiedTime());
                    } catch (IOException e) {
                        return 0; // If there's an error, treat them as equal
                    }
                };
            case "byDateCreated":
                return (file1, file2) -> {
                    try {
                        BasicFileAttributes attr1 =
                                Files.readAttributes(
                                        Paths.get(file1.getOriginalFilename()),
                                        BasicFileAttributes.class);
                        BasicFileAttributes attr2 =
                                Files.readAttributes(
                                        Paths.get(file2.getOriginalFilename()),
                                        BasicFileAttributes.class);
                        return attr1.creationTime().compareTo(attr2.creationTime());
                    } catch (IOException e) {
                        return 0; // If there's an error, treat them as equal
                    }
                };
            case "byPDFTitle":
                return (file1, file2) -> {
                    try (PDDocument doc1 = pdfDocumentFactory.load(file1);
                            PDDocument doc2 = pdfDocumentFactory.load(file2)) {
                        String title1 = doc1.getDocumentInformation().getTitle();
                        String title2 = doc2.getDocumentInformation().getTitle();
                        return title1.compareTo(title2);
                    } catch (IOException e) {
                        return 0;
                    }
                };
            case "orderProvided":
            default:
                return (file1, file2) -> 0; // Default is the order provided
        }
    }

    @PostMapping(consumes = "multipart/form-data", value = "/merge-pdfs")
    public ResponseEntity<byte[]> mergePdfs(@ModelAttribute MergePdfsRequest request)
            throws IOException {
        log.info("=== MANUAL MERGE CONTROLLER CALLED ===");
        List<Path> tempFiles = new ArrayList<>();
        List<PDDocument> sourceDocs = new ArrayList<>();
        PDDocument resultDoc = null;
        try {
            // traiter les fichiers uploadés
            MultipartFile[] files = request.getFileInput();
            if (files != null) {
                for (MultipartFile mf : files) {
                    Path tmp = Files.createTempFile("merge_", ".pdf");
                    tempFiles.add(tmp);
                    mf.transferTo(tmp.toFile());
                    try (PDDocument doc = Loader.loadPDF(tmp.toFile())) {
                        if (doc.getNumberOfPages() == 0) {
                            return errorResponse(
                                    "File " + mf.getOriginalFilename() + " contains no pages",
                                    HttpStatus.BAD_REQUEST);
                        }
                    }
                    sourceDocs.add(Loader.loadPDF(tmp.toFile()));
                }
            }
            // traiter les URLs
            String[] urls = request.getUrlInputs();
            if (urls != null) {
                for (String url : urls) {
                    Path tmp = Files.createTempFile("url_", ".pdf");
                    tempFiles.add(tmp);
                    downloadFileFromURL(url, tmp);
                    try (PDDocument doc = Loader.loadPDF(tmp.toFile())) {
                        if (doc.getNumberOfPages() == 0) {
                            return errorResponse(
                                    "Downloaded PDF has no pages from URL: " + url,
                                    HttpStatus.BAD_REQUEST);
                        }
                    }
                    sourceDocs.add(Loader.loadPDF(tmp.toFile()));
                }
            }
            if (sourceDocs.isEmpty()) {
                return errorResponse(
                        "No valid PDF sources found to merge.", HttpStatus.BAD_REQUEST);
            }
            // fusion manuelle
            resultDoc = mergeDocuments(sourceDocs);
            // suppression des signatures si demandé
            if (Boolean.TRUE.equals(request.getRemoveCertSign())) {
                PDAcroForm form = resultDoc.getDocumentCatalog().getAcroForm();
                if (form != null) {
                    form.getFields().removeIf(f -> f instanceof PDSignatureField);
                    if (form.getFields().isEmpty())
                        resultDoc.getDocumentCatalog().setAcroForm(null);
                }
            }
            // construction de la réponse
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                resultDoc.save(baos);
                byte[] data = baos.toByteArray();
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_PDF);
                headers.setContentDispositionFormData("attachment", "merged.pdf");
                return new ResponseEntity<>(data, headers, HttpStatus.OK);
            }
        } catch (IllegalArgumentException ex) {
            return errorResponse(ex.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (IOException ex) {
            return errorResponse("I/O error: " + ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        } finally {
            // nettoyage
            for (PDDocument d : sourceDocs)
                try {
                    d.close();
                } catch (IOException ignored) {
                }
            if (resultDoc != null)
                try {
                    resultDoc.close();
                } catch (IOException ignored) {
                }
            for (Path p : tempFiles)
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            log.info("=== MERGE CONTROLLER CLEANUP COMPLETED ===");
        }
    }

    /**
     * Aide à générer une réponse d'erreur en texte brut.
     */
    private ResponseEntity<byte[]> errorResponse(String msg, HttpStatus status) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.TEXT_PLAIN);
        return new ResponseEntity<>(msg.getBytes(StandardCharsets.UTF_8), h, status);
    }

    /**
     * Downloads a file from URL to a specific path
     */
    private void downloadFileFromURL(String urlStr, Path targetPath) throws IOException {
        URL url = URI.create(urlStr).toURL();
        try (InputStream in = url.openStream();
                java.io.OutputStream out = Files.newOutputStream(targetPath)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
    }
}
