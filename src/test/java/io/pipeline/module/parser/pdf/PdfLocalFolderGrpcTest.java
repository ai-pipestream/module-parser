package io.pipeline.module.parser.pdf;

import com.google.protobuf.ByteString;
import com.google.protobuf.Any;
import io.pipeline.data.module.ModuleProcessRequest;
import io.pipeline.data.module.ModuleProcessResponse;
import io.pipeline.data.module.PipeStepProcessor;
import io.pipeline.data.module.ProcessConfiguration;
import io.pipeline.data.module.ServiceMetadata;
import io.pipeline.data.v1.Blob;
import io.pipeline.data.v1.BlobBag;
import io.pipeline.data.v1.PipeDoc;
import io.pipeline.parsed.data.tika.v1.TikaResponse;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.*;
import java.time.Duration;
import java.util.UUID;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@QuarkusTest
public class PdfLocalFolderGrpcTest {
    private static final Logger LOG = Logger.getLogger(PdfLocalFolderGrpcTest.class);

    @GrpcClient
    PipeStepProcessor parserService;

    @Test
    public void testLocalHomePdfFolderIfPresent() throws Exception {
        Path homePdf = Paths.get(System.getProperty("user.home"), "pdf");
        if (!Files.exists(homePdf) || !Files.isDirectory(homePdf)) {
            Assumptions.abort("Skipping local ~/pdf test; folder not found: " + homePdf);
            return;
        }

        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .putConfigParams("extractMetadata", "true")
                .putConfigParams("maxContentLength", "5000000")
                .build();

        int processed = 0;
        int successes = 0;
        try (Stream<Path> walk = Files.walk(homePdf)) {
            for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile).filter(f -> f.toString().toLowerCase().endsWith(".pdf")).limit(5)::iterator) {
                processed++;
                ModuleProcessResponse resp = processPath(p, config)
                        .await().atMost(Duration.ofSeconds(20));
                assertThat("gRPC call should return a response", resp, notNullValue());
                if (resp.getSuccess() && resp.hasOutputDoc()) {
                    successes++;
                    PipeDoc out = resp.getOutputDoc();
                    assertThat("structured_data should exist", out.hasStructuredData(), is(true));
                    Any any = out.getStructuredData();
                    if (any.is(TikaResponse.class)) {
                        try {
                            TikaResponse tr = any.unpack(TikaResponse.class);
                            assertThat("TikaResponse should have typed metadata", tr.hasPdf() || tr.hasGeneric(), is(true));
                        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
                            throw new AssertionError("Failed to unpack TikaResponse from structured_data", e);
                        }
                    }
                } else {
                    LOG.warnf("Failed to process local PDF %s: %s", p.getFileName(), resp.getProcessorLogsList());
                }
            }
        }

        assertThat("Should process at least one local PDF if folder present", processed, greaterThan(0));
        double successRate = (double) successes / processed;
        assertThat("Reasonable success rate on local PDFs", successRate, greaterThan(0.6));
    }

    private Uni<ModuleProcessResponse> processPath(Path pdfPath, ProcessConfiguration config) throws Exception {
        byte[] content = Files.readAllBytes(pdfPath);
        Blob blob = Blob.newBuilder()
                .setBlobId(UUID.randomUUID().toString() + "-blob")
                .setDriveId("local-test-drive")
                .setData(ByteString.copyFrom(content))
                .setMimeType("application/pdf")
                .setFilename(pdfPath.getFileName().toString())
                .setSizeBytes(content.length)
                .build();

        PipeDoc doc = PipeDoc.newBuilder()
                .setDocId(UUID.randomUUID().toString())
                .setBlobBag(BlobBag.newBuilder().setBlob(blob).build())
                .build();

        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName("pdf-local-it-pipeline")
                .setPipeStepName("parser-pdf-local-it")
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
