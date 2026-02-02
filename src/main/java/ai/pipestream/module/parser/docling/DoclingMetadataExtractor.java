package ai.pipestream.module.parser.docling;

import ai.docling.serve.api.DoclingServeApi;
import ai.docling.serve.api.convert.request.ConvertDocumentRequest;
import ai.docling.serve.api.convert.request.options.*;
import ai.docling.serve.api.convert.request.source.FileSource;
import ai.docling.serve.api.convert.response.ConvertDocumentResponse;
import ai.docling.serve.api.convert.response.DocumentResponse;
import ai.pipestream.module.parser.config.DoclingOptions;
import ai.pipestream.parsed.data.docling.v1.DoclingDocument;
import ai.pipestream.parsed.data.docling.v1.DoclingParseMetadata;
import ai.pipestream.parsed.data.docling.v1.DoclingParseStatus;
import ai.pipestream.parsed.data.docling.v1.DoclingResponse;
import com.google.protobuf.Timestamp;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Extracts comprehensive metadata from documents using the Docling service.
 *
 * This class:
 * 1. Calls DoclingServeApi directly with full ConvertDocumentOptions
 * 2. Maps ConvertDocumentResponse → DoclingResponse proto
 * 3. Converts DoclingDocument Java POJO → DoclingDocument proto using proper field-by-field mapping
 * 4. Handles errors gracefully and provides status information
 *
 * NO JSON - uses DoclingDocumentMapper for proper gRPC strong typing.
 */
@ApplicationScoped
public class DoclingMetadataExtractor {

    private static final Logger LOG = Logger.getLogger(DoclingMetadataExtractor.class);

    @Inject
    DoclingServeApi doclingServeApi;

    /**
     * Extracts comprehensive metadata from document bytes using Docling.
     *
     * @param content The document content as byte array
     * @param filename The filename (used for format detection)
     * @param docId The document ID
     * @param options Docling configuration options (if null, uses defaults)
     * @return Complete DoclingResponse with parsed document structure
     */
    public DoclingResponse extractComprehensiveMetadata(
            byte[] content,
            String filename,
            String docId,
            DoclingOptions options) {

        if (options == null) {
            options = DoclingOptions.defaultOptions();
        }

        LOG.debugf("Extracting Docling metadata for document %s with filename %s, OCR engine: %s",
                  docId, filename, options.ocrEngine());

        long startTime = System.currentTimeMillis();
        DoclingResponse.Builder responseBuilder = DoclingResponse.newBuilder();

        // Set document ID
        if (docId != null && !docId.isEmpty()) {
            responseBuilder.setDocId(docId);
        }

        try {
            // Build complete ConvertDocumentOptions from DoclingOptions (1:1 mapping)
            ConvertDocumentOptions convertOptions = buildConvertDocumentOptions(options);

            // Build FileSource from base64-encoded content
            String base64Content = Base64.getEncoder().encodeToString(content);
            FileSource source = FileSource.builder()
                    .filename(filename)
                    .base64String(base64Content)
                    .build();

            // Build conversion request
            ConvertDocumentRequest request = ConvertDocumentRequest.builder()
                    .source(source)
                    .options(convertOptions)
                    .build();

            // Call Docling service directly with full options
            ConvertDocumentResponse doclingResponse = doclingServeApi.convertSource(request);

            // Map ConvertDocumentResponse to DoclingResponse proto
            mapDoclingResponse(doclingResponse, responseBuilder, docId, startTime);

        } catch (Exception e) {
            LOG.errorf(e, "Failed to extract Docling metadata for document %s", docId);

            // Build error response
            long processingTime = System.currentTimeMillis() - startTime;

            DoclingParseStatus.Builder statusBuilder = DoclingParseStatus.newBuilder()
                .setStatus(DoclingParseStatus.Status.STATUS_FAILED)
                .setParseTimeMs(processingTime)
                .addErrors(String.format("Docling parsing failed: %s", e.getMessage()));

            responseBuilder.setStatus(statusBuilder.build());
        }

        // Set parsed_at timestamp
        Timestamp now = Timestamp.newBuilder()
            .setSeconds(System.currentTimeMillis() / 1000)
            .setNanos((int) ((System.currentTimeMillis() % 1000) * 1000000))
            .build();
        responseBuilder.setParsedAt(now);

        return responseBuilder.build();
    }

    /**
     * Builds ConvertDocumentOptions from our DoclingOptions config.
     * This is a 1:1 mapping of all 28 fields.
     */
    private ConvertDocumentOptions buildConvertDocumentOptions(DoclingOptions options) {
        ConvertDocumentOptions.Builder builder = ConvertDocumentOptions.builder();

        // Input/Output Formats
        if (options.fromFormats() != null && !options.fromFormats().isEmpty()) {
            options.fromFormats().forEach(format ->
                builder.fromFormat(parseInputFormat(format)));
        }
        if (options.toFormats() != null && !options.toFormats().isEmpty()) {
            options.toFormats().forEach(format ->
                builder.toFormat(parseOutputFormat(format)));
        }
        if (options.imageExportMode() != null) {
            builder.imageExportMode(parseImageRefMode(options.imageExportMode()));
        }

        // OCR Configuration
        if (options.doOcr() != null) {
            builder.doOcr(options.doOcr());
        }
        if (options.forceOcr() != null) {
            builder.forceOcr(options.forceOcr());
        }
        if (options.ocrEngine() != null) {
            builder.ocrEngine(parseOcrEngine(options.ocrEngine()));
        }
        if (options.ocrLang() != null && !options.ocrLang().isEmpty()) {
            options.ocrLang().forEach(builder::ocrLang);
        }

        // PDF Processing
        if (options.pdfBackend() != null) {
            builder.pdfBackend(parsePdfBackend(options.pdfBackend()));
        }

        // Table Extraction
        if (options.tableMode() != null) {
            builder.tableMode(parseTableFormerMode(options.tableMode()));
        }
        if (options.tableCellMatching() != null) {
            builder.tableCellMatching(options.tableCellMatching());
        }
        if (options.doTableStructure() != null) {
            builder.doTableStructure(options.doTableStructure());
        }

        // Processing Control
        if (options.pipeline() != null) {
            builder.pipeline(parseProcessingPipeline(options.pipeline()));
        }
        if (options.pageRange() != null && !options.pageRange().isEmpty()) {
            options.pageRange().forEach(builder::pageRange);
        }
        if (options.documentTimeout() != null) {
            builder.documentTimeout(Duration.ofSeconds(options.documentTimeout()));
        }
        if (options.abortOnError() != null) {
            builder.abortOnError(options.abortOnError());
        }

        // Image Processing
        if (options.includeImages() != null) {
            builder.includeImages(options.includeImages());
        }
        if (options.imagesScale() != null) {
            builder.imagesScale(options.imagesScale());
        }

        // Output Formatting
        if (options.mdPageBreakPlaceholder() != null) {
            builder.mdPageBreakPlaceholder(options.mdPageBreakPlaceholder());
        }

        // Content Enrichment
        if (options.doCodeEnrichment() != null) {
            builder.doCodeEnrichment(options.doCodeEnrichment());
        }
        if (options.doFormulaEnrichment() != null) {
            builder.doFormulaEnrichment(options.doFormulaEnrichment());
        }

        // Picture AI Features
        if (options.doPictureClassification() != null) {
            builder.doPictureClassification(options.doPictureClassification());
        }
        if (options.doPictureDescription() != null) {
            builder.doPictureDescription(options.doPictureDescription());
        }
        if (options.pictureDescriptionAreaThreshold() != null) {
            builder.pictureDescriptionAreaThreshold(options.pictureDescriptionAreaThreshold());
        }
        // Note: pictureDescriptionLocal, pictureDescriptionApi, vlm fields are complex objects
        // For now, we'll skip them until we need them (they require parsing JSON strings)

        return builder.build();
    }

    // ==================== Enum Parsers ====================

    private InputFormat parseInputFormat(String format) {
        try {
            return InputFormat.valueOf(format.toUpperCase());
        } catch (IllegalArgumentException e) {
            LOG.warnf("Unknown input format: %s, skipping", format);
            return null;
        }
    }

    private OutputFormat parseOutputFormat(String format) {
        try {
            return OutputFormat.valueOf(format.toUpperCase());
        } catch (IllegalArgumentException e) {
            LOG.warnf("Unknown output format: %s, defaulting to JSON", format);
            return OutputFormat.JSON;
        }
    }

    private ImageRefMode parseImageRefMode(String mode) {
        try {
            return ImageRefMode.valueOf(mode.toUpperCase());
        } catch (IllegalArgumentException e) {
            LOG.warnf("Unknown image ref mode: %s, defaulting to EMBEDDED", mode);
            return ImageRefMode.EMBEDDED;
        }
    }

    private OcrEngine parseOcrEngine(String engine) {
        return switch (engine.toLowerCase()) {
            case "easyocr" -> OcrEngine.EASYOCR;
            case "mac", "ocrmac" -> OcrEngine.OCRMAC;
            case "rapidocr" -> OcrEngine.RAPIDOCR;
            case "tesserocr" -> OcrEngine.TESSEROCR;
            case "tesseract" -> OcrEngine.TESSERACT;
            default -> {
                LOG.warnf("Unknown OCR engine: %s, defaulting to EASYOCR", engine);
                yield OcrEngine.EASYOCR;
            }
        };
    }

    private PdfBackend parsePdfBackend(String backend) {
        try {
            return PdfBackend.valueOf(backend.toUpperCase());
        } catch (IllegalArgumentException e) {
            LOG.warnf("Unknown PDF backend: %s, defaulting to DLPARSE_V4", backend);
            return PdfBackend.DLPARSE_V4;
        }
    }

    private TableFormerMode parseTableFormerMode(String mode) {
        try {
            return TableFormerMode.valueOf(mode.toUpperCase());
        } catch (IllegalArgumentException e) {
            LOG.warnf("Unknown table mode: %s, defaulting to ACCURATE", mode);
            return TableFormerMode.ACCURATE;
        }
    }

    private ProcessingPipeline parseProcessingPipeline(String pipeline) {
        try {
            return ProcessingPipeline.valueOf(pipeline.toUpperCase());
        } catch (IllegalArgumentException e) {
            LOG.warnf("Unknown processing pipeline: %s", pipeline);
            return null;
        }
    }

    // ==================== Response Mapping ====================

    /**
     * Maps ConvertDocumentResponse from Docling Java API to DoclingResponse proto.
     */
    private void mapDoclingResponse(
            ConvertDocumentResponse doclingResponse,
            DoclingResponse.Builder responseBuilder,
            String docId,
            long startTime) throws Exception {

        DocumentResponse document = doclingResponse.getDocument();

        // Convert DoclingDocument Java POJO to proto using proper field-by-field mapping
        if (document != null && document.getJsonContent() != null) {
            ai.docling.core.DoclingDocument javaDoclingDoc = document.getJsonContent();

            // Map Java object to Proto object - NO JSON, proper field mapping
            DoclingDocument protoDoc = DoclingDocumentMapper.map(javaDoclingDoc);

            responseBuilder.setDocument(protoDoc);
        }

        // Map markdown, html, text exports
        if (document != null) {
            if (document.getMarkdownContent() != null && !document.getMarkdownContent().isEmpty()) {
                responseBuilder.setMarkdown(document.getMarkdownContent());
            }
            if (document.getHtmlContent() != null && !document.getHtmlContent().isEmpty()) {
                responseBuilder.setHtml(document.getHtmlContent());
            }
            if (document.getTextContent() != null && !document.getTextContent().isEmpty()) {
                responseBuilder.setText(document.getTextContent());
            }
        }

        // Map status
        DoclingParseStatus.Builder statusBuilder = DoclingParseStatus.newBuilder();

        String status = doclingResponse.getStatus();
        if (status != null) {
            switch (status.toLowerCase()) {
                case "success":
                    statusBuilder.setStatus(DoclingParseStatus.Status.STATUS_SUCCESS);
                    break;
                case "partial_success":
                    statusBuilder.setStatus(DoclingParseStatus.Status.STATUS_PARTIAL);
                    break;
                case "failure":
                    statusBuilder.setStatus(DoclingParseStatus.Status.STATUS_FAILED);
                    break;
                case "timeout":
                    statusBuilder.setStatus(DoclingParseStatus.Status.STATUS_TIMEOUT);
                    break;
                default:
                    statusBuilder.setStatus(DoclingParseStatus.Status.STATUS_UNSPECIFIED);
                    LOG.warnf("Unknown Docling status: %s", status);
            }
        } else {
            statusBuilder.setStatus(DoclingParseStatus.Status.STATUS_UNSPECIFIED);
        }

        // Map processing time
        long processingTime = System.currentTimeMillis() - startTime;
        statusBuilder.setParseTimeMs(processingTime);

        // Map errors
        if (doclingResponse.getErrors() != null && !doclingResponse.getErrors().isEmpty()) {
            doclingResponse.getErrors().forEach(error -> {
                String errorMsg = String.format("%s: %s",
                    error.getComponentType() != null ? error.getComponentType() : "Unknown",
                    error.getErrorMessage() != null ? error.getErrorMessage() : "No message");
                statusBuilder.addErrors(errorMsg);
            });
        }

        responseBuilder.setStatus(statusBuilder.build());
    }
}
