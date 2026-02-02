package ai.pipestream.module.parser.creativecommons;

import com.google.protobuf.Any;
import ai.pipestream.data.module.v1.ProcessDataRequest;
import ai.pipestream.data.module.v1.ProcessDataResponse;
import ai.pipestream.data.module.v1.PipeStepProcessorService;
import ai.pipestream.data.v1.ProcessConfiguration;
import ai.pipestream.data.module.v1.ServiceMetadata;
import ai.pipestream.data.v1.PipeDoc;
import ai.pipestream.module.parser.util.ReactiveTestDocumentLoader;
import ai.pipestream.parsed.data.creative_commons.v1.CreativeCommonsMetadata;
import ai.pipestream.parsed.data.tika.v1.TikaResponse;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@QuarkusTest
public class CreativeCommonsGrpcIntegrationTest {

    @GrpcClient
    PipeStepProcessorService parserService;

    @Test
    public void testProcessCreativeCommonsSamplesViaGrpc() {
        long available = ReactiveTestDocumentLoader
                .countTestDocuments("creative_commons")
                .await().atMost(Duration.ofSeconds(5));
        Assumptions.assumeTrue(available > 0, "No CC samples found under sample_doc_types/creative_commons");

        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .putConfigParams("extractMetadata", "true")
                .putConfigParams("maxContentLength", "5000000")
                .build();

        var results = ReactiveTestDocumentLoader.streamTestDocuments("creative_commons")
                .onItem().transformToUniAndConcatenate(doc -> processDoc(doc, config))
                .collect().asList()
                .await().atMost(Duration.ofMinutes(2));

        assertThat("Should process at least one file", results.size(), greaterThan(0));
        boolean foundCC = false;
        for (ProcessDataResponse resp : results) {
            if (!resp.getSuccess() || !resp.hasOutputDoc()) continue;
            PipeDoc out = resp.getOutputDoc();
            assertThat("structured_data should be present", out.getParsedMetadataMap().containsKey("tika"), is(true));
            Any any = out.getParsedMetadataMap().get("tika").getData();
            if (!any.is(TikaResponse.class)) continue;
            try {
                TikaResponse tr = any.unpack(TikaResponse.class);
                if (tr.hasCreativeCommons()) {
                    CreativeCommonsMetadata cc = tr.getCreativeCommons();
                    if (cc.hasWebStatement() || cc.getRightsOwnersCount() > 0 || cc.hasUsageTerms() || cc.hasRightsCertificate() || cc.hasRightsMarked()) {
                        foundCC = true;
                        break;
                    }
                }
            } catch (com.google.protobuf.InvalidProtocolBufferException e) {
                throw new AssertionError("Failed to unpack TikaResponse from structured_data", e);
            }
        }
        assertThat("Should find at least one document with Creative Commons metadata", foundCC, is(true));
    }

    private Uni<ProcessDataResponse> processDoc(PipeDoc doc, ProcessConfiguration config) {
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName("cc-it-pipeline")
                .setPipeStepName("parser-cc-it")
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
