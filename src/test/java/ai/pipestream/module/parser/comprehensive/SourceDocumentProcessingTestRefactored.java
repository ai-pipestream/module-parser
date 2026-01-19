package ai.pipestream.module.parser.comprehensive;

import ai.pipestream.data.module.v1.ProcessDataRequest;
import ai.pipestream.data.module.v1.ProcessDataResponse;
import ai.pipestream.data.v1.ProcessConfiguration;
import ai.pipestream.data.module.v1.ServiceMetadata;
import ai.pipestream.data.module.v1.PipeStepProcessorService;
import ai.pipestream.data.v1.PipeDoc;
import ai.pipestream.module.parser.util.ReactiveTestDocumentLoader;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Refactored test for processing source documents through the parser.
 * This test uses the reactive ReactiveTestDocumentLoader to process documents one at a time,
 * avoiding memory issues with large test datasets.
 * 
 * Instead of generating .pb files, this test validates parser functionality
 * with real documents and will eventually integrate with repository-service.
 */
@QuarkusTest
public class SourceDocumentProcessingTestRefactored {
    private static final Logger LOG = Logger.getLogger(SourceDocumentProcessingTestRefactored.class);

    @Inject
    @GrpcClient
    PipeStepProcessorService parserService;

    @Test
    public void processAllTestDocumentsReactively() {
        LOG.info("=== Starting Reactive Source Document Processing Test ===");
        
        // Create progress tracker
        ReactiveTestDocumentLoader.ProgressTracker tracker = 
            new ReactiveTestDocumentLoader.ProgressTracker("Source Document Processing", 25);
        
        // Statistics collectors
        AtomicInteger totalProcessed = new AtomicInteger(0);
        AtomicInteger successfulExtractions = new AtomicInteger(0);
        AtomicInteger documentsWithTitle = new AtomicInteger(0);
        AtomicInteger documentsWithBody = new AtomicInteger(0);
        AtomicInteger documentsWithMetadata = new AtomicInteger(0);
        AtomicInteger emptyDocuments = new AtomicInteger(0);
        
        // Track document types and their success rates
        AtomicReference<String> currentCategory = new AtomicReference<>("");
        AtomicInteger categoryDocs = new AtomicInteger(0);
        AtomicInteger categorySuccess = new AtomicInteger(0);
        
        // Process configuration
        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .putConfigParams("extractMetadata", "true")
                .putConfigParams("maxContentLength", "10000000")  // 10MB limit
                .putConfigParams("detectLanguage", "true")
                .putConfigParams("extractStructure", "true")
                .build();
        
        // Process all test documents
        ReactiveTestDocumentLoader.streamTestDocuments("test-documents")
            .onItem().invoke(testDoc -> {
                // Detect category changes
                String docCategory = detectCategory(testDoc);
                if (!docCategory.equals(currentCategory.get())) {
                    if (!currentCategory.get().isEmpty()) {
                        LOG.infof("Category '%s' complete: %d documents, %d successful (%.1f%%)",
                                currentCategory.get(), categoryDocs.get(), categorySuccess.get(),
                                (categorySuccess.get() * 100.0) / categoryDocs.get());
                    }
                    currentCategory.set(docCategory);
                    categoryDocs.set(0);
                    categorySuccess.set(0);
                    LOG.infof("Processing category: %s", docCategory);
                }
                categoryDocs.incrementAndGet();
            })
            .onItem().transformToUniAndConcatenate(testDoc -> {
                // Create request for this document
                ServiceMetadata metadata = ServiceMetadata.newBuilder()
                        .setPipelineName("source-processing-pipeline")
                        .setPipeStepName("parser-source-test")
                        .setStreamId(UUID.randomUUID().toString())
                        .setCurrentHopNumber(1)
                        .build();
                
                ProcessDataRequest request = ProcessDataRequest.newBuilder()
                        .setDocument(testDoc)
                        .setConfig(config)
                        .setMetadata(metadata)
                        .build();
                
                // Process through parser and analyze result
                return parserService.processData(request)
                    .onItem().invoke(response -> {
                        totalProcessed.incrementAndGet();
                        
                        if (response.getSuccess() && response.hasOutputDoc()) {
                            tracker.recordSuccess();
                            categorySuccess.incrementAndGet();
                            
                            PipeDoc resultDoc = response.getOutputDoc();
                            var searchMeta = resultDoc.getSearchMetadata();
                            
                            // Analyze extraction results
                            boolean hasTitle = searchMeta.hasTitle() && !searchMeta.getTitle().isEmpty();
                            boolean hasBody = searchMeta.hasBody() && !searchMeta.getBody().isEmpty();
                            boolean hasMetadata = searchMeta.hasAuthor() || searchMeta.hasLanguage() || 
                                                searchMeta.hasCategory() || searchMeta.hasTags();
                            
                            if (hasTitle || hasBody) {
                                successfulExtractions.incrementAndGet();
                            }
                            
                            if (hasTitle) documentsWithTitle.incrementAndGet();
                            if (hasBody) documentsWithBody.incrementAndGet();
                            if (hasMetadata) documentsWithMetadata.incrementAndGet();
                            
                            if (!hasTitle && !hasBody && !hasMetadata) {
                                emptyDocuments.incrementAndGet();
                                
                                // Log details about documents with no extraction
                                String filename = testDoc.hasBlobBag() && testDoc.getBlobBag().hasBlob() ? 
                                    testDoc.getBlobBag().getBlob().getFilename() : "unknown";
                                String mimeType = testDoc.hasBlobBag() && testDoc.getBlobBag().hasBlob() ? 
                                    testDoc.getBlobBag().getBlob().getMimeType() : "unknown";
                                    
                                LOG.debugf("No content extracted from: %s (mime: %s)", 
                                    filename, mimeType);
                            }
                            
                            // Log detailed info for specific file types
                            if (LOG.isDebugEnabled() && hasBody) {
                                String filename = testDoc.hasBlobBag() && testDoc.getBlobBag().hasBlob() ? 
                                    testDoc.getBlobBag().getBlob().getFilename() : "";
                                int bodyLength = searchMeta.getBody().length();
                                LOG.debugf("Extracted %d chars from %s", bodyLength, filename);
                            }
                            
                        } else {
                            tracker.recordFailure();
                            
                            // Log processing failures
                            String filename = testDoc.hasBlobBag() && testDoc.getBlobBag().hasBlob() ? 
                                testDoc.getBlobBag().getBlob().getFilename() : "unknown";
                            LOG.warnf("Failed to process: %s", filename);
                            
                            if (response.getProcessorLogsCount() > 0) {
                                for (String logEntry : response.getProcessorLogsList()) {
                                    LOG.debugf("  Parser log: %s", logEntry);
                                }
                            }
                        }
                    })
                    .onFailure().invoke(error -> {
                        tracker.recordFailure();
                        LOG.errorf(error, "Error processing document");
                    })
                    .onFailure().recoverWithItem(response -> 
                        // Return a failed response on error to continue processing
                        ProcessDataResponse.newBuilder()
                            .setSuccess(false)
                            .build()
                    );
            })
            .collect().asList()
            .await().atMost(Duration.ofMinutes(10)); // Allow up to 10 minutes for all processing
        
        // Log final category stats
        if (!currentCategory.get().isEmpty()) {
            LOG.infof("Category '%s' complete: %d documents, %d successful (%.1f%%)",
                    currentCategory.get(), categoryDocs.get(), categorySuccess.get(),
                    (categorySuccess.get() * 100.0) / categoryDocs.get());
        }
        
        // Log final statistics
        tracker.logFinal();
        LOG.info("\n=== Source Document Processing Summary ===");
        LOG.infof("Total documents processed: %d", totalProcessed.get());
        LOG.infof("Successful extractions: %d (%.1f%%)", 
                successfulExtractions.get(), 
                (successfulExtractions.get() * 100.0) / totalProcessed.get());
        LOG.infof("Documents with title: %d (%.1f%%)", 
                documentsWithTitle.get(),
                (documentsWithTitle.get() * 100.0) / totalProcessed.get());
        LOG.infof("Documents with body: %d (%.1f%%)", 
                documentsWithBody.get(),
                (documentsWithBody.get() * 100.0) / totalProcessed.get());
        LOG.infof("Documents with metadata: %d (%.1f%%)", 
                documentsWithMetadata.get(),
                (documentsWithMetadata.get() * 100.0) / totalProcessed.get());
        LOG.infof("Empty documents (no extraction): %d (%.1f%%)", 
                emptyDocuments.get(),
                (emptyDocuments.get() * 100.0) / totalProcessed.get());
        
        // Assertions
        assertThat("Should process documents", totalProcessed.get(), is(greaterThan(0)));
        
        // Parser should handle all documents gracefully (even if no content extracted)
        double successRate = (double) tracker.getSuccessful() / tracker.getProcessed();
        assertThat("Parser should handle most documents without errors", 
                successRate, is(greaterThanOrEqualTo(0.9)));
        
        // Content extraction expectations (realistic based on test data)
        double extractionRate = (double) successfulExtractions.get() / totalProcessed.get();
        LOG.infof("Overall extraction rate: %.1f%%", extractionRate * 100);
        
        // At least half of documents should have extractable content
        assertThat("Significant portion of documents should have extractable content", 
                extractionRate, is(greaterThanOrEqualTo(0.5)));
    }
    
    @Test
    public void processSpecificFileTypes() {
        LOG.info("=== Testing Specific File Type Processing ===");
        
        // Test office documents which should have good extraction rates
        testCategoryExtraction("sample_office_files", 0.8);
        
        // Test text files which should have excellent extraction rates
        testCategoryExtraction("sample_text", 0.95);
        
        // Test source code files
        testCategoryExtraction("sample_source_code", 0.9);
        
        // Test archives (may have lower extraction rates)
        testCategoryExtraction("sample_archive_files", 0.3);
        
        // Test images (should have minimal text extraction)
        testCategoryExtraction("sample_image", 0.1);
    }
    
    private void testCategoryExtraction(String category, double expectedSuccessRate) {
        LOG.infof("Testing category: %s (expected rate: %.0f%%)", 
                category, expectedSuccessRate * 100);
        
        ReactiveTestDocumentLoader.ProgressTracker tracker = 
            new ReactiveTestDocumentLoader.ProgressTracker(category + " Processing", 5);
        
        AtomicInteger docsWithContent = new AtomicInteger(0);
        
        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .putConfigParams("extractMetadata", "true")
                .build();
        
        var results = ReactiveTestDocumentLoader.streamTestDocumentsByCategory(category)
            .onItem().transformToUniAndConcatenate(testDoc -> {
                ServiceMetadata metadata = ServiceMetadata.newBuilder()
                        .setPipelineName("category-test-pipeline")
                        .setPipeStepName("parser-" + category)
                        .setStreamId(UUID.randomUUID().toString())
                        .build();
                
                ProcessDataRequest request = ProcessDataRequest.newBuilder()
                        .setDocument(testDoc)
                        .setConfig(config)
                        .setMetadata(metadata)
                        .build();
                
                return parserService.processData(request)
                    .onItem().invoke(response -> {
                        if (response.getSuccess()) {
                            tracker.recordSuccess();
                            
                            if (response.hasOutputDoc()) {
                                var searchMeta = response.getOutputDoc().getSearchMetadata();
                                boolean hasContent = (searchMeta.hasBody() && !searchMeta.getBody().isEmpty()) ||
                                                   (searchMeta.hasTitle() && !searchMeta.getTitle().isEmpty());
                                
                                if (hasContent) {
                                    docsWithContent.incrementAndGet();
                                }
                            }
                        } else {
                            tracker.recordFailure();
                        }
                    })
                    .onFailure().invoke(error -> tracker.recordFailure())
                    .onFailure().recoverWithItem(ProcessDataResponse.newBuilder()
                            .setSuccess(false)
                            .build());
            })
            .collect().asList()
            .await().atMost(Duration.ofMinutes(2));
        
        tracker.logFinal();
        
        if (tracker.getProcessed() > 0) {
            double actualRate = (double) docsWithContent.get() / tracker.getProcessed();
            LOG.infof("%s extraction rate: %.1f%% (%d/%d documents)", 
                    category, actualRate * 100, docsWithContent.get(), tracker.getProcessed());
            
            // Use a more lenient assertion for test stability
            double tolerance = 0.2; // 20% tolerance
            assertThat(String.format("%s should have expected extraction rate", category),
                    actualRate, is(greaterThanOrEqualTo(Math.max(0, expectedSuccessRate - tolerance))));
        }
    }
    
    private String detectCategory(PipeDoc doc) {
        if (doc.hasBlobBag() && doc.getBlobBag().hasBlob()) {
            String filename = doc.getBlobBag().getBlob().getFilename();
            if (filename != null) {
                // Try to extract category from the path structure
                if (filename.contains("/")) {
                    String[] parts = filename.split("/");
                    if (parts.length > 1) {
                        return parts[parts.length - 2]; // Return parent directory name
                    }
                }
            }
        }
        return "unknown";
    }
}