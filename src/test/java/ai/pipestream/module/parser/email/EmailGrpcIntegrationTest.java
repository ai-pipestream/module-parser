package ai.pipestream.module.parser.email;

import ai.pipestream.data.module.v1.ProcessDataRequest;
import ai.pipestream.data.module.v1.ProcessDataResponse;
import ai.pipestream.data.module.v1.PipeStepProcessorService;
import ai.pipestream.data.v1.ProcessConfiguration;
import ai.pipestream.data.module.v1.ServiceMetadata;
import ai.pipestream.data.v1.PipeDoc;
import ai.pipestream.module.parser.util.ReactiveTestDocumentLoader;
import ai.pipestream.parsed.data.email.v1.EmailMetadata;
import ai.pipestream.parsed.data.tika.v1.TikaResponse;
import com.google.protobuf.Any;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.time.Duration;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@QuarkusTest
public class EmailGrpcIntegrationTest {

    @GrpcClient
    PipeStepProcessorService parserService;

    @Test
    public void testProcessSampleEmailsViaGrpc() {
        // Skip if folder has no samples
        long available = ReactiveTestDocumentLoader
                .countTestDocuments("email")
                .await().atMost(Duration.ofSeconds(5));
        Assumptions.assumeTrue(available > 0, "No sample emails found under sample_doc_types/email");

        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .putConfigParams("extractMetadata", "true")
                .putConfigParams("maxContentLength", "2000000")
                .build();

        var results = ReactiveTestDocumentLoader.streamTestDocuments("email")
                .onItem().transformToUniAndConcatenate(doc -> processDoc(doc, config))
                .collect().asList()
                .await().atMost(Duration.ofMinutes(1));

        assertThat("Should process at least one email", results.size(), greaterThan(0));
        long successes = results.stream().filter(ProcessDataResponse::getSuccess).count();
        assertThat("Most emails should parse successfully", successes, greaterThanOrEqualTo(1L));

        boolean foundTyped = false;
        for (ProcessDataResponse resp : results) {
            if (!resp.getSuccess() || !resp.hasOutputDoc()) continue;
            PipeDoc out = resp.getOutputDoc();
            assertThat("structured_data should be present", out.getParsedMetadataMap().containsKey("tika"), is(true));
            Any any = out.getParsedMetadataMap().get("tika").getData();
            if (any.is(TikaResponse.class)) {
                try {
                    TikaResponse tr = any.unpack(TikaResponse.class);
                    if (tr.hasEmail()) {
                        EmailMetadata em = tr.getEmail();
                        if (em.hasMessageFrom() || em.getMessageToEmailCount() > 0 || em.hasInternetMessageId()) {
                            foundTyped = true;
                            break;
                        }
                    }
                } catch (com.google.protobuf.InvalidProtocolBufferException e) {
                    throw new AssertionError("Failed to unpack TikaResponse from structured_data", e);
                }
            }
        }
        assertThat("Should find at least one email with typed metadata", foundTyped, is(true));
    }

    private Uni<ProcessDataResponse> processDoc(PipeDoc doc, ProcessConfiguration config) {
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName("email-it-pipeline")
                .setPipeStepName("parser-email-it")
                .setStreamId(UUID.randomUUID().toString())
                .setCurrentHopNumber(1)
                .build();

        ProcessDataRequest request = ProcessDataRequest.newBuilder()
                .setDocument(doc)
                .setConfig(config)
                .setMetadata(metadata)
                .build();

        return parserService.processData(request);
    }
}
