package ai.pipestream.module.parser.warc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.netpreserve.jwarc.WarcReader;
import org.netpreserve.jwarc.WarcRecord;

import java.io.InputStream;
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
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        for (String p : files) {
            InputStream raw = cl.getResourceAsStream(p);
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
}
