package ai.pipestream.module.parser.util;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

@QuarkusTest
public class SampleLoaderServiceTest {

    @Inject
    SampleLoaderService sampleLoaderService;

    @Test
    public void testLoadDemoDocuments() {
        List<Map<String, Object>> documents = sampleLoaderService.loadDemoDocuments();

        Assertions.assertNotNull(documents, "Documents list should not be null");
        Assertions.assertEquals(4, documents.size(), "Should load exactly 4 sample documents from files.csv");

        // Verify content of the first document (MagPi145.pdf)
        Map<String, Object> doc1 = documents.get(0);
        Assertions.assertEquals("MagPi145.pdf", doc1.get("filename"));
        Assertions.assertEquals("The MagPi Issue 145", doc1.get("title"));
        Assertions.assertEquals("Raspberry Pi magazine issue", doc1.get("description"));
        Assertions.assertEquals("application/pdf", doc1.get("content_type"));

        // Verify content of the last document (irs_f1040.pdf)
        Map<String, Object> doc4 = documents.get(3);
        Assertions.assertEquals("irs_f1040.pdf", doc4.get("filename"));
        Assertions.assertEquals("IRS Form 1040", doc4.get("title"));
        Assertions.assertEquals("US Individual Income Tax Return", doc4.get("description"));
    }
}
