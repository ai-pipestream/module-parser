package ai.pipestream.module.parser.comprehensive;

import ai.pipestream.data.module.v1.ProcessDataRequest;
import ai.pipestream.data.module.v1.ProcessDataResponse;
import ai.pipestream.data.v1.ProcessConfiguration;
import ai.pipestream.data.module.v1.ServiceMetadata;
import ai.pipestream.data.module.v1.PipeStepProcessorService;
import ai.pipestream.data.v1.PipeDoc;
import ai.pipestream.module.parser.util.TestDocumentLoader;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Comprehensive test for the Parser Service using reactive document streaming.
 * This test processes documents one at a time to avoid memory issues.
 */
@QuarkusTest
public class ParserServiceComprehensiveTestRefactored {
    private static final Logger LOG = Logger.getLogger(ParserServiceComprehensiveTestRefactored.class);

    @Inject
    @GrpcClient
    PipeStepProcessorService parserService;

    @Test
    public void testProcessAllAvailableDocumentsReactively() {
        LOG.info("=== Starting Reactive Parser Comprehensive Test ===");
        
        // Create progress tracker
        TestDocumentLoader.ProgressTracker tracker = 
            new TestDocumentLoader.ProgressTracker("Parser Test", 10);
        
        // Statistics collectors
        AtomicInteger docsWithBody = new AtomicInteger(0);
        AtomicInteger docsWithTitle = new AtomicInteger(0);
        AtomicInteger docsWithStructuredData = new AtomicInteger(0);
        AtomicInteger docsWithNoContent = new AtomicInteger(0);
        
        // First, count how many documents we have (use root path, not "test-documents/" prefix)
        // The test-documents JAR has resources at root level like sample_text/, sample_office_files/, etc.
        Long totalDocs = TestDocumentLoader.countTestDocuments("sample_text")
                .await().atMost(Duration.ofSeconds(5));

        LOG.infof("Found %d test documents to process", totalDocs);

        // Process configuration
        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .putConfigParams("extractMetadata", "true")
                .putConfigParams("maxContentLength", "1000000")
                .build();

        // Stream and process documents one at a time (use root-level path from JAR)
        TestDocumentLoader.streamTestDocuments("sample_text", 100) // Limit to 100 for testing
            .onItem().transformToUniAndConcatenate(testDoc -> {
                // Create request for this document
                ServiceMetadata metadata = ServiceMetadata.newBuilder()
                        .setPipelineName("comprehensive-test-pipeline")
                        .setPipeStepName("parser-comprehensive-test")
                        .setStreamId(UUID.randomUUID().toString())
                        .setCurrentHopNumber(1)
                        .build();
                
                ProcessDataRequest request = ProcessDataRequest.newBuilder()
                        .setDocument(testDoc)
                        .setConfig(config)
                        .setMetadata(metadata)
                        .build();
                
                // Process through parser and handle result
                return parserService.processData(request)
                    .onItem().invoke(response -> {
                        if (response.getSuccess() && response.hasOutputDoc()) {
                            tracker.recordSuccess();
                            
                            PipeDoc resultDoc = response.getOutputDoc();
                            
                            // Analyze what we got from parsing
                            var searchMeta = resultDoc.getSearchMetadata();
                            boolean hasBody = searchMeta.hasBody() && !searchMeta.getBody().isEmpty();
                            boolean hasTitle = searchMeta.hasTitle() && !searchMeta.getTitle().isEmpty();
                            boolean hasStructuredData = resultDoc.getParsedMetadataMap().containsKey("tika");
                            
                            // Track statistics
                            if (hasBody) docsWithBody.incrementAndGet();
                            if (hasTitle) docsWithTitle.incrementAndGet();
                            if (hasStructuredData) docsWithStructuredData.incrementAndGet();
                            
                            if (!hasBody && !hasTitle && !hasStructuredData) {
                                docsWithNoContent.incrementAndGet();
                                
                                // Log details about empty documents
                                String originalMime = testDoc.hasBlobBag() && 
                                    testDoc.getBlobBag().hasBlob() ? 
                                    testDoc.getBlobBag().getBlob().getMimeType() : "unknown";
                                    
                                LOG.debugf("Document %s has no extractable content (mime: %s)", 
                                    testDoc.getDocId().substring(0, 8), originalMime);
                            }
                        } else {
                            tracker.recordFailure();
                            LOG.warnf("Failed to process document %s: %s", 
                                testDoc.getDocId(), response.getProcessorLogsList());
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
            .await().atMost(Duration.ofMinutes(5)); // Wait up to 5 minutes for all processing
        
        // Log final statistics
        tracker.logFinal();
        LOG.info("✅ Reactive comprehensive testing complete!");
        LOG.infof("Documents with body: %d", docsWithBody.get());
        LOG.infof("Documents with title: %d", docsWithTitle.get());
        LOG.infof("Documents with structured data: %d", docsWithStructuredData.get());
        LOG.infof("Documents with no extractable content: %d", docsWithNoContent.get());
        
        // Assertions
        int processed = tracker.getProcessed();
        assertThat("Should process at least some documents", processed, is(greaterThan(0)));
        
        // All documents should be processed successfully (even if empty)
        double successRate = (double) tracker.getSuccessful() / processed;
        LOG.infof("Success rate: %.2f%%", successRate * 100);
        assertThat("High success rate expected", successRate, is(greaterThanOrEqualTo(0.95)));
        
        // Content analysis - be realistic about what parsers can extract
        if (processed > 10) { // Only make content assertions with sufficient samples
            int docsWithContent = processed - docsWithNoContent.get();
            double contentRate = (double) docsWithContent / processed;
            LOG.infof("Content extraction rate: %.2f%%", contentRate * 100);
            
            // At least 50% should have some extractable content
            assertThat("Most documents should have some extractable content", 
                    contentRate, is(greaterThanOrEqualTo(0.5)));
        }
    }
    
    @Test
    public void testProcessSpecificCategory() {
        LOG.info("=== Testing Specific Document Category ===");

        // Test just text files which should all be parseable
        TestDocumentLoader.ProgressTracker tracker =
            new TestDocumentLoader.ProgressTracker("Text Parser Test", 5);

        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .putConfigParams("extractMetadata", "true")
                .build();

        // Use root-level path from JAR (not test-documents/sample_text)
        var results = TestDocumentLoader.streamTestDocuments("sample_text")
            .onItem().transformToUniAndConcatenate(testDoc -> {
                ServiceMetadata metadata = ServiceMetadata.newBuilder()
                        .setPipelineName("category-test-pipeline")
                        .setPipeStepName("parser-text-test")
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
                            
                            // Text files should have extractable content
                            if (response.hasOutputDoc()) {
                                var searchMeta = response.getOutputDoc().getSearchMetadata();
                                boolean hasContent = searchMeta.hasBody() && 
                                    !searchMeta.getBody().isEmpty();
                                
                                if (!hasContent) {
                                    LOG.warnf("Text file had no extracted content: %s", 
                                        testDoc.getBlobBag().getBlob().getFilename());
                                }
                            }
                        } else {
                            tracker.recordFailure();
                        }
                    });
            })
            .collect().asList()
            .await().atMost(Duration.ofMinutes(2));
        
        tracker.logFinal();
        
        // Text files should have very high success rate
        if (tracker.getProcessed() > 0) {
            double successRate = (double) tracker.getSuccessful() / tracker.getProcessed();
            assertThat("Text files should parse successfully", 
                    successRate, is(greaterThanOrEqualTo(0.9)));
        }
    }
}