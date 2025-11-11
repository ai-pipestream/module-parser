package ai.pipestream.module.parser.comprehensive;

import ai.pipestream.data.module.*;
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Test to analyze what Tika/Parser actually extracts from different file types.
 * This helps us understand that Tika almost always produces SOMETHING - 
 * even if it's just metadata without body content.
 */
@QuarkusTest
public class ParserMetadataAnalysisTest {
    private static final Logger LOG = Logger.getLogger(ParserMetadataAnalysisTest.class);

    @Inject
    @GrpcClient
    PipeStepProcessor parserService;

    @Test
    public void analyzeParserExtractionPatterns() {
        LOG.info("=== Analyzing Parser/Tika Extraction Patterns ===");
        
        // Categories of extraction
        AtomicInteger totalDocs = new AtomicInteger(0);
        AtomicInteger parserFailures = new AtomicInteger(0);
        AtomicInteger metadataOnly = new AtomicInteger(0);
        AtomicInteger titleOnly = new AtomicInteger(0);
        AtomicInteger bodyOnly = new AtomicInteger(0);
        AtomicInteger titleAndBody = new AtomicInteger(0);
        AtomicInteger hasStructuredData = new AtomicInteger(0);
        AtomicInteger hasCustomFields = new AtomicInteger(0);
        
        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .putConfigParams("extractMetadata", "true")
                .build();
        
        ReactiveTestDocumentLoader.streamTestDocuments("test-documents", 200) // Process all available
            .onItem().transformToUniAndConcatenate(testDoc -> {
                ServiceMetadata metadata = ServiceMetadata.newBuilder()
                        .setPipelineName("metadata-analysis")
                        .setPipeStepName("parser-analysis")
                        .setStreamId(UUID.randomUUID().toString())
                        .build();
                
                ModuleProcessRequest request = ModuleProcessRequest.newBuilder()
                        .setDocument(testDoc)
                        .setConfig(config)
                        .setMetadata(metadata)
                        .build();
                
                return parserService.processData(request)
                    .onItem().invoke(response -> {
                        totalDocs.incrementAndGet();
                        
                        if (!response.getSuccess() || !response.hasOutputDoc()) {
                            parserFailures.incrementAndGet();
                            LOG.warnf("Parser failed for: %s", 
                                testDoc.hasBlobBag() && testDoc.getBlobBag().hasBlob() ? 
                                testDoc.getBlobBag().getBlob().getFilename() : "unknown");
                            return;
                        }
                        
                        PipeDoc outputDoc = response.getOutputDoc();
                        var searchMeta = outputDoc.getSearchMetadata();
                        
                        boolean hasTitle = searchMeta.hasTitle() && !searchMeta.getTitle().isEmpty();
                        boolean hasBody = searchMeta.hasBody() && !searchMeta.getBody().isEmpty();
                        boolean hasCustom = searchMeta.hasCustomFields() && 
                                          searchMeta.getCustomFields().getFieldsCount() > 0;
                        boolean hasStructured = outputDoc.hasStructuredData();
                        
                        // Categorize the extraction
                        if (!hasTitle && !hasBody) {
                            metadataOnly.incrementAndGet();
                            
                            // Log what metadata we DID get
                            String filename = testDoc.hasBlobBag() && testDoc.getBlobBag().hasBlob() ? 
                                testDoc.getBlobBag().getBlob().getFilename() : "unknown";
                            String mimeType = testDoc.hasBlobBag() && testDoc.getBlobBag().hasBlob() ? 
                                testDoc.getBlobBag().getBlob().getMimeType() : "unknown";
                                
                            LOG.debugf("Metadata-only extraction for %s (mime: %s):", filename, mimeType);
                            
                            if (searchMeta.hasSourceMimeType()) {
                                LOG.debugf("  - Detected MIME: %s", searchMeta.getSourceMimeType());
                            }
                            if (searchMeta.hasContentLength()) {
                                LOG.debugf("  - Content length: %d", searchMeta.getContentLength());
                            }
                            if (searchMeta.hasLanguage()) {
                                LOG.debugf("  - Language: %s", searchMeta.getLanguage());
                            }
                            if (searchMeta.hasAuthor()) {
                                LOG.debugf("  - Author: %s", searchMeta.getAuthor());
                            }
                            if (searchMeta.hasCreationDate()) {
                                LOG.debugf("  - Creation date present");
                            }
                            if (hasCustom) {
                                LOG.debugf("  - Custom fields: %d", 
                                    searchMeta.getCustomFields().getFieldsCount());
                            }
                        } else if (hasTitle && hasBody) {
                            titleAndBody.incrementAndGet();
                        } else if (hasTitle) {
                            titleOnly.incrementAndGet();
                        } else {
                            bodyOnly.incrementAndGet();
                        }
                        
                        if (hasCustom) hasCustomFields.incrementAndGet();
                        if (hasStructured) hasStructuredData.incrementAndGet();
                    })
                    .onFailure().invoke(error -> {
                        parserFailures.incrementAndGet();
                        LOG.errorf(error, "Error processing document");
                    })
                    .onFailure().recoverWithItem(ModuleProcessResponse.newBuilder()
                            .setSuccess(false)
                            .build());
            })
            .collect().asList()
            .await().atMost(Duration.ofMinutes(5));
        
        // Report analysis
        LOG.info("\n=== Parser/Tika Extraction Analysis ===");
        LOG.infof("Total documents processed: %d", totalDocs.get());
        LOG.info("\nExtraction Categories:");
        LOG.infof("  Parser failures: %d (%.1f%%)", 
                parserFailures.get(), 
                (parserFailures.get() * 100.0) / totalDocs.get());
        LOG.infof("  Metadata only (no title/body): %d (%.1f%%)", 
                metadataOnly.get(), 
                (metadataOnly.get() * 100.0) / totalDocs.get());
        LOG.infof("  Title only: %d (%.1f%%)", 
                titleOnly.get(), 
                (titleOnly.get() * 100.0) / totalDocs.get());
        LOG.infof("  Body only: %d (%.1f%%)", 
                bodyOnly.get(), 
                (bodyOnly.get() * 100.0) / totalDocs.get());
        LOG.infof("  Title AND body: %d (%.1f%%)", 
                titleAndBody.get(), 
                (titleAndBody.get() * 100.0) / totalDocs.get());
        
        LOG.info("\nAdditional Data:");
        LOG.infof("  Documents with custom fields: %d (%.1f%%)", 
                hasCustomFields.get(), 
                (hasCustomFields.get() * 100.0) / totalDocs.get());
        LOG.infof("  Documents with structured data: %d (%.1f%%)", 
                hasStructuredData.get(), 
                (hasStructuredData.get() * 100.0) / totalDocs.get());
        
        // Key insight assertion
        int docsWithSomething = totalDocs.get() - parserFailures.get();
        LOG.info("\n🔍 KEY INSIGHT:");
        LOG.infof("  Tika extracted SOMETHING from %d/%d documents (%.1f%%)", 
                docsWithSomething, totalDocs.get(), 
                (docsWithSomething * 100.0) / totalDocs.get());
        LOG.info("  This includes metadata-only extractions for binary files like images, videos, etc.");
        
        // Assertions
        assertThat("Parser should rarely fail completely", 
                parserFailures.get(), is(lessThanOrEqualTo(5)));
        assertThat("Most documents should have some extraction", 
                docsWithSomething, is(greaterThan(totalDocs.get() * 9 / 10))); // >90%
        assertThat("Almost all successful parses should have structured data (Tika metadata)", 
                hasStructuredData.get(), is(greaterThanOrEqualTo(docsWithSomething - 5))); // Allow a few without
    }
    
    @Test  
    public void analyzeSpecificFileTypeExtractions() {
        LOG.info("=== Analyzing Specific File Type Extractions ===");
        
        // Test different categories to understand extraction patterns
        analyzeCategory("sample_image", "Image files");
        analyzeCategory("sample_video", "Video files");
        analyzeCategory("sample_audio", "Audio files");
        analyzeCategory("sample_office_files", "Office documents");
        analyzeCategory("sample_text", "Text files");
        analyzeCategory("sample_source_code", "Source code");
        analyzeCategory("sample_archive_files", "Archive files");
    }
    
    private void analyzeCategory(String category, String description) {
        LOG.infof("\nAnalyzing %s:", description);
        
        AtomicInteger total = new AtomicInteger(0);
        AtomicInteger withContent = new AtomicInteger(0);
        AtomicInteger metadataOnly = new AtomicInteger(0);
        
        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .putConfigParams("extractMetadata", "true")
                .build();
        
        ReactiveTestDocumentLoader.streamTestDocumentsByCategory(category)
            .onItem().transformToUniAndConcatenate(testDoc -> {
                ServiceMetadata metadata = ServiceMetadata.newBuilder()
                        .setPipelineName("category-analysis")
                        .setPipeStepName("parser-" + category)
                        .setStreamId(UUID.randomUUID().toString())
                        .build();
                
                ModuleProcessRequest request = ModuleProcessRequest.newBuilder()
                        .setDocument(testDoc)
                        .setConfig(config)
                        .setMetadata(metadata)
                        .build();
                
                return parserService.processData(request)
                    .onItem().invoke(response -> {
                        total.incrementAndGet();
                        
                        if (response.getSuccess() && response.hasOutputDoc()) {
                            var searchMeta = response.getOutputDoc().getSearchMetadata();
                            boolean hasContent = (searchMeta.hasBody() && !searchMeta.getBody().isEmpty()) ||
                                               (searchMeta.hasTitle() && !searchMeta.getTitle().isEmpty());
                            
                            if (hasContent) {
                                withContent.incrementAndGet();
                            } else {
                                metadataOnly.incrementAndGet();
                                
                                // Show what metadata we got
                                String filename = testDoc.hasBlobBag() && testDoc.getBlobBag().hasBlob() ? 
                                    testDoc.getBlobBag().getBlob().getFilename() : "unknown";
                                int customFieldCount = searchMeta.hasCustomFields() ? 
                                    searchMeta.getCustomFields().getFieldsCount() : 0;
                                LOG.debugf("  %s: metadata-only with %d custom fields", 
                                    filename, customFieldCount);
                            }
                        }
                    })
                    .onFailure().recoverWithItem(ModuleProcessResponse.newBuilder()
                            .setSuccess(false)
                            .build());
            })
            .collect().asList()
            .await().atMost(Duration.ofMinutes(2));
        
        LOG.infof("  - Total files: %d", total.get());
        LOG.infof("  - With text content: %d (%.1f%%)", 
                withContent.get(), 
                total.get() > 0 ? (withContent.get() * 100.0) / total.get() : 0);
        LOG.infof("  - Metadata only: %d (%.1f%%)", 
                metadataOnly.get(),
                total.get() > 0 ? (metadataOnly.get() * 100.0) / total.get() : 0);
    }
}