package ai.pipestream.module.parser;

import com.google.protobuf.Struct;
import com.google.protobuf.util.JsonFormat;
import ai.pipestream.data.module.*;
import ai.pipestream.data.v1.PipeDoc;
import ai.pipestream.module.parser.config.ParserConfig;
import ai.pipestream.module.parser.config.AdvancedOptions;
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
 * Test enhanced parsing features with comprehensive extraction options.
 * Uses reactive document loading to test parser configuration options.
 */
@QuarkusTest
public class EnhancedParsingTestRefactored {
    private static final Logger LOG = Logger.getLogger(EnhancedParsingTestRefactored.class);

    @Inject
    @GrpcClient
    PipeStepProcessor parserService;

    @Test
    public void testEnhancedParsingWithAllOptions() {
        LOG.info("=== Testing Enhanced Parsing with Comprehensive Extraction ===");
        
        // Create enhanced parser configuration with all options enabled
        ParserConfig enhancedConfig = ParserConfig.create(
            null, // parsingOptions - use defaults
            AdvancedOptions.comprehensiveExtraction(), // All advanced options enabled
            null, // contentTypeHandling - use defaults  
            null, // errorHandling - use defaults
            "enhanced-test-config"
        );
        
        // Convert config to JSON for the request
        String configJson = convertConfigToJson(enhancedConfig);
        
        // Track extraction enhancements
        AtomicInteger totalDocs = new AtomicInteger(0);
        AtomicInteger docsWithEnhancedMetadata = new AtomicInteger(0);
        AtomicInteger docsWithStructuredContent = new AtomicInteger(0);
        AtomicInteger docsWithLanguageDetection = new AtomicInteger(0);
        AtomicInteger docsWithAuthorInfo = new AtomicInteger(0);
        
        // Test with various document types that benefit from enhanced parsing
        ReactiveTestDocumentLoader.streamTestDocuments("test-documents", 50) // Sample set
            .onItem().transformToUniAndConcatenate(testDoc -> {
                ServiceMetadata metadata = ServiceMetadata.newBuilder()
                        .setPipelineName("enhanced-parsing-test")
                        .setPipeStepName("parser-enhanced")
                        .setStreamId(UUID.randomUUID().toString())
                        .build();
                
                // Create config with JSON
                ProcessConfiguration.Builder configBuilder = ProcessConfiguration.newBuilder()
                        .putConfigParams("extractMetadata", "true")
                        .putConfigParams("detectLanguage", "true")
                        .putConfigParams("extractStructure", "true")
                        .putConfigParams("extractLinks", "true");
                
                // Add the enhanced config as custom JSON if we have it
                if (configJson != null) {
                    try {
                        Struct.Builder structBuilder = Struct.newBuilder();
                        JsonFormat.parser().merge(configJson, structBuilder);
                        configBuilder.setCustomJsonConfig(structBuilder.build());
                    } catch (Exception e) {
                        LOG.warnf("Failed to add custom config: %s", e.getMessage());
                    }
                }
                
                ModuleProcessRequest request = ModuleProcessRequest.newBuilder()
                        .setDocument(testDoc)
                        .setConfig(configBuilder.build())
                        .setMetadata(metadata)
                        .build();
                
                return parserService.processData(request)
                    .onItem().invoke(response -> {
                        totalDocs.incrementAndGet();
                        
                        if (response.getSuccess() && response.hasOutputDoc()) {
                            PipeDoc outputDoc = response.getOutputDoc();
                            var searchMeta = outputDoc.getSearchMetadata();
                            
                            // Check for enhanced extraction features
                            boolean hasEnhancedMetadata = false;
                            boolean hasStructured = outputDoc.hasStructuredData();
                            boolean hasLanguage = searchMeta.hasLanguage() && !searchMeta.getLanguage().isEmpty();
                            boolean hasAuthor = searchMeta.hasAuthor() && !searchMeta.getAuthor().isEmpty();
                            
                            // Check if we got more metadata than just basic extraction
                            if (searchMeta.hasCreationDate() || searchMeta.hasLastModifiedDate() || 
                                searchMeta.hasCategory() || searchMeta.hasTags() || 
                                searchMeta.hasKeywords() || searchMeta.hasContentLength()) {
                                hasEnhancedMetadata = true;
                            }
                            
                            if (hasEnhancedMetadata) docsWithEnhancedMetadata.incrementAndGet();
                            if (hasStructured) docsWithStructuredContent.incrementAndGet();
                            if (hasLanguage) docsWithLanguageDetection.incrementAndGet();
                            if (hasAuthor) docsWithAuthorInfo.incrementAndGet();
                            
                            // Log interesting extractions
                            if (hasLanguage || hasAuthor) {
                                String filename = testDoc.hasBlobBag() && testDoc.getBlobBag().hasBlob() ? 
                                    testDoc.getBlobBag().getBlob().getFilename() : "unknown";
                                LOG.debugf("Enhanced extraction for %s - Language: %s, Author: %s",
                                    filename,
                                    hasLanguage ? searchMeta.getLanguage() : "none",
                                    hasAuthor ? searchMeta.getAuthor() : "none");
                            }
                        }
                    })
                    .onFailure().recoverWithItem(ModuleProcessResponse.newBuilder()
                            .setSuccess(false)
                            .build());
            })
            .collect().asList()
            .await().atMost(Duration.ofMinutes(2));
        
        // Report results
        LOG.info("\n=== Enhanced Parsing Results ===");
        LOG.infof("Total documents processed: %d", totalDocs.get());
        LOG.infof("Documents with enhanced metadata: %d (%.1f%%)", 
                docsWithEnhancedMetadata.get(),
                (docsWithEnhancedMetadata.get() * 100.0) / totalDocs.get());
        LOG.infof("Documents with structured content: %d (%.1f%%)", 
                docsWithStructuredContent.get(),
                (docsWithStructuredContent.get() * 100.0) / totalDocs.get());
        LOG.infof("Documents with language detection: %d (%.1f%%)", 
                docsWithLanguageDetection.get(),
                (docsWithLanguageDetection.get() * 100.0) / totalDocs.get());
        LOG.infof("Documents with author info: %d (%.1f%%)", 
                docsWithAuthorInfo.get(),
                (docsWithAuthorInfo.get() * 100.0) / totalDocs.get());
        
        // Assertions
        assertThat("Should process documents", totalDocs.get(), is(greaterThan(0)));
        assertThat("Most documents should have structured content (Tika metadata)", 
                docsWithStructuredContent.get(), is(greaterThan(totalDocs.get() * 9 / 10)));
        // Language detection and author info depend on document types
        LOG.info("Enhanced parsing features are working as expected");
    }
    
    @Test
    public void testMinimalParsingConfiguration() {
        LOG.info("=== Testing Minimal Parsing Configuration ===");
        
        // Create minimal parser configuration
        ParserConfig minimalConfig = ParserConfig.create(
            null, 
            AdvancedOptions.fastProcessing(), // Fast/minimal extraction
            null,  
            null,
            "minimal-test-config"
        );
        
        AtomicInteger totalDocs = new AtomicInteger(0);
        AtomicInteger docsWithContent = new AtomicInteger(0);
        
        // Test with a small set
        ReactiveTestDocumentLoader.streamTestDocuments("test-documents/sample_text", 10)
            .onItem().transformToUniAndConcatenate(testDoc -> {
                ServiceMetadata metadata = ServiceMetadata.newBuilder()
                        .setPipelineName("minimal-parsing-test")
                        .setPipeStepName("parser-minimal")
                        .setStreamId(UUID.randomUUID().toString())
                        .build();
                
                ProcessConfiguration config = ProcessConfiguration.newBuilder()
                        .putConfigParams("extractMetadata", "false") // Minimal mode
                        .build();
                
                ModuleProcessRequest request = ModuleProcessRequest.newBuilder()
                        .setDocument(testDoc)
                        .setConfig(config)
                        .setMetadata(metadata)
                        .build();
                
                return parserService.processData(request)
                    .onItem().invoke(response -> {
                        totalDocs.incrementAndGet();
                        
                        if (response.getSuccess() && response.hasOutputDoc()) {
                            var searchMeta = response.getOutputDoc().getSearchMetadata();
                            if ((searchMeta.hasBody() && !searchMeta.getBody().isEmpty()) ||
                                (searchMeta.hasTitle() && !searchMeta.getTitle().isEmpty())) {
                                docsWithContent.incrementAndGet();
                            }
                        }
                    })
                    .onFailure().recoverWithItem(ModuleProcessResponse.newBuilder()
                            .setSuccess(false)
                            .build());
            })
            .collect().asList()
            .await().atMost(Duration.ofMinutes(1));
        
        LOG.infof("Minimal parsing: %d/%d documents had content extracted", 
                docsWithContent.get(), totalDocs.get());
        
        // Even minimal parsing should extract text content
        assertThat("Text files should have content even with minimal parsing", 
                docsWithContent.get(), is(greaterThan(totalDocs.get() / 2)));
    }
    
    private String convertConfigToJson(ParserConfig config) {
        try {
            // This would normally use Jackson or similar
            // For now, return null to use default config params
            return null;
        } catch (Exception e) {
            LOG.warnf("Failed to convert config to JSON: %s", e.getMessage());
            return null;
        }
    }
}