package ai.pipestream.module.parser.warc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.netpreserve.jwarc.WarcReader;
import org.netpreserve.jwarc.WarcRecord;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

public class WarcNormalizationTest {
    @Test
    public void normalizeAndParseWithJwarc() throws Exception {
        List<String> files = Arrays.asList(
                "sample_doc_types/warc/sample-tiny-566b.warc.gz",
                "sample_doc_types/warc/sample-small-336kb.warc.gz",
                "sample_doc_types/warc/sample-medium-646kb.warc.gz"
        );
        int ok = 0;
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        for (String p : files) {
            InputStream raw = cl.getResourceAsStream(p);
            Assumptions.assumeTrue(raw != null, "Missing resource: " + p);
            byte[] original = raw.readAllBytes();
            byte[] normalized = WarcNormalizer.normalizeGz(original);
            try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(normalized));
                 WarcReader reader = new WarcReader(gis)) {
                java.util.Iterator<WarcRecord> it = reader.iterator();
                if (it.hasNext()) {
                    WarcRecord r = it.next();
                    r.type();
                    r.headers().map();
                    ok++;
                }
            }
        }
        assertThat("At least one normalized WARC should parse via jwarc", ok, greaterThan(0));
    }
}
