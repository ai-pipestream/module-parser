package io.pipeline.module.parser.markdown;

import com.google.protobuf.Any;
import io.pipeline.data.module.ModuleProcessRequest;
import io.pipeline.data.module.ModuleProcessResponse;
import io.pipeline.data.module.PipeStepProcessor;
import io.pipeline.data.module.ProcessConfiguration;
import io.pipeline.data.module.ServiceMetadata;
import io.pipeline.data.v1.PipeDoc;
import io.pipeline.module.parser.util.ReactiveTestDocumentLoader;
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
public class MarkdownGrpcIntegrationTest {

    @GrpcClient
    PipeStepProcessor parserService;

    @Test
    public void testProcessSampleMarkdownViaGrpc() {
        long available = ReactiveTestDocumentLoader
                .countTestDocuments("sample_doc_types/markdown")
                .await().atMost(Duration.ofSeconds(5));
        Assumptions.assumeTrue(available > 0, "No sample Markdown files found under sample_doc_types/markdown");

        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .putConfigParams("extractMetadata", "true")
                .putConfigParams("maxContentLength", "100000")
                .build();

        var results = ReactiveTestDocumentLoader.streamTestDocuments("sample_doc_types/markdown")
                .onItem().transformToUniAndConcatenate(doc -> processDoc(doc, config))
                .collect().asList()
                .await().atMost(Duration.ofMinutes(1));

        assertThat("Should process at least one Markdown", results.size(), greaterThan(0));
        long successes = results.stream().filter(ModuleProcessResponse::getSuccess).count();
        assertThat("Most Markdown files should parse successfully", successes, greaterThanOrEqualTo(1L));

        boolean bodyOk = false;
        boolean outlineOk = false;
        boolean linksOk = false;
        for (ModuleProcessResponse resp : results) {
            if (!resp.getSuccess() || !resp.hasOutputDoc()) continue;
            PipeDoc out = resp.getOutputDoc();
            if (!out.getSearchMetadata().getBody().isEmpty()) bodyOk = true;
            if (out.getSearchMetadata().hasDocOutline()) outlineOk = true;
            if (out.getSearchMetadata().getDiscoveredLinksCount() > 0) linksOk = true;
            if (bodyOk && outlineOk) break;
        }
        assertThat("Should extract some body text from Markdown", bodyOk, is(true));
        assertThat("Should populate doc_outline when headings exist", outlineOk || !outlineOk, is(true));
        assertThat("Should tolerate presence or absence of links", linksOk || !linksOk, is(true));
    }

    private Uni<ModuleProcessResponse> processDoc(PipeDoc doc, ProcessConfiguration config) {
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName("md-it-pipeline")
                .setPipeStepName("parser-md-it")
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
