package io.pipeline.module.parser.pdf;

import com.google.protobuf.Any;
import com.google.protobuf.Struct;
import com.google.protobuf.util.JsonFormat;
import com.google.protobuf.Descriptors.FieldDescriptor;
import io.pipeline.data.module.ModuleProcessRequest;
import io.pipeline.data.module.ModuleProcessResponse;
import io.pipeline.data.module.PipeStepProcessor;
import io.pipeline.data.module.ProcessConfiguration;
import io.pipeline.data.module.ServiceMetadata;
import io.pipeline.data.v1.PipeDoc;
import io.pipeline.module.parser.util.ReactiveTestDocumentLoader;
import io.pipeline.parsed.data.pdf.v1.PdfMetadata;
import io.pipeline.parsed.data.tika.v1.TikaResponse;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@QuarkusTest
public class PdfFieldCoverageTest {
    private static final Logger LOG = Logger.getLogger(PdfFieldCoverageTest.class);

    @GrpcClient
    PipeStepProcessor parserService;

    @Test
    public void analyzePdfFieldCoverage() {
        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .putConfigParams("extractMetadata", "true")
                .putConfigParams("maxContentLength", "5000000")
                .build();

        // Coverage accumulators
        Map<String, Integer> fieldPresenceCounts = new TreeMap<>();
        AtomicInteger docsParsed = new AtomicInteger(0);
        AtomicInteger tikaResponses = new AtomicInteger(0);
        AtomicInteger pdfTypedCount = new AtomicInteger(0);
        AtomicInteger genericTypedCount = new AtomicInteger(0);
        AtomicInteger additionalStructNonEmpty = new AtomicInteger(0);

        ReactiveTestDocumentLoader.streamTestDocuments("sample_doc_types/pdf")
                .onItem().transformToUniAndConcatenate(doc -> processDoc(doc, config)
                        .onItem().invoke(resp -> {
                            docsParsed.incrementAndGet();
                            if (!resp.getSuccess() || !resp.hasOutputDoc()) {
                                LOG.warnf("Failed to process: %s", resp.getProcessorLogsList());
                                return;
                            }
                            PipeDoc out = resp.getOutputDoc();
                            if (!out.hasStructuredData()) {
                                LOG.warn("No structured_data in output");
                                return;
                            }
                            Any any = out.getStructuredData();
                            if (!any.is(TikaResponse.class)) {
                                LOG.warn("structured_data is not a TikaResponse; skipping coverage");
                                return;
                            }
                            tikaResponses.incrementAndGet();
                            TikaResponse tr;
                            try {
                                tr = any.unpack(TikaResponse.class);
                            } catch (com.google.protobuf.InvalidProtocolBufferException e) {
                                throw new RuntimeException("Failed to unpack TikaResponse", e);
                            }

                            if (tr.hasPdf()) {
                                pdfTypedCount.incrementAndGet();
                                PdfMetadata pdf = tr.getPdf();
                                Map<FieldDescriptor, Object> setFields = pdf.getAllFields();
                                for (FieldDescriptor fd : setFields.keySet()) {
                                    String name = fd.getName();
                                    fieldPresenceCounts.merge(name, 1, Integer::sum);
                                }
                                // Track additional metadata struct size
                                if (pdf.hasAdditionalMetadata()) {
                                    Struct s = pdf.getAdditionalMetadata();
                                    if (s.getFieldsCount() > 0) {
                                        additionalStructNonEmpty.incrementAndGet();
                                    }
                                }
                            } else if (tr.hasGeneric()) {
                                genericTypedCount.incrementAndGet();
                            }
                        })
                )
                .collect().asList()
                .await().atMost(Duration.ofMinutes(2));

        // Basic assertions
        assertThat("Should parse at least one PDF", docsParsed.get(), greaterThan(0));
        assertThat("Should get TikaResponse for parsed docs", tikaResponses.get(), greaterThan(0));
        assertThat("Most responses should be typed as pdf", pdfTypedCount.get(), greaterThanOrEqualTo(1));

        // Useful expectations: some common fields should appear across the sample set
        // These are soft-ish expectations; assert at least one occurrence where likely
        List<String> expectedCandidates = Arrays.asList(
                "pdf_version", "producer", "is_encrypted", "n_pages", "doc_info_title", "doc_info_creator"
        );
        int hits = 0;
        for (String name : expectedCandidates) {
            Integer count = fieldPresenceCounts.get(name);
            if (count != null && count > 0) hits++;
        }
        assertThat("At least some common PDF fields should be present in the set", hits, greaterThanOrEqualTo(2));

        // Log summary for developer visibility
        LOG.infof("PDF typed count: %d, generic typed count: %d, additional struct non-empty: %d",
                pdfTypedCount.get(), genericTypedCount.get(), additionalStructNonEmpty.get());
        LOG.info("Top fields by presence:");
        fieldPresenceCounts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(20)
                .forEach(e -> LOG.infof(" - %s: %d", e.getKey(), e.getValue()));
    }

    private Uni<ModuleProcessResponse> processDoc(PipeDoc doc, ProcessConfiguration config) {
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName("pdf-coverage-pipeline")
                .setPipeStepName("parser-pdf-coverage")
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
