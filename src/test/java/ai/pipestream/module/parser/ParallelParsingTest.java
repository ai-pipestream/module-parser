package ai.pipestream.module.parser;

import ai.pipestream.data.module.v1.ProcessDataRequest;
import ai.pipestream.data.module.v1.ProcessDataResponse;
import ai.pipestream.data.v1.*;
import ai.pipestream.module.parser.config.DoclingOptions;
import ai.pipestream.module.parser.config.ParserConfig;
import ai.pipestream.module.parser.docling.DoclingMetadataExtractor;
import ai.pipestream.module.parser.util.DocumentParser;
import ai.pipestream.parsed.data.docling.v1.DoclingResponse;
import com.google.protobuf.ByteString;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;

@QuarkusTest
public class ParallelParsingTest {

    @Inject
    ParserServiceImpl parserService;

    @InjectMock
    DocumentParser documentParser;

    @InjectMock
    DoclingMetadataExtractor doclingMetadataExtractor;

    @Test
    public void testParallelExecution() throws Exception {
        // Mock Tika parser with delay
        Mockito.when(documentParser.parseDocument(any(ByteString.class), any(ParserConfig.class), any()))
                .thenAnswer(invocation -> {
                    Thread.sleep(500); // Simulate 500ms Tika work
                    return PipeDoc.newBuilder()
                            .setDocId("test-doc")
                            .setSearchMetadata(SearchMetadata.newBuilder().setTitle("Mock Tika").setBody("Body").build())
                            .build();
                });

        // Mock Docling extractor with delay
        Mockito.when(doclingMetadataExtractor.extractComprehensiveMetadata(any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    Thread.sleep(500); // Simulate 500ms Docling work
                    return DoclingResponse.newBuilder().build();
                });

        // Create request with both enabled
        String docId = UUID.randomUUID().toString();
        Blob blob = Blob.newBuilder()
                .setData(ByteString.copyFromUtf8("test data"))
                .setMimeType("application/pdf")
                .setFilename("test.pdf")
                .build();

        PipeDoc inputDoc = PipeDoc.newBuilder()
                .setDocId(docId)
                .setBlobBag(BlobBag.newBuilder().setBlob(blob).build())
                .build();

        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .setJsonConfig(Struct.newBuilder()
                        .putFields("enableTika", Value.newBuilder().setBoolValue(true).build())
                        .putFields("enableDocling", Value.newBuilder().setBoolValue(true).build())
                        .build())
                .build();

        ProcessDataRequest request = ProcessDataRequest.newBuilder()
                .setDocument(inputDoc)
                .setConfig(config)
                .build();

        // Run timing test
        long start = System.currentTimeMillis();
        ProcessDataResponse response = parserService.processData(request).await().indefinitely();
        long duration = System.currentTimeMillis() - start;

        Assertions.assertTrue(response.getSuccess(), "Parsing should succeed");
        
        // Assert concurrency
        // If sequential: 500 + 500 = 1000ms
        // If parallel: max(500, 500) = 500ms
        // Allow some overhead (e.g., < 900ms proves overlap)
        System.out.println("Total duration: " + duration + "ms");
        Assertions.assertTrue(duration < 900, "Execution took " + duration + "ms, expected parallel execution (< 900ms)");
        Assertions.assertTrue(duration >= 500, "Execution took too little time (" + duration + "ms), expected at least 500ms");
    }
}
