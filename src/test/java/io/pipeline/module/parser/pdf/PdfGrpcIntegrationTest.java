package io.pipeline.module.parser.pdf;

import io.pipeline.data.module.ModuleProcessRequest;
import io.pipeline.data.module.ModuleProcessResponse;
import io.pipeline.data.module.PipeStepProcessor;
import io.pipeline.data.module.ProcessConfiguration;
import io.pipeline.data.module.ServiceMetadata;
import io.pipeline.data.v1.PipeDoc;
import io.pipeline.parsed.data.tika.v1.TikaResponse;
import com.google.protobuf.Any;
import io.pipeline.module.parser.util.ReactiveTestDocumentLoader;
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
public class PdfGrpcIntegrationTest {
    private static final Logger LOG = Logger.getLogger(PdfGrpcIntegrationTest.class);

    @GrpcClient
    PipeStepProcessor parserService;

    @Test
    public void testProcessSamplePdfsViaGrpc() {
        var tracker = new ReactiveTestDocumentLoader.ProgressTracker("PDF gRPC Test", 5);
        AtomicInteger docsWithBody = new AtomicInteger(0);
        AtomicInteger docsWithStructured = new AtomicInteger(0);

        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .putConfigParams("extractMetadata", "true")
                .putConfigParams("maxContentLength", "2000000")
                .build();

        ReactiveTestDocumentLoader.streamTestDocuments("sample_doc_types/pdf")
                .onItem().transformToUniAndConcatenate(doc -> processDoc(doc, config)
                        .onItem().invoke(resp -> {
                            if (resp.getSuccess() && resp.hasOutputDoc()) {
                                tracker.recordSuccess();
                                var out = resp.getOutputDoc();
                                var search = out.getSearchMetadata();
                                if (search.hasBody() && !search.getBody().isEmpty()) {
                                    docsWithBody.incrementAndGet();
                                }
                                if (out.hasStructuredData()) {
                                    docsWithStructured.incrementAndGet();
                                }
                            } else {
                                tracker.recordFailure();
                                LOG.warnf("gRPC parse failed: %s", resp.getProcessorLogsList());
                            }
                        })
                )
                .collect().asList()
                .await().atMost(Duration.ofMinutes(2));

        tracker.logFinal();

        int processed = tracker.getProcessed();
        assertThat("Should process at least one sample PDF", processed, greaterThan(0));
        // Require 100% success and 100% metadata presence
        assertThat("All PDFs should parse successfully", tracker.getSuccessful(), is(processed));
        assertThat("All PDFs should have structured metadata", docsWithStructured.get(), is(processed));
        // Body text may be absent for scanned/image-only PDFs; require at least half to have body
        int bodyThreshold = Math.max(1, processed / 2);
        assertThat("At least half of PDFs should yield body text", docsWithBody.get(), greaterThanOrEqualTo(bodyThreshold));
    }

    @Test
    public void testSimplePdfProcessesAndCapturesMetadata() {
        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .putConfigParams("extractMetadata", "true")
                .build();

        // Stream and filter for simple.pdf
        var response = ReactiveTestDocumentLoader.streamTestDocuments("sample_doc_types/pdf")
                .onItem().transformToUniAndConcatenate(doc -> {
                    // only pass through simple.pdf
                    String filename = doc.getBlobBag().getBlob().getFilename();
                    if (!"simple.pdf".equalsIgnoreCase(filename)) {
                        return Uni.createFrom().nullItem();
                    }
                    return processDoc(doc, config);
                })
                .select().where(item -> item != null)
                .collect().first()
                .await().atMost(Duration.ofSeconds(30));

        assertThat("Should have processed simple.pdf", response, notNullValue());
        assertThat(response.getSuccess(), is(true));
        assertThat(response.hasOutputDoc(), is(true));
        PipeDoc out = response.getOutputDoc();
        // Body may be empty for some PDFs; don't require > 10 chars
        assertThat("structured_data should exist", out.hasStructuredData(), is(true));
        // Unpack TikaResponse and assert typed PDF metadata presence
        Any any = out.getStructuredData();
        if (any.is(TikaResponse.class)) {
            try {
                TikaResponse tr = any.unpack(TikaResponse.class);
                // At least one of pdf or generic should be populated; for PDFs, prefer pdf
                assertThat("TikaResponse should have either pdf or generic metadata",
                        tr.hasPdf() || tr.hasGeneric(), is(true));
            } catch (com.google.protobuf.InvalidProtocolBufferException e) {
                throw new AssertionError("Failed to unpack TikaResponse from structured_data", e);
            }
        }
    }

    private Uni<ModuleProcessResponse> processDoc(PipeDoc doc, ProcessConfiguration config) {
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName("pdf-it-pipeline")
                .setPipeStepName("parser-pdf-it")
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
