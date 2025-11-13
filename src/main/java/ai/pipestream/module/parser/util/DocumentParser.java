package ai.pipestream.module.parser.util;

import ai.pipestream.data.v1.DocOutline;
import ai.pipestream.data.v1.PipeDoc;
import ai.pipestream.data.v1.SearchMetadata;
import com.google.protobuf.ByteString;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import ai.pipestream.module.parser.config.ParserConfig;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.tika.Tika;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.WriteOutContentHandler;
import org.jboss.logging.Logger;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.tika.mime.MediaType;
import org.apache.tika.metadata.XMPRights;

/**
 * CDI bean for parsing documents using Apache Tika.
 *
 * <p>This class provides methods to parse various document formats using Apache Tika.
 * It supports custom parser configurations, metadata extraction, and content length limits.
 * The parser can handle various document formats including PDF, Microsoft Office documents,
 * HTML, XML, and plain text files.</p>
 *
 * <p>The parser can be configured to disable specific parsers (like EMF) for problematic
 * file types, or enable special parsers (like GeoTopicParser) for enhanced functionality.</p>
 */
@Singleton
public class DocumentParser {
    private static final Logger LOG = Logger.getLogger(DocumentParser.class);
    private static final Tika TIKA = new Tika();

    @Inject
    MetadataMapper metadataMapper;

    /**
     * Parses a document and returns a PipeDoc with the parsed content using ParserConfig.
     *
     * @param content The content of the document to parse.
     * @param config The parser configuration.
     * @param filename Optional filename for content type detection and EMF parser logic.
     * @return A PipeDoc object containing the parsed title, body, and metadata.
     * @throws IOException if an I/O error occurs while parsing the document.
     * @throws SAXException if a SAX error occurs while parsing the document.
     * @throws TikaException if a Tika error occurs while parsing the document.
     */
    public PipeDoc parseDocument(ByteString content, ParserConfig config, String filename)
            throws IOException, SAXException, TikaException {
        
        LOG.debugf("Parsing document with filename: %s, content size: %d bytes, config ID: %s", 
                  filename, content.size(), config.configId());
        
        // Convert ParserConfig to Map for compatibility with existing methods
        Map<String, String> configMap = convertConfigToMap(config);
        
        // Use existing implementation
        return parseDocument(content, configMap, filename);
    }

    /**
     * Parses a document and returns a PipeDoc with the parsed content using legacy Map config.
     *
     * @param content The content of the document to parse.
     * @param configMap The configuration map for the parser.
     * @param filename Optional filename for content type detection and EMF parser logic.
     * @return A PipeDoc object containing the parsed title, body, and metadata.
     * @throws IOException if an I/O error occurs while parsing the document.
     * @throws SAXException if a SAX error occurs while parsing the document.
     * @throws TikaException if a Tika error occurs while parsing the document.
     */
    public PipeDoc parseDocument(ByteString content, Map<String, String> configMap, String filename)
            throws IOException, SAXException, TikaException {
        
        LOG.debugf("Parsing document with filename: %s, content size: %d bytes", 
                  filename, content.size());

        // Early-return for fonts: avoid Tika content parsing entirely
        if (filename != null && filename.toLowerCase().matches(".*\\.(ttf|otf|woff2?|pfa|pfb)$")) {
            String lowerName = filename.toLowerCase();
            String contentType = lowerName.endsWith(".ttf") ? "font/ttf"
                    : lowerName.endsWith(".otf") ? "font/otf"
                    : lowerName.endsWith(".woff2") ? "font/woff2"
                    : lowerName.endsWith(".woff") ? "font/woff"
                    : "application/octet-stream";

            String title = filename;
            int dot = filename.lastIndexOf('.');
            if (dot > 0) {
                title = filename.substring(0, dot);
            }

            SearchMetadata.Builder searchMetadataBuilder = SearchMetadata.newBuilder()
                    .setTitle(title)
                    .setBody("");

            PipeDoc.Builder docBuilder = PipeDoc.newBuilder()
                    .setSearchMetadata(searchMetadataBuilder.build());

            LOG.debugf("Skipped content parse for font; returning minimal PipeDoc (Content-Type hint: %s)", contentType);
            return docBuilder.build();
        }
        
        // Create the appropriate parser based on configuration
        Parser parser = createParser(configMap, filename);
        
        // Set up the content handler with the specified max content length
        BodyContentHandler handler = createContentHandler(configMap);
        
        // Set up metadata and parse context
        Metadata metadata = new Metadata();
        ParseContext parseContext = new ParseContext();
        parseContext.set(Parser.class, parser);
        
        // Parse the document
        try (InputStream stream = new ByteArrayInputStream(content.toByteArray())) {
            // Add filename to metadata if available
            if (filename != null && !filename.isEmpty()) {
                metadata.set("resourceName", filename);
                // Hint content type to prefer the correct parser (e.g., PDF, fonts)
                try {
                    String hintedType = TIKA.detect(filename);
                    if (hintedType != null && !hintedType.isEmpty()) {
                        metadata.set("Content-Type", hintedType);
                    }
                } catch (Exception ignore) {
                    // Best-effort hint only
                }

                // Additional strong hints for formats that are commonly mis-detected
                String lowerName = filename.toLowerCase();
                if (lowerName.endsWith(".ttf")) {
                    metadata.set("Content-Type", "font/ttf");
                } else if (lowerName.endsWith(".otf")) {
                    metadata.set("Content-Type", "font/otf");
                } else if (lowerName.endsWith(".woff")) {
                    metadata.set("Content-Type", "font/woff");
                } else if (lowerName.endsWith(".woff2")) {
                    metadata.set("Content-Type", "font/woff2");
                }
            }
            
            try {
                parser.parse(stream, handler, metadata, parseContext);
            } catch (org.apache.commons.compress.archivers.ArchiveException ae) {
                // Some formats (e.g., fonts) can be misrouted into archive detection.
                // Retry using a config without DefaultZipContainerDetector.
                LOG.warnf(ae, "Archive detection failed; retrying without zip container detector");
                String noZipDetectorCfg = "<properties>\n" +
                        "  <detectors>\n" +
                        "    <detector class=\"org.apache.tika.detect.DefaultDetector\"/>\n" +
                        "  </detectors>\n" +
                        "</properties>";
                try (InputStream cfg = new ByteArrayInputStream(noZipDetectorCfg.getBytes());
                     InputStream retry = new ByteArrayInputStream(content.toByteArray())) {
                    TikaConfig tikaCfg = new TikaConfig(cfg);
                    Parser retryParser = new AutoDetectParser(tikaCfg);
                    ParseContext retryCtx = new ParseContext();
                    retryCtx.set(Parser.class, retryParser);
                    retryParser.parse(retry, handler, metadata, retryCtx);
                }
            }
        }

        // Post-process: Extract XMP Rights metadata if this is an image with XMP
        try {
            String mimeType = metadata.get(org.apache.tika.metadata.Metadata.CONTENT_TYPE);
            if (mimeType != null && mimeType.startsWith("image/")) {
                extractXMPRights(content, metadata);
            }
        } catch (Exception e) {
            LOG.debugf("Could not extract XMP Rights: %s", e.getMessage());
        }

        // Extract title and body
        String handlerContent = handler.toString();
        String title = extractTitle(metadata, handlerContent, configMap);
        String body = extractBody(handlerContent, metadata, content, configMap);
        
        // Debug logging to understand content extraction
        if (getBooleanConfig(configMap, "logParsingErrors", false)) {
            LOG.infof("Content extraction debug - handler content length: %d, cleaned body length: %d", 
                     handlerContent.length(), body.length());
            if (handlerContent.length() > 0 && body.isEmpty()) {
                LOG.warnf("Handler extracted %d chars but body is empty after cleanup. Raw content: '%s'", 
                         handlerContent.length(), handlerContent.length() > 200 ? handlerContent.substring(0, 200) + "..." : handlerContent);
            }
        }
        
        LOG.debugf("Parsed document - title: '%s', body length: %d, content type: %s", 
                  title, body.length(), metadata.get("Content-Type"));
        
        // Build the PipeDoc with SearchMetadata
        SearchMetadata.Builder searchMetadataBuilder = SearchMetadata.newBuilder()
                .setBody(body);
        
        if (title != null && !title.isEmpty()) {
            searchMetadataBuilder.setTitle(title);
        }
        
        PipeDoc.Builder docBuilder = PipeDoc.newBuilder()
                .setSearchMetadata(searchMetadataBuilder.build());
        
        // Add metadata if requested
        if (getBooleanConfig(configMap, "extractMetadata", true)) {
            Map<String, String> metadataMap = metadataMapper.toMap(metadata, configMap);
            if (!metadataMap.isEmpty()) {
                Struct.Builder structBuilder = Struct.newBuilder();
                for (Map.Entry<String, String> entry : metadataMap.entrySet()) {
                    structBuilder.putFields(entry.getKey(), Value.newBuilder().setStringValue(entry.getValue()).build());
                }
                // Store metadata in structured_data field using Any
                com.google.protobuf.Any metadataAny = com.google.protobuf.Any.pack(structBuilder.build());
                docBuilder.setStructuredData(metadataAny);
            }
        }
        
        PipeDoc parsedDoc = docBuilder.build();
        
        // Apply post-processing based on document type if title extraction is enabled
        if (getBooleanConfig(configMap, "enableTitleExtraction", true)) {
            parsedDoc = postProcessParsedDocument(parsedDoc, metadata, filename, configMap);
        }
        
        return parsedDoc;
    }
    
    /**
     * Convenience method that parses without filename.
     */
    public PipeDoc parseDocument(ByteString content, Map<String, String> configMap)
            throws IOException, SAXException, TikaException {
        return parseDocument(content, configMap, null);
    }
    
    /**
     * Creates the appropriate Tika parser based on configuration.
     * <p>
     * TODO: Fix EMF parser assertion errors - Currently some documents with embedded EMF images
     * (particularly older PowerPoint files) can cause AssertionError in Apache POI's EMF parser.
     * This is a known issue in POI/Tika. For now, we catch these errors in ParserServiceImpl
     * and return graceful failures. In the future, we should:
     * 1. Update to newer Tika version when the fix is available
     * 2. Consider always disabling EMF parser for problematic document types
     * 3. Implement custom EMF parser configuration based on document analysis
     */
    private Parser createParser(Map<String, String> configMap, String filename) {
        boolean disableEmfParser = shouldDisableEmfParserForFile(configMap, filename);
        boolean enableGeoTopicParser = getBooleanConfig(configMap, "enableGeoTopicParser", false);
        boolean isFont = filename != null && filename.toLowerCase().matches(".*\\.(ttf|otf|woff2?|pfa|pfb)$");
        boolean disableArchiveDetection = getBooleanConfig(configMap, "disableArchiveDetection", false);
        
        if (disableArchiveDetection || isFont) {
            LOG.infof("Routing fonts to TrueTypeParser to bypass container detection: %s", filename);
            try {
                return new org.apache.tika.parser.font.TrueTypeParser();
            } catch (Throwable t) {
                LOG.warnf(t, "Falling back to default parser for font due to missing font parser classes");
                return new AutoDetectParser();
            }
        } else if (disableEmfParser) {
            LOG.infof("Creating custom parser with EMF parser disabled for file: %s", filename);
            try {
                String customConfig = createCustomParserConfig();
                try (InputStream is = new ByteArrayInputStream(customConfig.getBytes())) {
                    TikaConfig tikaConfig = new TikaConfig(is);
                    return new AutoDetectParser(tikaConfig);
                }
            } catch (Exception e) {
                LOG.error("Failed to create custom parser configuration: {}", e.getMessage(), e);
                LOG.info("Falling back to default Tika configuration");
                return new AutoDetectParser();
            }
        } else if (enableGeoTopicParser) {
            LOG.info("Creating parser with GeoTopicParser enabled");
            try {
                String geoTopicConfig = createGeoTopicParserConfig();
                try (InputStream is = new ByteArrayInputStream(geoTopicConfig.getBytes())) {
                    TikaConfig tikaConfig = new TikaConfig(is);
                    return new AutoDetectParser(tikaConfig);
                }
            } catch (Exception e) {
                LOG.error("Failed to create GeoTopicParser configuration: {}", e.getMessage(), e);
                LOG.info("Falling back to default Tika configuration");
                return new AutoDetectParser();
            }
        } else {
            LOG.debug("Using default Tika configuration");
            return new AutoDetectParser();
        }
    }
    
    /**
     * Creates a content handler with appropriate limits.
     */
    private BodyContentHandler createContentHandler(Map<String, String> configMap) {
        // Default to 100MB limit for content extraction
        int maxContentLength = getIntConfig(configMap, "maxContentLength", 100 * 1024 * 1024);
        
        if (maxContentLength > 0) {
            // Use WriteOutContentHandler for better memory management with large documents
            return new BodyContentHandler(new WriteOutContentHandler(maxContentLength));
        } else {
            // Use -1 for unlimited content length
            return new BodyContentHandler(-1);
        }
    }
    
    /**
     * Extracts title from metadata with fallbacks.
     */
    private String extractTitle(Metadata metadata, String body, Map<String, String> configMap) {
        // Try various title metadata fields
        String title = cleanUpText(metadata.get("dc:title"));
        if (title == null || title.isEmpty()) {
            title = cleanUpText(metadata.get("title"));
        }
        if (title == null || title.isEmpty()) {
            title = cleanUpText(metadata.get("Title"));
        }
        
        // If still no title and body extraction is available, try to extract from first line
        if ((title == null || title.isEmpty()) && getBooleanConfig(configMap, "enableTitleExtraction", true) && body != null) {
            String[] lines = body.split("\n", 3);
            if (lines.length > 0) {
                String firstLine = cleanUpText(lines[0]);
                if (firstLine != null && firstLine.length() > 0 && firstLine.length() < 200) {
                    title = firstLine;
                }
            }
        }
        
        return title;
    }
    
    /**
     * Extracts body content with fallbacks.
     */
    private String extractBody(String handlerContent, Metadata metadata, ByteString originalContent, Map<String, String> configMap) {
        String body = cleanUpText(handlerContent);
        
        // If body is empty, try to get content from other metadata fields
        if (body.isEmpty()) {
            String contentFromMetadata = cleanUpText(metadata.get("content"));
            if (!contentFromMetadata.isEmpty()) {
                body = contentFromMetadata;
            }
        }
        
        // If still empty and it's a text file, use the content directly
        if (body.isEmpty() && metadata.get("Content-Type") != null && 
                metadata.get("Content-Type").startsWith("text/")) {
            body = new String(originalContent.toByteArray(), StandardCharsets.UTF_8);
            body = cleanUpText(body);
        }
        
        // If body is still empty, leave it blank - downstream modules can handle empty body
        if (body.isEmpty() && getBooleanConfig(configMap, "logParsingErrors", false)) {
            LOG.debug("No text content extracted from document. Body will be empty.");
        }
        
        return body;
    }
    
    /**
     * Post-processes a parsed document based on its content type.
     */
    private PipeDoc postProcessParsedDocument(PipeDoc parsedDoc, Metadata metadata, String filename, Map<String, String> configMap) {
        // If both title and body are non-empty, minimal post-processing needed
        if (!parsedDoc.getSearchMetadata().getTitle().isEmpty() && !parsedDoc.getSearchMetadata().getBody().isEmpty()) {
            return parsedDoc;
        }
        
        // Get the content type from metadata
        String contentType = metadata.get("Content-Type");
        if (contentType == null || contentType.isEmpty()) {
            // Try to infer content type from filename if available
            if (filename != null && getBooleanConfig(configMap, "fallbackToFilename", true)) {
                contentType = inferContentTypeFromFilename(filename);
            }
        }
        
        if (contentType != null) {
            LOG.debugf("Post-processing document with content type: %s", contentType);
            
            // Apply document type-specific processing
            PipeDoc.Builder builder = parsedDoc.toBuilder();
            
            if (contentType.startsWith("application/pdf")) {
                processPdfDocument(parsedDoc, builder, metadata);
            } else if (contentType.contains("presentation")) {
                processPresentationDocument(parsedDoc, builder, metadata);
            } else if (contentType.contains("wordprocessing") || contentType.contains("msword")) {
                processWordDocument(parsedDoc, builder, metadata);
            } else if (contentType.contains("spreadsheet") || contentType.contains("excel")) {
                processSpreadsheetDocument(parsedDoc, builder, metadata);
            } else if (contentType.startsWith("text/html")) {
                processHtmlDocument(parsedDoc, builder, metadata);
            }
            
            return builder.build();
        }
        
        return parsedDoc;
    }
    
    /**
     * Creates a custom Tika configuration XML that disables problematic parsers.
     */
    private String createCustomParserConfig()
            throws ParserConfigurationException, TransformerException {
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

        // Root element
        Document doc = docBuilder.newDocument();
        Element rootElement = doc.createElement("properties");
        doc.appendChild(rootElement);

        // Add parser options
        Element parsers = doc.createElement("parsers");
        rootElement.appendChild(parsers);

        // Disable EMF Parser
        Element emfParser = doc.createElement("parser");
        emfParser.setAttribute("class", "org.apache.tika.parser.microsoft.EMFParser");
        emfParser.setAttribute("enabled", "false");
        parsers.appendChild(emfParser);

        return transformDocumentToString(doc);
    }
    
    /**
     * Creates a Tika configuration XML with GeoTopicParser enabled.
     */
    private String createGeoTopicParserConfig()
            throws ParserConfigurationException, TransformerException {
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

        // Root element
        Document doc = docBuilder.newDocument();
        Element rootElement = doc.createElement("properties");
        doc.appendChild(rootElement);

        // Add parser options
        Element parsers = doc.createElement("parsers");
        rootElement.appendChild(parsers);

        // Add GeoTopicParser
        Element geoTopicParser = doc.createElement("parser");
        geoTopicParser.setAttribute("class", "org.apache.tika.parser.geo.topic.GeoTopicParser");
        geoTopicParser.setAttribute("enabled", "true");
        parsers.appendChild(geoTopicParser);

        return transformDocumentToString(doc);
    }
    
    /**
     * Transforms an XML Document to a string.
     */
    private String transformDocumentToString(Document doc) throws TransformerException {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }
    
    // Document type-specific processing methods (simplified versions)
    private void processPdfDocument(PipeDoc parsedDoc, PipeDoc.Builder builder, Metadata metadata) {
        if (parsedDoc.getSearchMetadata().getTitle().isEmpty()) {
            String title = metadata.get("pdf:docinfo:title");
            if (title != null && !title.isEmpty()) {
                SearchMetadata updatedMetadata = parsedDoc.getSearchMetadata().toBuilder()
                        .setTitle(cleanUpText(title))
                        .build();
                builder.setSearchMetadata(updatedMetadata);
            }
        }
    }
    
    private void processPresentationDocument(PipeDoc parsedDoc, PipeDoc.Builder builder, Metadata metadata) {
        if (parsedDoc.getSearchMetadata().getTitle().isEmpty()) {
            String title = metadata.get("dc:title");
            if (title == null || title.isEmpty()) {
                title = metadata.get("title");
            }
            if (title != null && !title.isEmpty()) {
                SearchMetadata updatedMetadata = parsedDoc.getSearchMetadata().toBuilder()
                        .setTitle(cleanUpText(title))
                        .build();
                builder.setSearchMetadata(updatedMetadata);
            }
        }
    }
    
    private void processWordDocument(PipeDoc parsedDoc, PipeDoc.Builder builder, Metadata metadata) {
        if (parsedDoc.getSearchMetadata().getTitle().isEmpty()) {
            String title = metadata.get("dc:title");
            if (title == null || title.isEmpty()) {
                title = metadata.get("title");
            }
            if (title != null && !title.isEmpty()) {
                SearchMetadata updatedMetadata = parsedDoc.getSearchMetadata().toBuilder()
                        .setTitle(cleanUpText(title))
                        .build();
                builder.setSearchMetadata(updatedMetadata);
            }
        }
    }
    
    private void processSpreadsheetDocument(PipeDoc parsedDoc, PipeDoc.Builder builder, Metadata metadata) {
        if (parsedDoc.getSearchMetadata().getTitle().isEmpty()) {
            String title = metadata.get("dc:title");
            if (title != null && !title.isEmpty()) {
                SearchMetadata updatedMetadata = parsedDoc.getSearchMetadata().toBuilder()
                        .setTitle(cleanUpText(title))
                        .build();
                builder.setSearchMetadata(updatedMetadata);
            }
        }
    }
    
    private void processHtmlDocument(PipeDoc parsedDoc, PipeDoc.Builder builder, Metadata metadata) {
        if (parsedDoc.getSearchMetadata().getTitle().isEmpty()) {
            String title = metadata.get("dc:title");
            if (title == null || title.isEmpty()) {
                title = metadata.get("title");
            }
            if (title != null && !title.isEmpty()) {
                SearchMetadata updatedMetadata = parsedDoc.getSearchMetadata().toBuilder()
                        .setTitle(cleanUpText(title))
                        .build();
                builder.setSearchMetadata(updatedMetadata);
            }
        }

        // Populate HTML outline if enabled and body present
        try {
            ai.pipestream.module.parser.config.ParserConfig cfg = ai.pipestream.module.parser.config.ParserConfig.defaultConfig();
            if (cfg.outlineExtraction() != null && Boolean.TRUE.equals(cfg.outlineExtraction().enableHtmlOutline())) {
                String body = parsedDoc.getSearchMetadata().getBody();
                if (body != null && !body.isEmpty()) {
                    DocOutline outline = ai.pipestream.module.parser.tika.builders.HtmlOutlineExtractor
                            .buildDocOutlineFromHtml(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    if (outline.getSectionsCount() > 0) {
                        SearchMetadata sm = parsedDoc.getSearchMetadata().toBuilder()
                                .setDocOutline(outline)
                                .build();
                        builder.setSearchMetadata(sm);
                    }
                }
            }
        } catch (Exception ignored) {}
    }
    
    /**
     * Infers content type from filename extension.
     */
    private String inferContentTypeFromFilename(String filename) {
        if (filename == null) {
            return "";
        }
        return TIKA.detect(filename);
    }
    
    /**
     * Cleans up extracted text by trimming whitespace and normalizing line breaks.
     */
    private String cleanUpText(String text) {
        if (text == null) {
            return "";
        }
        
        return text.trim()
                   .replaceAll("\\s+", " ")  // Replace multiple whitespace with single space
                   .replaceAll("\\n\\s*\\n", "\n\n");  // Normalize double line breaks
    }
    
    // Helper methods for configuration extraction
    
    /**
     * Gets an integer configuration value with a default.
     */
    private int getIntConfig(Map<String, String> configMap, String key, int defaultValue) {
        String value = configMap.get(key);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            LOG.warnf("Invalid integer value for config key '%s': %s, using default: %d", key, value, defaultValue);
            return defaultValue;
        }
    }
    
    /**
     * Gets a boolean configuration value with a default.
     */
    private boolean getBooleanConfig(Map<String, String> configMap, String key, boolean defaultValue) {
        String value = configMap.get(key);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }
    
    /**
     * Determines if EMF parser should be disabled for a specific file.
     * 
     * NOTE: EMF parser in Apache POI/Tika has known issues with certain PowerPoint and Word files
     * that contain embedded EMF graphics. This causes AssertionError in HemfPlusRecordIterator.
     * For production stability, we default to disabling EMF parser for potentially problematic files.
     */
    private boolean shouldDisableEmfParserForFile(Map<String, String> configMap, String filename) {
        // Check if EMF parser is explicitly disabled
        if (getBooleanConfig(configMap, "disableEmfParser", false)) {
            return true;
        }
        
        // Check if this file type should disable EMF parser
        if (filename != null) {
            String lowerFilename = filename.toLowerCase();
            
            // Disable EMF parser for problematic file types
            if (lowerFilename.endsWith(".emf") || 
                lowerFilename.endsWith(".wmf") ||
                lowerFilename.contains("corrupted")) {
                return true;
            }
            
            // CRITICAL: Disable EMF parser for older Office formats known to cause AssertionError
            // These formats often contain embedded EMF graphics that trigger POI bugs
            if (lowerFilename.endsWith(".ppt") ||     // PowerPoint 97-2003
                lowerFilename.endsWith(".doc")) {     // Word 97-2003  
                LOG.debugf("Disabling EMF parser for potentially problematic file: %s", filename);
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Converts ParserConfig record to Map<String, String> for compatibility with existing code.
     */
    private Map<String, String> convertConfigToMap(ParserConfig config) {
        Map<String, String> configMap = new TreeMap<>();
        
        // Parsing options
        if (config.parsingOptions() != null) {
            var options = config.parsingOptions();
            if (options.maxContentLength() != null) {
                configMap.put("maxContentLength", options.maxContentLength().toString());
            }
            if (options.extractMetadata() != null) {
                configMap.put("extractMetadata", options.extractMetadata().toString());
            }
            if (options.maxMetadataValueLength() != null) {
                configMap.put("maxMetadataValueLength", options.maxMetadataValueLength().toString());
            }
            if (options.parseTimeoutSeconds() != null) {
                configMap.put("parseTimeoutSeconds", options.parseTimeoutSeconds().toString());
            }
        }
        
        // Advanced options
        if (config.advancedOptions() != null) {
            var advanced = config.advancedOptions();
            if (advanced.enableGeoTopicParser() != null) {
                configMap.put("enableGeoTopicParser", advanced.enableGeoTopicParser().toString());
            }
            if (advanced.disableEmfParser() != null) {
                configMap.put("disableEmfParser", advanced.disableEmfParser().toString());
            }
            if (advanced.extractEmbeddedDocs() != null) {
                configMap.put("extractEmbeddedDocs", advanced.extractEmbeddedDocs().toString());  
            }
            if (advanced.maxRecursionDepth() != null) {
                configMap.put("maxRecursionDepth", advanced.maxRecursionDepth().toString());
            }
            if (advanced.enableOcrParser() != null) {
                configMap.put("enableOcrParser", advanced.enableOcrParser().toString());
            }
            if (advanced.enableScientificParser() != null) {
                configMap.put("enableScientificParser", advanced.enableScientificParser().toString());
            }
            if (advanced.enableGreedyBackoff() != null) {
                configMap.put("enableGreedyBackoff", advanced.enableGreedyBackoff().toString());
            }
        }
        
        // Content type handling
        if (config.contentTypeHandling() != null) {
            var contentType = config.contentTypeHandling();
            if (contentType.enableTitleExtraction() != null) {
                configMap.put("enableTitleExtraction", contentType.enableTitleExtraction().toString());
            }
            if (contentType.fallbackToFilename() != null) {
                configMap.put("fallbackToFilename", contentType.fallbackToFilename().toString());
            }
            if (contentType.supportedMimeTypes() != null && !contentType.supportedMimeTypes().isEmpty()) {
                configMap.put("supportedMimeTypes", String.join(",", contentType.supportedMimeTypes()));
            }
        }
        
        // Error handling
        if (config.errorHandling() != null) {
            var errorHandling = config.errorHandling();
            if (errorHandling.ignoreTikaException() != null) {
                configMap.put("ignoreTikaException", errorHandling.ignoreTikaException().toString());
            }
            if (errorHandling.fallbackToPlainText() != null) {
                configMap.put("fallbackToPlainText", errorHandling.fallbackToPlainText().toString());
            }
            if (errorHandling.logParsingErrors() != null) {
                configMap.put("logParsingErrors", errorHandling.logParsingErrors().toString());
            }
        }
        
        LOG.debugf("Converted ParserConfig to Map with %d entries", configMap.size());
        return configMap;
    }

    /**
     * Retrieves a set of all MIME types supported by the default Tika configuration.
     *
     * @return A Set of strings, where each string is a supported MIME type (e.g., "application/pdf").
     */
    public static Set<String> getSupportedMimeTypes() {
        // Get the default Tika configuration which contains the media type registry
        TikaConfig config = TikaConfig.getDefaultConfig();

        // Get the registry and then get all the media types from it
        Set<MediaType> mediaTypes = config.getMediaTypeRegistry().getTypes();

        // Convert the Set<MediaType> to a Set<String> for easier use
        return mediaTypes.stream()
                         .map(MediaType::toString)
                         .collect(Collectors.toSet());
    }

    /**
     * Public wrapper for XMP Rights extraction - allows other components to use this functionality.
     *
     * @param content The image file content
     * @param metadata The Tika metadata to populate
     */
    public void extractXMPRightsPublic(com.google.protobuf.ByteString content, Metadata metadata) {
        extractXMPRights(content, metadata);
    }

    /**
     * Extracts XMP Rights metadata from image content.
     * This is a post-processing step that reads XMP data using Adobe XMPCore
     * to extract Creative Commons and Rights metadata that Tika's standard parsers miss.
     *
     * @param content The image file content
     * @param metadata The Tika metadata to populate
     */
    private void extractXMPRights(ByteString content, Metadata metadata) {
        LOG.debugf("Attempting to extract XMP Rights from image content (%d bytes)", content.size());
        try (ByteArrayInputStream bis = new ByteArrayInputStream(content.toByteArray())) {
            // Use ImageMetadataExtractor which knows how to extract XMP from image formats
            byte[] xmpPacket = extractXMPPacket(bis);
            if (xmpPacket == null || xmpPacket.length == 0) {
                LOG.tracef("No XMP packet found in image");
                return;
            }

            LOG.debugf("Found XMP packet (%d bytes), parsing for Rights metadata", xmpPacket.length);

            // Parse the XMP packet with Adobe XMPCore
            try (ByteArrayInputStream xmpStream = new ByteArrayInputStream(xmpPacket)) {
                com.adobe.internal.xmp.XMPMeta xmpMeta =
                    com.adobe.internal.xmp.XMPMetaFactory.parse(xmpStream);

                // Extract XMP Rights properties
                extractXMPRightsFromAdobe(xmpMeta, metadata);
            }
        } catch (Exception e) {
            LOG.debugf("Could not extract XMP Rights: %s - %s", e.getClass().getSimpleName(), e.getMessage());
        }
    }

    /**
     * Extract the raw XMP packet from an image stream
     */
    private byte[] extractXMPPacket(InputStream imageStream) throws IOException {
        // Use Tika's XMP packet scanner
        org.apache.tika.parser.xmp.XMPPacketScanner scanner =
            new org.apache.tika.parser.xmp.XMPPacketScanner();

        try (java.io.ByteArrayOutputStream xmpOut = new java.io.ByteArrayOutputStream()) {
            boolean found = scanner.parse(imageStream, xmpOut);
            if (found && xmpOut.size() > 0) {
                return xmpOut.toByteArray();
            }
            return null;
        } catch (Exception e) {
            LOG.tracef("Error scanning for XMP packet: %s", e.getMessage());
            return null;
        }
    }

    /**
     * Extract XMP Rights properties using Adobe XMPCore library
     */
    private void extractXMPRightsFromAdobe(com.adobe.internal.xmp.XMPMeta xmpMeta, Metadata metadata) {
        if (xmpMeta == null) {
            return;
        }

        try {
            String rightsNS = "http://ns.adobe.com/xap/1.0/rights/";

            // Extract WebStatement
            try {
                com.adobe.internal.xmp.properties.XMPProperty prop =
                    xmpMeta.getProperty(rightsNS, "WebStatement");
                if (prop != null && prop.getValue() != null) {
                    metadata.set(XMPRights.WEB_STATEMENT, prop.getValue());
                    LOG.infof("Extracted WebStatement: %s", prop.getValue());
                } else {
                    LOG.debugf("WebStatement property was null or had no value");
                }
            } catch (Exception e) {
                LOG.tracef("No WebStatement: %s", e.getMessage());
            }

            // Extract UsageTerms
            try {
                com.adobe.internal.xmp.properties.XMPProperty prop =
                    xmpMeta.getProperty(rightsNS, "UsageTerms");
                if (prop != null && prop.getValue() != null) {
                    metadata.set(XMPRights.USAGE_TERMS, prop.getValue());
                    LOG.debugf("Extracted UsageTerms: %s", prop.getValue());
                }
            } catch (Exception e) {
                LOG.tracef("No UsageTerms: %s", e.getMessage());
            }

            // Extract Marked
            try {
                com.adobe.internal.xmp.properties.XMPProperty prop =
                    xmpMeta.getProperty(rightsNS, "Marked");
                if (prop != null && prop.getValue() != null) {
                    metadata.set(XMPRights.MARKED, prop.getValue());
                    LOG.debugf("Extracted Marked: %s", prop.getValue());
                }
            } catch (Exception e) {
                LOG.tracef("No Marked: %s", e.getMessage());
            }

            // Extract Certificate
            try {
                com.adobe.internal.xmp.properties.XMPProperty prop =
                    xmpMeta.getProperty(rightsNS, "Certificate");
                if (prop != null && prop.getValue() != null) {
                    metadata.set(XMPRights.CERTIFICATE, prop.getValue());
                    LOG.debugf("Extracted Certificate: %s", prop.getValue());
                }
            } catch (Exception e) {
                LOG.tracef("No Certificate: %s", e.getMessage());
            }

            // Extract Owner (array property)
            try {
                int count = xmpMeta.countArrayItems(rightsNS, "Owner");
                for (int i = 1; i <= count; i++) {
                    com.adobe.internal.xmp.properties.XMPProperty prop =
                        xmpMeta.getArrayItem(rightsNS, "Owner", i);
                    if (prop != null && prop.getValue() != null) {
                        metadata.add(XMPRights.OWNER.getName(), prop.getValue());
                        LOG.debugf("Extracted Owner: %s", prop.getValue());
                    }
                }
            } catch (Exception e) {
                LOG.tracef("No Owner array: %s", e.getMessage());
            }

            LOG.infof("Successfully extracted XMP Rights metadata from image");
        } catch (Exception e) {
            LOG.debugf("Error extracting XMP Rights: %s", e.getMessage());
        }
    }
}