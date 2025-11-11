package ai.pipestream.module.parser.warc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.netpreserve.jwarc.WarcReader;
import org.netpreserve.jwarc.WarcRecord;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

public class WarcJwarcSanityTest {
    @Test
    public void sanityReadFirstRecordWithJwarc() throws Exception {
        List<String> files = Arrays.asList(
                "sample_doc_types/warc/sample-tiny-566b.warc.gz",
                "sample_doc_types/warc/sample-small-336kb.warc.gz",
                "sample_doc_types/warc/sample-medium-646kb.warc.gz"
        );
        int ok = 0;
        for (String p : files) {
            InputStream raw = openTestResource(p);
            Assumptions.assumeTrue(raw != null, "Missing resource: " + p);
            try (GZIPInputStream gis = new GZIPInputStream(raw);
                 WarcReader reader = new WarcReader(gis)) {
                java.util.Iterator<WarcRecord> it = reader.iterator();
                if (it.hasNext()) {
                    WarcRecord r = it.next();
                    // touch a few fields to ensure parse
                    r.type();
                    r.headers().map();
                    ok++;
                }
            }
        }
        assertThat("At least one valid WARC should parse via jwarc", ok, greaterThan(0));
    }

    private static InputStream openTestResource(String logicalPath) throws Exception {
        // Try external path via SAMPLE_DOC_TYPES env or system property
        String sampleTypesRoot = firstNonBlank(
                System.getenv("SAMPLE_DOC_TYPES"),
                System.getProperty("sample.doc.types")
        );
        if (logicalPath.startsWith("sample_doc_types/") && sampleTypesRoot != null && !sampleTypesRoot.isBlank()) {
            String remainder = logicalPath.substring("sample_doc_types/".length());
            Path p = Paths.get(sampleTypesRoot).resolve(remainder);
            if (Files.exists(p)) {
                return Files.newInputStream(p);
            }
        }
        // Fallback to classpath
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return cl.getResourceAsStream(logicalPath);
    }

    private static String firstNonBlank(String... vals) {
        if (vals == null) return null;
        for (String v : vals) if (v != null && !v.trim().isEmpty()) return v;
        return null;
    }
}
