package io.pipeline.module.parser;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.pipeline.data.v1.Blob;
import io.pipeline.data.v1.PipeDoc;
import io.pipeline.data.v1.SearchMetadata;
import io.pipeline.data.v1.BlobBag;
import io.pipeline.data.module.*;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public abstract class ParserServiceTestBase {

    protected abstract PipeStepProcessor getParserService();

    @Test
    void testParseTextDocument() {
        String testContent = "This is a sample text document that will be parsed by Tika. It contains important information.";
        String docId = UUID.randomUUID().toString();
        
        PipeDoc inputDoc = createTestDocument(docId, testContent, "text/plain", "sample.txt");
        ModuleProcessRequest request = createProcessRequest(inputDoc);

        var response = getParserService().processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();
        
        // Verify successful processing
        assertThat("Parser should successfully process text document", response.getSuccess(), is(true));
        assertThat("Response should contain output document", response.hasOutputDoc(), is(true));
        
        PipeDoc outputDoc = response.getOutputDoc();
        assertThat("Document ID should be preserved", outputDoc.getDocId(), is(equalTo(docId)));
        assertThat("Parsed body should contain original content", 
                outputDoc.getSearchMetadata().getBody(), containsString("sample text document"));
        assertThat("Processing logs should indicate success", 
                response.getProcessorLogsList(), hasItem(containsString("successfully processed")));
    }

    @Test
    void testParseDocumentWithMetadata() {
        String testContent = "Document with metadata extraction enabled.";
        String docId = UUID.randomUUID().toString();
        
        PipeDoc inputDoc = createTestDocument(docId, testContent, "text/plain", "metadata-test.txt");
        
        // Create config with metadata extraction enabled
        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .setCustomJsonConfig(Struct.newBuilder()
                        .putFields("extractMetadata", Value.newBuilder().setBoolValue(true).build())
                        .build())
                .build();
        
        ModuleProcessRequest request = ModuleProcessRequest.newBuilder()
                .setDocument(inputDoc)
                .setMetadata(createServiceMetadata())
                .setConfig(config)
                .build();

        var response = getParserService().processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();
        
        assertThat("Parser should successfully extract metadata", response.getSuccess(), is(true));
        PipeDoc outputDoc = response.getOutputDoc();
        assertThat("Document should have structured data (metadata)", outputDoc.hasStructuredData(), is(true));
        assertThat("Processing logs should mention metadata extraction", 
                response.getProcessorLogsList(), hasItem(containsString("Extracted custom data fields")));
    }

    @Test
    void testParseEmptyDocument() {
        String docId = UUID.randomUUID().toString();
        PipeDoc inputDoc = createTestDocument(docId, "", "text/plain", "empty.txt");
        ModuleProcessRequest request = createProcessRequest(inputDoc);

        var response = getParserService().processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();
        
        assertThat("Parser should handle empty documents gracefully", response.getSuccess(), is(true));
        assertThat("Response should contain output document", response.hasOutputDoc(), is(true));
        
        PipeDoc outputDoc = response.getOutputDoc();
        assertThat("Document ID should be preserved", outputDoc.getDocId(), is(equalTo(docId)));
        // Empty document should not have body set
        assertThat("Empty document should not have body field set", 
                outputDoc.getSearchMetadata().hasBody(), is(false));
    }

    @Test
    void testParseDocumentWithoutBlob() {
        String docId = UUID.randomUUID().toString();
        
        // Create document without blob data
        PipeDoc inputDoc = PipeDoc.newBuilder()
                .setDocId(docId)
                .setSearchMetadata(SearchMetadata.newBuilder()
                        .setTitle("Document without blob")
                        .build())
                .build();
        
        ModuleProcessRequest request = createProcessRequest(inputDoc);

        var response = getParserService().processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();
        
        assertThat("Parser should handle documents without blob data", response.getSuccess(), is(true));
        assertThat("Response should contain output document", response.hasOutputDoc(), is(true));
        assertThat("Processing logs should indicate no blob data", 
                response.getProcessorLogsList(), hasItem(containsString("no blob data")));
    }

    @Test
    void testProcessRequestWithoutDocument() {
        ModuleProcessRequest request = ModuleProcessRequest.newBuilder()
                .setMetadata(createServiceMetadata())
                .setConfig(ProcessConfiguration.newBuilder().build())
                .build();

        var response = getParserService().processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();
        
        assertThat("Parser should handle requests without documents", response.getSuccess(), is(true));
        assertThat("Response should not contain output document", response.hasOutputDoc(), is(false));
        assertThat("Processing logs should indicate no document", 
                response.getProcessorLogsList(), hasItem(containsString("no document")));
    }

    @Test
    void testServiceRegistration() {
        RegistrationRequest request = RegistrationRequest.newBuilder().build();
        
        var registration = getParserService().getServiceRegistration(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();
        
        assertThat("Module name should be 'parser'", registration.getModuleName(), is(equalTo("parser")));
        assertThat("Registration should include JSON schema", registration.hasJsonConfigSchema(), is(true));
        assertThat("JSON schema should be valid", registration.getJsonConfigSchema().length(), is(greaterThan(100)));
        assertThat("Health check should pass", registration.getHealthCheckPassed(), is(true));
    }

    @Test
    void testParseMultipleDocumentTypes() {
        // Test different document types that parser should handle
        String[] testCases = {
                "Plain text content for testing",
                "<html><body><h1>HTML Document</h1><p>HTML content</p></body></html>",
                "{\"json\": \"content\", \"test\": true}"
        };
        
        String[] mimeTypes = {"text/plain", "text/html", "application/json"};
        String[] filenames = {"test.txt", "test.html", "test.json"};
        
        for (int i = 0; i < testCases.length; i++) {
            String docId = UUID.randomUUID().toString();
            PipeDoc inputDoc = createTestDocument(docId, testCases[i], mimeTypes[i], filenames[i]);
            ModuleProcessRequest request = createProcessRequest(inputDoc);

            var response = getParserService().processData(request)
                    .subscribe().withSubscriber(UniAssertSubscriber.create())
                    .awaitItem()
                    .getItem();
            
            assertThat(String.format("Parser should handle %s documents", mimeTypes[i]), 
                    response.getSuccess(), is(true));
            assertThat(String.format("Response should contain parsed %s content", mimeTypes[i]), 
                    response.hasOutputDoc(), is(true));
            
            PipeDoc outputDoc = response.getOutputDoc();
            assertThat(String.format("Parsed %s should have non-empty body", mimeTypes[i]), 
                    outputDoc.getSearchMetadata().getBody().length(), is(greaterThan(0)));
        }
    }

    // Helper methods
    private PipeDoc createTestDocument(String docId, String content, String mimeType, String filename) {
        Blob blob = Blob.newBuilder()
                .setBlobId(docId + "-blob")
                .setDriveId("test-drive")
                .setData(com.google.protobuf.ByteString.copyFromUtf8(content))
                .setMimeType(mimeType)
                .setFilename(filename)
                .setSizeBytes(content.length())
                .build();
        
        return PipeDoc.newBuilder()
                .setDocId(docId)
                .setBlobBag(BlobBag.newBuilder()
                        .setBlob(blob)
                        .build())
                .build();
    }
    
    private ServiceMetadata createServiceMetadata() {
        return ServiceMetadata.newBuilder()
                .setPipelineName("test-pipeline")
                .setPipeStepName("parser-step")
                .setStreamId(UUID.randomUUID().toString())
                .setCurrentHopNumber(1)
                .putContextParams("tenant", "test-tenant")
                .build();
    }
    
    private ModuleProcessRequest createProcessRequest(PipeDoc document) {
        return ModuleProcessRequest.newBuilder()
                .setDocument(document)
                .setMetadata(createServiceMetadata())
                .setConfig(ProcessConfiguration.newBuilder()
                        .putConfigParams("mode", "parser")
                        .build())
                .build();
    }
}