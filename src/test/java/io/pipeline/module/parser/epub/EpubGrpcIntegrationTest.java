package io.pipeline.module.parser.epub;

import com.google.protobuf.Any;
import io.pipeline.data.module.ModuleProcessRequest;
import io.pipeline.data.module.ModuleProcessResponse;
import io.pipeline.data.module.PipeStepProcessor;
import io.pipeline.data.module.ProcessConfiguration;
import io.pipeline.data.module.ServiceMetadata;
import io.pipeline.data.v1.PipeDoc;
import io.pipeline.module.parser.util.ReactiveTestDocumentLoader;
import io.pipeline.parsed.data.epub.v1.EpubMetadata;
import io.pipeline.parsed.data.tika.v1.TikaResponse;
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
    PipeStepProcessor parserService;

    @Test
    public void testProcessSampleEpubsViaGrpc() {
        long available = ReactiveTestDocumentLoader
                .countTestDocuments("sample_doc_types/ebooks/epub")
                .await().atMost(Duration.ofSeconds(5));
        Assumptions.assumeTrue(available > 0, "No sample EPUBs found under sample_doc_types/epub");

        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .putConfigParams("extractMetadata", "true")
                .putConfigParams("maxContentLength", "5000000")
                .build();

        var results = ReactiveTestDocumentLoader.streamTestDocumentsWithExtension("sample_doc_types/ebooks/epub", ".epub")
                .onItem().transformToUniAndConcatenate(doc -> processDoc(doc, config))
                .collect().asList()
                .await().atMost(Duration.ofMinutes(2));

        assertThat("Should process at least one epub", results.size(), greaterThan(0));
        long successes = results.stream().filter(ModuleProcessResponse::getSuccess).count();
        assertThat("Most epub files should parse successfully", successes, greaterThanOrEqualTo(1L));

        boolean foundTyped = false;
        for (ModuleProcessResponse resp : results) {
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

    private Uni<ModuleProcessResponse> processDoc(PipeDoc doc, ProcessConfiguration config) {
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName("epub-it-pipeline")
                .setPipeStepName("parser-epub-it")
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
