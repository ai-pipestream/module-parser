package ai.pipestream.module.parser.office;

import com.google.protobuf.Any;
import com.google.protobuf.Struct;
import ai.pipestream.data.module.ModuleProcessRequest;
import ai.pipestream.data.module.ModuleProcessResponse;
import ai.pipestream.data.module.PipeStepProcessor;
import ai.pipestream.data.module.ProcessConfiguration;
import ai.pipestream.data.module.ServiceMetadata;
import ai.pipestream.data.v1.PipeDoc;
import ai.pipestream.module.parser.util.ReactiveTestDocumentLoader;
import ai.pipestream.parsed.data.office.v1.OfficeMetadata;
import ai.pipestream.parsed.data.tika.v1.TikaResponse;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@QuarkusTest
public class OfficeFieldCoverageTest {
    private static final Logger LOG = Logger.getLogger(OfficeFieldCoverageTest.class);

    @GrpcClient
    PipeStepProcessor parserService;

    @Test
    public void analyzeOfficeFieldCoverage() {
        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .putConfigParams("extractMetadata", "true")
                .putConfigParams("maxContentLength", "5000000")
                .build();

        Map<String, Integer> fieldCounts = new TreeMap<>();
        AtomicInteger processed = new AtomicInteger(0);
        AtomicInteger typedOffice = new AtomicInteger(0);

        ReactiveTestDocumentLoader.streamTestDocuments("sample_doc_types/office")
                .onItem().transformToUniAndConcatenate(doc -> processDoc(doc, config)
                        .onItem().invoke(resp -> {
                            processed.incrementAndGet();
                            if (!resp.getSuccess() || !resp.hasOutputDoc()) return;
                            PipeDoc out = resp.getOutputDoc();
                            if (!out.hasStructuredData()) return;
                            Any any = out.getStructuredData();
                            if (!any.is(TikaResponse.class)) return;
                            try {
                                TikaResponse tr = any.unpack(TikaResponse.class);
                                if (tr.hasOffice()) {
                                    typedOffice.incrementAndGet();
                                    OfficeMetadata om = tr.getOffice();
                                    for (var e : om.getAllFields().entrySet()) {
                                        fieldCounts.merge(e.getKey().getName(), 1, Integer::sum);
                                    }
                                    if (om.hasAdditionalMetadata()) {
                                        Struct s = om.getAdditionalMetadata();
                                        if (s.getFieldsCount() > 0) {
                                            fieldCounts.merge("_additional_metadata_non_empty", 1, Integer::sum);
                                        }
                                    }
                                }
                            } catch (com.google.protobuf.InvalidProtocolBufferException e) {
                                throw new RuntimeException(e);
                            }
                        })
                )
                .collect().asList()
                .await().atMost(Duration.ofMinutes(3));

        assertThat("Should process Office samples", processed.get(), greaterThan(0));
        assertThat("At least one Office doc should be typed", typedOffice.get(), greaterThanOrEqualTo(1));

        LOG.info("Office field coverage (top):");
        fieldCounts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(25)
                .forEach(e -> LOG.infof(" - %s: %d", e.getKey(), e.getValue()));
    }

    private Uni<ModuleProcessResponse> processDoc(PipeDoc doc, ProcessConfiguration config) {
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName("office-coverage-pipeline")
                .setPipeStepName("parser-office-coverage")
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
