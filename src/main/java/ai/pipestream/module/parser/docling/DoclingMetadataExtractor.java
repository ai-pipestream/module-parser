package ai.pipestream.module.parser.docling;

import ai.docling.serve.api.convert.request.options.OutputFormat;
import ai.docling.serve.api.convert.response.ConvertDocumentResponse;
import ai.docling.serve.api.convert.response.DocumentResponse;
import ai.pipestream.parsed.data.docling.v1.DoclingDocument;
import ai.pipestream.parsed.data.docling.v1.DoclingParseMetadata;
import ai.pipestream.parsed.data.docling.v1.DoclingParseStatus;
import ai.pipestream.parsed.data.docling.v1.DoclingResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.JsonFormat;
import io.quarkiverse.docling.runtime.client.DoclingService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Extracts comprehensive metadata from documents using the Docling service.
 *
 * This class:
 * 1. Calls DoclingService to convert documents
 * 2. Maps ConvertDocumentResponse → DoclingResponse proto
 * 3. Converts DoclingDocument Java POJO → DoclingDocument proto via JSON
 * 4. Handles errors gracefully and provides status information
 *
 * The DoclingDocument proto is a 1:1 mapping with ai.docling.core.DoclingDocument Java class.
 */
@ApplicationScoped
public class DoclingMetadataExtractor {

    private static final Logger LOG = Logger.getLogger(DoclingMetadataExtractor.class);

    @Inject
    DoclingService doclingService;

    @Inject
    ObjectMapper objectMapper;

    /**
     * Extracts comprehensive metadata from document bytes using Docling.
     *
     * @param content The document content as byte array
     * @param filename The filename (used for format detection)
     * @param docId The document ID
     * @return Complete DoclingResponse with parsed document structure
     */
    public DoclingResponse extractComprehensiveMetadata(
            byte[] content,
            String filename,
            String docId) {

        LOG.debugf("Extracting Docling metadata for document %s with filename %s", docId, filename);

        long startTime = System.currentTimeMillis();
        DoclingResponse.Builder responseBuilder = DoclingResponse.newBuilder();

        // Set document ID
        if (docId != null && !docId.isEmpty()) {
            responseBuilder.setDocId(docId);
        }

        try {
            // Call Docling service to convert document
            // Request all output formats to populate response fields
            ConvertDocumentResponse doclingResponse = doclingService.convertFromBytes(
                content,
                filename,
                OutputFormat.JSON
            );

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
     * Maps ConvertDocumentResponse from Docling Java API to DoclingResponse proto.
     */
    private void mapDoclingResponse(
            ConvertDocumentResponse doclingResponse,
            DoclingResponse.Builder responseBuilder,
            String docId,
            long startTime) throws Exception {

        DocumentResponse document = doclingResponse.getDocument();

        // Convert DoclingDocument Java POJO to proto via JSON
        if (document != null && document.getJsonContent() != null) {
            ai.docling.core.DoclingDocument javaDoclingDoc = document.getJsonContent();

            // Serialize Java DoclingDocument to JSON
            String json = objectMapper.writeValueAsString(javaDoclingDoc);
            LOG.tracef("Docling JSON for document %s: %s", docId, json);

            // Deserialize JSON to proto DoclingDocument
            DoclingDocument.Builder docBuilder = DoclingDocument.newBuilder();
            JsonFormat.parser()
                .ignoringUnknownFields()
                .merge(json, docBuilder);
            DoclingDocument protoDoc = docBuilder.build();

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
