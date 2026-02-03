package ai.pipestream.module.parser.util;

import de.siegmar.fastcsv.reader.CsvReader;
import de.siegmar.fastcsv.reader.CsvRecord;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service to load demo sample documents metadata from a CSV index using FastCSV.
 */
@ApplicationScoped
public class SampleLoaderService {

    private static final Logger LOG = Logger.getLogger(SampleLoaderService.class);
    private static final String CSV_PATH = "/META-INF/resources/samples/files.csv";

    public List<Map<String, Object>> loadDemoDocuments() {
        List<Map<String, Object>> documents = new ArrayList<>();

        try (InputStream is = getClass().getResourceAsStream(CSV_PATH)) {
            if (is == null) {
                LOG.warnf("files.csv not found at %s", CSV_PATH);
                return documents;
            }

            // FastCSV works best with a Reader
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                
                // Use ofCsvRecord to get an Iterable<CsvRecord> directly
                Iterable<CsvRecord> csv = CsvReader.builder()
                        .ofCsvRecord(br);

                boolean isHeader = true;
                for (CsvRecord row : csv) {
                    // Skip the first row (header)
                    if (isHeader) {
                        isHeader = false;
                        continue;
                    }

                    // FastCSV provides simple index-based access
                    if (row.getFieldCount() >= 4) {
                        Map<String, Object> doc = new HashMap<>();
                        doc.put("filename", row.getField(0).trim());
                        doc.put("title", row.getField(1).trim());
                        doc.put("description", row.getField(2).trim());
                        doc.put("content_type", row.getField(3).trim());
                        documents.add(doc);
                    } else {
                        LOG.warnf("Skipping invalid line %d in files.csv: insufficient columns", row.getStartingLineNumber());
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("Error reading files.csv", e);
            throw new RuntimeException("Failed to read sample index", e);
        }

        return documents;
    }
}