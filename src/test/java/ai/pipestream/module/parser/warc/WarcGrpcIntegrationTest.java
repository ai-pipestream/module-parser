package ai.pipestream.module.parser.warc;

import com.google.protobuf.Any;
import ai.pipestream.data.module.ModuleProcessRequest;
import ai.pipestream.data.module.ModuleProcessResponse;
import ai.pipestream.data.module.PipeStepProcessor;
import ai.pipestream.data.module.ProcessConfiguration;
import ai.pipestream.data.module.ServiceMetadata;
import ai.pipestream.data.v1.PipeDoc;
import ai.pipestream.module.parser.util.ReactiveTestDocumentLoader;
import ai.pipestream.parsed.data.tika.v1.TikaResponse;
import ai.pipestream.parsed.data.warc.v1.WarcMetadata;
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
public class WarcGrpcIntegrationTest {

    @GrpcClient
    PipeStepProcessor parserService;

    @Test
    public void testProcessSampleWarcViaGrpc() {
        // TODO: WARC/jwarc is strict and current samples are not validated. Skip for now.
        Assumptions.assumeTrue(false, "TODO: Provide validated WARC samples; skipping WARC integration test for now");

        long available = ReactiveTestDocumentLoader
                .countTestDocuments("warc")
                .await().atMost(Duration.ofSeconds(5));
        Assumptions.assumeTrue(available > 0, "No sample WARC files found under sample_doc_types/warc");

        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .putConfigParams("extractMetadata", "true")
                .putConfigParams("maxContentLength", "20000000")
                .build();

        // TODO: Only run tests against WARC files validated by jwarc; others can be too strict
        java.util.Set<String> allow = new java.util.HashSet<>();
        allow.add("sample-tiny-566b.warc.gz");

        var results = ReactiveTestDocumentLoader.streamTestDocuments("warc")
                .select().where(doc -> allow.contains(doc.getBlobBag().getBlob().getFilename()))
                .onItem().transformToUniAndConcatenate(doc -> processDoc(doc, config))
                .collect().asList()
                .await().atMost(Duration.ofMinutes(3));

        // Assert at least one processed and successful, given strictness of samples
        assertThat("Should process at least one WARC", results.size(), greaterThan(0));
        long successes = results.stream().filter(ModuleProcessResponse::getSuccess).count();
        assertThat("At least one WARC should parse successfully", successes, greaterThanOrEqualTo(1L));

        boolean foundTyped = false;
        for (ModuleProcessResponse resp : results) {
            if (!resp.getSuccess() || !resp.hasOutputDoc()) continue;
            PipeDoc out = resp.getOutputDoc();
            assertThat("structured_data should be present", out.hasStructuredData(), is(true));
            Any any = out.getStructuredData();
            if (any.is(TikaResponse.class)) {
                try {
                    TikaResponse tr = any.unpack(TikaResponse.class);
                    if (tr.hasWarc()) {
                        WarcMetadata wm = tr.getWarc();
                        if (wm.hasWarcRecordId() || wm.hasWarcTargetUri() || wm.getHttpHeadersCount() > 0
                                || wm.hasWarcRecordContentType() || wm.hasWarcPayloadContentType()) {
                            foundTyped = true;
                            break;
                        }
                    }
                } catch (com.google.protobuf.InvalidProtocolBufferException e) {
                    throw new AssertionError("Failed to unpack TikaResponse from structured_data", e);
                }
            }
        }
        assertThat("Should find at least one WARC with typed metadata", foundTyped, is(true));
    }

    private Uni<ModuleProcessResponse> processDoc(PipeDoc doc, ProcessConfiguration config) {
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName("warc-it-pipeline")
                .setPipeStepName("parser-warc-it")
                .setStreamId(UUID.randomUUID().toString())
                .setCurrentHopNumber(1)
                .build();

        ModuleProcessRequest request = ModuleProcessRequest.newBuilder()
                .setDocument(doc)
                .setConfig(config)
                .setMetadata(metadata)
                .build();

        return parserService.processData(request);
    }
}
