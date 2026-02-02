package ai.pipestream.module.parser.pdf;

import com.google.protobuf.ByteString;
import ai.pipestream.data.module.v1.ProcessDataRequest;
import ai.pipestream.data.module.v1.ProcessDataResponse;
import ai.pipestream.data.v1.ProcessConfiguration;
import ai.pipestream.data.module.v1.ServiceMetadata;
import ai.pipestream.data.module.v1.PipeStepProcessorService;
import ai.pipestream.data.v1.Blob;
import ai.pipestream.data.v1.BlobBag;
import ai.pipestream.data.v1.PipeDoc;
import ai.pipestream.data.v1.SearchMetadata;
import ai.pipestream.parsed.data.tika.v1.TikaResponse;
import com.google.protobuf.Any;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@QuarkusTest
public class PdfOutlineGeneratedIntegrationTest {

    @GrpcClient
    PipeStepProcessorService parserService;

    @Test
    public void testPdfBookmarksPopulateDocOutline() throws Exception {
        byte[] pdf = generatePdfWithBookmarks();

        // Build PipeDoc with inline blob
        Blob blob = Blob.newBuilder()
                .setBlobId(UUID.randomUUID().toString())
                .setDriveId("test")
                .setData(ByteString.copyFrom(pdf))
                .setMimeType("application/pdf")
                .setFilename("bookmarks.pdf")
                .build();
        BlobBag bag = BlobBag.newBuilder().setBlob(blob).build();
        PipeDoc doc = PipeDoc.newBuilder()
                .setDocId(UUID.randomUUID().toString())
                .setSearchMetadata(SearchMetadata.newBuilder().setTitle("PDF with bookmarks").build())
                .setBlobBag(bag)
                .build();

        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .putConfigParams("extractMetadata", "true")
                .build();
        ServiceMetadata meta = ServiceMetadata.newBuilder()
                .setPipelineName("pdf-outline-it")
                .setPipeStepName("parser-pdf-outline")
                .setStreamId(UUID.randomUUID().toString())
                .setCurrentHopNumber(1)
                .build();
        ProcessDataRequest req = ProcessDataRequest.newBuilder()
                .setDocument(doc)
                .setConfig(config)
                .setMetadata(meta)
                .build();

        ProcessDataResponse resp = parserService.processData(req).await().atMost(Duration.ofSeconds(20));
        assertThat(resp.getSuccess(), is(true));
        assertThat(resp.hasOutputDoc(), is(true));
        PipeDoc out = resp.getOutputDoc();
        assertThat("doc_outline should be populated", out.getSearchMetadata().hasDocOutline(), is(true));
        assertThat(out.getSearchMetadata().getDocOutline().getSectionsCount(), greaterThan(0));

        // Also ensure structured_data is present and typed
        assertThat(out.getParsedMetadataMap().containsKey("tika"), is(true));
        Any any = out.getParsedMetadataMap().get("tika").getData();
        assertThat(any.is(TikaResponse.class), is(true));
    }

    private byte[] generatePdfWithBookmarks() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            // Create pages
            PDPage page1 = new PDPage();
            PDPage page2 = new PDPage();
            PDPage page3 = new PDPage();
            doc.addPage(page1);
            doc.addPage(page2);
            doc.addPage(page3);

            // Outline root
            PDDocumentOutline outline = new PDDocumentOutline();
            doc.getDocumentCatalog().setDocumentOutline(outline);

            // Bookmark 1 -> page 1
            PDOutlineItem bm1 = new PDOutlineItem();
            bm1.setTitle("Chapter 1");
            PDPageFitDestination dest1 = new PDPageFitDestination();
            dest1.setPage(page1);
            bm1.setDestination(dest1);
            outline.addLast(bm1);

            // Bookmark 2 -> page 2 with a child
            PDOutlineItem bm2 = new PDOutlineItem();
            bm2.setTitle("Chapter 2");
            PDPageFitDestination dest2 = new PDPageFitDestination();
            dest2.setPage(page2);
            bm2.setDestination(dest2);
            outline.addLast(bm2);

            PDOutlineItem bm2a = new PDOutlineItem();
            bm2a.setTitle("Section 2.1");
            PDPageFitDestination dest2a = new PDPageFitDestination();
            dest2a.setPage(page2);
            bm2a.setDestination(dest2a);
            bm2.addLast(bm2a);

            // Bookmark 3 -> page 3
            PDOutlineItem bm3 = new PDOutlineItem();
            bm3.setTitle("Chapter 3");
            PDPageFitDestination dest3 = new PDPageFitDestination();
            dest3.setPage(page3);
            bm3.setDestination(dest3);
            outline.addLast(bm3);

            outline.openNode();
            bm1.openNode();
            bm2.openNode();

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            doc.save(bos);
            return bos.toByteArray();
        }
    }
}
