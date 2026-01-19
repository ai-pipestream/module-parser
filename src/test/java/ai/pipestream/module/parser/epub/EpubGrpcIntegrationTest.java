package ai.pipestream.module.parser.epub;

import com.google.protobuf.Any;
import ai.pipestream.data.module.v1.ProcessDataRequest;
import ai.pipestream.data.module.v1.ProcessDataResponse;
import ai.pipestream.data.module.v1.PipeStepProcessorService;
import ai.pipestream.data.v1.ProcessConfiguration;
import ai.pipestream.data.module.v1.ServiceMetadata;
import ai.pipestream.data.v1.PipeDoc;
import ai.pipestream.module.parser.util.ReactiveTestDocumentLoader;
import ai.pipestream.parsed.data.epub.v1.EpubMetadata;
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
public class EpubGrpcIntegrationTest {

    @GrpcClient
    PipeStepProcessorService parserService;

    @Test
    public void testProcessSampleEpubsViaGrpc() {
        long available = ReactiveTestDocumentLoader
                .countTestDocuments("ebooks/epub")
                .await().atMost(Duration.ofSeconds(5));
        Assumptions.assumeTrue(available > 0, "No sample EPUBs found under sample_doc_types/epub");

        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .putConfigParams("extractMetadata", "true")
                .putConfigParams("maxContentLength", "5000000")
                .build();

        var results = ReactiveTestDocumentLoader.streamTestDocumentsWithExtension("ebooks/epub", ".epub")
                .onItem().transformToUniAndConcatenate(doc -> processDoc(doc, config))
                .collect().asList()
                .await().atMost(Duration.ofMinutes(2));

        assertThat("Should process at least one epub", results.size(), greaterThan(0));
        long successes = results.stream().filter(ProcessDataResponse::getSuccess).count();
        assertThat("Most epub files should parse successfully", successes, greaterThanOrEqualTo(1L));

        boolean foundTyped = false;
        for (ProcessDataResponse resp : results) {
            if (!resp.getSuccess() || !resp.hasOutputDoc()) continue;
            PipeDoc out = resp.getOutputDoc();
            assertThat("structured_data should be present", out.hasStructuredData(), is(true));
            Any any = out.getStructuredData();
            if (any.is(TikaResponse.class)) {
                try {
                    TikaResponse tr = any.unpack(TikaResponse.class);
                    if (tr.hasEpub()) {
                        EpubMetadata em = tr.getEpub();
                        if (em.hasVersion() || em.hasRenditionLayout() || em.hasMimetype()) {
                            foundTyped = true;
                            break;
                        }
                    }
                } catch (com.google.protobuf.InvalidProtocolBufferException e) {
                    throw new AssertionError("Failed to unpack TikaResponse from structured_data", e);
                }
            }
        }
        assertThat("Should find at least one EPUB with typed metadata", foundTyped, is(true));
    }

    private Uni<ProcessDataResponse> processDoc(PipeDoc doc, ProcessConfiguration config) {
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName("epub-it-pipeline")
                .setPipeStepName("parser-epub-it")
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
