package ai.pipestream.module.parser.database;

import com.google.protobuf.Any;
import ai.pipestream.data.module.v1.ProcessDataRequest;
import ai.pipestream.data.module.v1.ProcessDataResponse;
import ai.pipestream.data.module.v1.PipeStepProcessorService;
import ai.pipestream.data.v1.ProcessConfiguration;
import ai.pipestream.data.module.v1.ServiceMetadata;
import ai.pipestream.data.v1.PipeDoc;
import ai.pipestream.module.parser.util.ReactiveTestDocumentLoader;
import ai.pipestream.parsed.data.tika.v1.TikaResponse;
import ai.pipestream.parsed.data.database.v1.DatabaseMetadata;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@QuarkusTest
public class DatabaseGrpcIntegrationTest {

    @GrpcClient
    PipeStepProcessorService parserService;

    @Test
    public void testProcessSampleDatabasesViaGrpc() {
        long available = ReactiveTestDocumentLoader
                .countTestDocuments("database")
                .await().atMost(Duration.ofSeconds(5));
        Assumptions.assumeTrue(available > 0, "No sample database files found under sample_doc_types/database");

        // Limit to small sqlite/db files that are known to be parseable by Tika without JDBC
        Set<String> allow = Set.of(
                "northwind_small.sqlite", "sakila.db", "chinook.db", "sample_database.dbf"
        );

        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .putConfigParams("extractMetadata", "true")
                .putConfigParams("maxContentLength", "5000000")
                .build();

        var results = ReactiveTestDocumentLoader.streamTestDocuments("database")
                .select().where(doc -> allow.contains(doc.getBlobBag().getBlob().getFilename()))
                .onItem().transformToUniAndConcatenate(doc -> processDoc(doc, config))
                .collect().asList()
                .await().atMost(Duration.ofMinutes(2));

        assertThat("Should process at least one database file", results.size(), greaterThan(0));
        long successes = results.stream().filter(ProcessDataResponse::getSuccess).count();
        assertThat("All selected database samples should parse successfully", successes, is((long) results.size()));

        boolean foundTyped = false;
        for (ProcessDataResponse resp : results) {
            if (!resp.getSuccess() || !resp.hasOutputDoc()) continue;
            PipeDoc out = resp.getOutputDoc();
            assertThat("structured_data should be present", out.hasStructuredData(), is(true));
            Any any = out.getStructuredData();
            if (any.is(TikaResponse.class)) {
                try {
                    TikaResponse tr = any.unpack(TikaResponse.class);
                    if (tr.hasDatabase()) {
                        DatabaseMetadata db = tr.getDatabase();
                        // At minimum, typed object exists and has basic fields like content_type/resource_name
                        if (db.hasContentType() || db.hasResourceName()) {
                            foundTyped = true;
                            break;
                        }
                    }
                } catch (com.google.protobuf.InvalidProtocolBufferException e) {
                    throw new AssertionError("Failed to unpack TikaResponse from structured_data", e);
                }
            }
        }
        assertThat("Should find at least one database with typed metadata", foundTyped, is(true));
    }

    private Uni<ProcessDataResponse> processDoc(PipeDoc doc, ProcessConfiguration config) {
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName("database-it-pipeline")
                .setPipeStepName("parser-database-it")
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
