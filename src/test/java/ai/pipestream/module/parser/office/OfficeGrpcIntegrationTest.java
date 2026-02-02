package ai.pipestream.module.parser.office;

import com.google.protobuf.Any;
import ai.pipestream.data.module.v1.ProcessDataRequest;
import ai.pipestream.data.module.v1.ProcessDataResponse;
import ai.pipestream.data.module.v1.PipeStepProcessorService;
import ai.pipestream.data.v1.ProcessConfiguration;
import ai.pipestream.data.module.v1.ServiceMetadata;
import ai.pipestream.data.v1.PipeDoc;
import ai.pipestream.module.parser.util.ReactiveTestDocumentLoader;
import ai.pipestream.parsed.data.tika.v1.TikaResponse;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@QuarkusTest
public class OfficeGrpcIntegrationTest {
    private static final Logger LOG = Logger.getLogger(OfficeGrpcIntegrationTest.class);

    @GrpcClient
    PipeStepProcessorService parserService;

    @Test
    public void testProcessSampleOfficeViaGrpc() {
        var tracker = new ReactiveTestDocumentLoader.ProgressTracker("Office gRPC Test", 10);
        AtomicInteger docsWithStructured = new AtomicInteger(0);
        AtomicInteger officeTyped = new AtomicInteger(0);

        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .putConfigParams("extractMetadata", "true")
                .putConfigParams("maxContentLength", "3000000")
                .build();

        // Use root-level path from sample-doc-types JAR (not sample_doc_types/office)
        ReactiveTestDocumentLoader.streamTestDocuments("office")
                .onItem().transformToUniAndConcatenate(doc -> processDoc(doc, config)
                        .onItem().invoke(resp -> {
                            if (resp.getSuccess() && resp.hasOutputDoc()) {
                                tracker.recordSuccess();
                                PipeDoc out = resp.getOutputDoc();
                                if (out.getParsedMetadataMap().containsKey("tika")) {
                                    docsWithStructured.incrementAndGet();
                                    Any any = out.getParsedMetadataMap().get("tika").getData();
                                    if (any.is(TikaResponse.class)) {
                                        try {
                                            TikaResponse tr = any.unpack(TikaResponse.class);
                                            if (tr.hasOffice()) {
                                                officeTyped.incrementAndGet();
                                            }
                                        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
                                            throw new AssertionError("Failed to unpack TikaResponse", e);
                                        }
                                    }
                                }
                            } else {
                                tracker.recordFailure();
                                LOG.warnf("gRPC parse failed: %s", resp.getProcessorLogsList());
                            }
                        })
                )
                .collect().asList()
                .await().atMost(Duration.ofMinutes(3));

        tracker.logFinal();
        int processed = tracker.getProcessed();
        assertThat("Should process at least one Office sample", processed, greaterThan(0));
        double successRate = (double) tracker.getSuccessful() / processed;
        assertThat("High success rate expected for sample Office docs", successRate, greaterThanOrEqualTo(0.85));
        assertThat("Structured data should be present in some outputs", docsWithStructured.get(), greaterThanOrEqualTo(1));
        assertThat("At least one Office doc should result in typed Office metadata", officeTyped.get(), greaterThanOrEqualTo(1));
    }

    private Uni<ProcessDataResponse> processDoc(PipeDoc doc, ProcessConfiguration config) {
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName("office-it-pipeline")
                .setPipeStepName("parser-office-it")
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
