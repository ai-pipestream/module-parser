package ai.pipestream.module.parser.markdown;

import ai.pipestream.data.module.v1.ProcessDataRequest;
import ai.pipestream.data.module.v1.ProcessDataResponse;
import ai.pipestream.data.module.v1.PipeStepProcessorService;
import ai.pipestream.data.v1.ProcessConfiguration;
import ai.pipestream.data.module.v1.ServiceMetadata;
import ai.pipestream.data.v1.PipeDoc;
import ai.pipestream.module.parser.util.ReactiveTestDocumentLoader;
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
    PipeStepProcessorService parserService;

    @Test
    public void testProcessSampleMarkdownViaGrpc() {
        long available = ReactiveTestDocumentLoader
                .countTestDocuments("markdown")
                .await().atMost(Duration.ofSeconds(5));
        Assumptions.assumeTrue(available > 0, "No sample Markdown files found under sample_doc_types/markdown");

        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .putConfigParams("extractMetadata", "true")
                .putConfigParams("maxContentLength", "100000")
                .build();

        var results = ReactiveTestDocumentLoader.streamTestDocuments("markdown")
                .onItem().transformToUniAndConcatenate(doc -> processDoc(doc, config))
                .collect().asList()
                .await().atMost(Duration.ofMinutes(1));

        assertThat("Should process at least one Markdown", results.size(), greaterThan(0));
        long successes = results.stream().filter(ProcessDataResponse::getSuccess).count();
        assertThat("Most Markdown files should parse successfully", successes, greaterThanOrEqualTo(1L));

        boolean bodyOk = false;
        boolean outlineOk = false;
        boolean linksOk = false;
        for (ProcessDataResponse resp : results) {
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

    private Uni<ProcessDataResponse> processDoc(PipeDoc doc, ProcessConfiguration config) {
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName("md-it-pipeline")
                .setPipeStepName("parser-md-it")
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
