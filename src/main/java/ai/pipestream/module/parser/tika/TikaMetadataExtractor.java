package ai.pipestream.module.parser.tika;

import ai.pipestream.module.parser.tika.builders.DocumentTypeDetector;
import ai.pipestream.module.parser.tika.builders.MetadataUtils;
import ai.pipestream.module.parser.tika.builders.PdfMetadataBuilder;
import ai.pipestream.module.parser.tika.builders.OfficeMetadataBuilder;
import ai.pipestream.module.parser.tika.builders.RtfMetadataBuilder;
import ai.pipestream.module.parser.tika.builders.EpubMetadataBuilder;
import ai.pipestream.module.parser.tika.builders.ImageMetadataBuilder;
import ai.pipestream.module.parser.tika.builders.EmailMetadataBuilder;
import ai.pipestream.module.parser.tika.builders.MediaMetadataBuilder;
import ai.pipestream.module.parser.tika.builders.HtmlMetadataBuilder;
import ai.pipestream.module.parser.tika.builders.WarcMetadataBuilder;
import ai.pipestream.module.parser.tika.builders.CreativeCommonsMetadataBuilder;
import ai.pipestream.module.parser.tika.builders.FontMetadataBuilder;
import ai.pipestream.parsed.data.dublin.v1.DublinCoreMetadata;
import ai.pipestream.parsed.data.tika.v1.TikaResponse;
import ai.pipestream.parsed.data.tika.v1.TikaContent;
import ai.pipestream.shaded.tika.metadata.Metadata;
import ai.pipestream.shaded.tika.metadata.DublinCore;
import org.jboss.logging.Logger;

import java.util.HashSet;
import java.util.Set;

/**
 * Main orchestrator for extracting comprehensive metadata from Tika Metadata objects.
 * 
 * This class:
 * 1. Detects document type from Tika metadata
 * 2. Routes to appropriate metadata builder
 * 3. Builds Dublin Core metadata
 * 4. Assembles complete TikaResponse with oneof document_metadata
 * 
 * Follows the principle: "Whatever Tika extracts, we save - strongly-typed if we recognize it, struct if we don't."
 */
public class TikaMetadataExtractor {
    
    private static final Logger LOG = Logger.getLogger(TikaMetadataExtractor.class);
    
    /**
     * Extracts comprehensive metadata from Tika Metadata object.
     * 
     * @param tikaMetadata The Tika metadata extracted from document
     * @param parserClass The Tika parser class name used
     * @param extractedText The text content extracted by Tika
     * @param docId The document ID
     * @return Complete TikaResponse with strongly-typed metadata and flexible struct data
     */
    public static TikaResponse extractComprehensiveMetadata(
            Metadata tikaMetadata, 
            String parserClass, 
            String extractedText,
            String docId) {
        
        LOG.debugf("Extracting comprehensive metadata for document %s using parser %s", docId, parserClass);
        
        // Detect document type
        DocumentTypeDetector.DocumentType docType = DocumentTypeDetector.detect(tikaMetadata);
        LOG.debugf("Detected document type: %s", docType);
        
        // Build TikaResponse
        TikaResponse.Builder responseBuilder = TikaResponse.newBuilder();
        
        // Set document ID
        if (docId != null && !docId.isEmpty()) {
            responseBuilder.setDocId(docId);
        }
        
        // Build content
        TikaContent.Builder contentBuilder = TikaContent.newBuilder();
        if (extractedText != null && !extractedText.isEmpty()) {
            contentBuilder.setBody(extractedText);
        }
        
        // Add content type and other content metadata
        // Content-Type is captured in typed metadata or additional metadata; TikaContent has no content_type field
        
        String contentLength = tikaMetadata.get("Content-Length");
        if (contentLength != null && !contentLength.isEmpty()) {
            try {
                long length = Long.parseLong(contentLength);
                contentBuilder.setContentLength(length);
            } catch (NumberFormatException e) {
                LOG.warnf("Failed to parse content length: %s", contentLength);
            }
        }
        
        responseBuilder.setContent(contentBuilder.build());
        
        // Build Dublin Core metadata (common to all document types)
        // Collect consumed keys so document-type builders can exclude them from additional_metadata
        Set<String> dublinCoreKeys = new HashSet<>();
        DublinCoreMetadata dublinCore = buildDublinCoreMetadata(tikaMetadata, dublinCoreKeys);
        responseBuilder.setDublinCore(dublinCore);

        // Also exclude XMP-sourced Dublin Core duplicates and x-default variants
        collectDublinCoreRelatedKeys(tikaMetadata, dublinCoreKeys);

        // Exclude Tika internal fields common to all document types
        collectTikaInternalKeys(tikaMetadata, dublinCoreKeys);

        // Get Tika version
        String tikaVersion = MetadataUtils.getTikaVersion();

        // Route to appropriate metadata builder based on document type
        switch (docType) {
            case PDF:
                responseBuilder.setPdf(PdfMetadataBuilder.build(tikaMetadata, parserClass, tikaVersion, dublinCoreKeys));
                break;
                
            case OFFICE:
                responseBuilder.setOffice(OfficeMetadataBuilder.build(tikaMetadata, parserClass, tikaVersion, dublinCoreKeys));
                break;

            case IMAGE:
                responseBuilder.setImage(ImageMetadataBuilder.build(tikaMetadata, parserClass, tikaVersion, dublinCoreKeys));
                break;

            case EMAIL:
                responseBuilder.setEmail(EmailMetadataBuilder.build(tikaMetadata, parserClass, tikaVersion, dublinCoreKeys));
                break;

            case MEDIA:
                responseBuilder.setMedia(MediaMetadataBuilder.build(tikaMetadata, parserClass, tikaVersion, dublinCoreKeys));
                break;

            case HTML:
                responseBuilder.setHtml(HtmlMetadataBuilder.build(tikaMetadata, parserClass, tikaVersion, dublinCoreKeys));
                break;

            case RTF:
                responseBuilder.setRtf(RtfMetadataBuilder.build(tikaMetadata, parserClass, tikaVersion, dublinCoreKeys));
                break;

            case DATABASE:
                responseBuilder.setDatabase(ai.pipestream.module.parser.tika.builders.DatabaseMetadataBuilder
                        .build(tikaMetadata, parserClass, tikaVersion, dublinCoreKeys));
                break;

            case FONT:
                responseBuilder.setFont(FontMetadataBuilder.build(tikaMetadata, parserClass, tikaVersion, dublinCoreKeys));
                break;

            case EPUB:
                responseBuilder.setEpub(EpubMetadataBuilder.build(tikaMetadata, parserClass, tikaVersion, dublinCoreKeys));
                break;

            case WARC:
                responseBuilder.setWarc(WarcMetadataBuilder.build(tikaMetadata, parserClass, tikaVersion, dublinCoreKeys));
                break;

            case CLIMATE_FORECAST:
                responseBuilder.setClimateForecast(ai.pipestream.module.parser.tika.builders.ClimateForecastMetadataBuilder
                        .build(tikaMetadata, parserClass, tikaVersion, dublinCoreKeys));
                break;

            case CREATIVE_COMMONS:
                responseBuilder.setCreativeCommons(CreativeCommonsMetadataBuilder.build(tikaMetadata, parserClass, tikaVersion, dublinCoreKeys));
                break;

            case GENERIC:
            default:
                responseBuilder.setGeneric(buildGenericMetadata(tikaMetadata, parserClass, tikaVersion));
                break;
        }
        
        // Overlay: attach Creative Commons metadata when present, regardless of primary type
        try {
            if (DocumentTypeDetector.detect(tikaMetadata) != DocumentTypeDetector.DocumentType.CREATIVE_COMMONS) {
                if (hasXmpRights(tikaMetadata)) {
                    responseBuilder.setCreativeCommons(CreativeCommonsMetadataBuilder.build(tikaMetadata, parserClass, tikaVersion, dublinCoreKeys));
                }
            }
        } catch (Exception ignored) {}

        TikaResponse response = responseBuilder.build();
        LOG.debugf("Built comprehensive metadata response for document %s with %d total metadata fields", 
                  docId, tikaMetadata.names().length);
        
        return response;
    }

    private static boolean hasXmpRights(ai.pipestream.shaded.tika.metadata.Metadata md) {
        String[] names = md.names();
        for (String n : names) {
            String ln = n.toLowerCase();
            if (ln.contains("xmprights") || ln.contains("xmp-rights") || ln.contains(":rights") || ln.contains("xmp.rights")) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Builds Dublin Core metadata from Tika metadata.
     * Populates consumedKeys with the Tika metadata key names that were consumed,
     * so document-type builders can exclude them from additional_metadata.
     */
    private static DublinCoreMetadata buildDublinCoreMetadata(Metadata tikaMetadata, Set<String> consumedKeys) {
        DublinCoreMetadata.Builder builder = DublinCoreMetadata.newBuilder();

        mapDcField(tikaMetadata, DublinCore.TITLE, builder::setTitle, consumedKeys);
        mapDcRepeatedField(tikaMetadata, DublinCore.CREATOR, builder::addCreators, consumedKeys);
        mapDcRepeatedField(tikaMetadata, DublinCore.SUBJECT, builder::addSubjects, consumedKeys);
        mapDcField(tikaMetadata, DublinCore.DESCRIPTION, builder::setDescription, consumedKeys);
        mapDcField(tikaMetadata, DublinCore.PUBLISHER, builder::setPublisher, consumedKeys);
        mapDcRepeatedField(tikaMetadata, DublinCore.CONTRIBUTOR, builder::addContributors, consumedKeys);
        mapDcField(tikaMetadata, DublinCore.TYPE, builder::setType, consumedKeys);
        mapDcField(tikaMetadata, DublinCore.FORMAT, builder::setFormat, consumedKeys);
        mapDcField(tikaMetadata, DublinCore.IDENTIFIER, builder::setIdentifier, consumedKeys);
        mapDcField(tikaMetadata, DublinCore.SOURCE, builder::setSource, consumedKeys);
        mapDcField(tikaMetadata, DublinCore.LANGUAGE, builder::setLanguage, consumedKeys);
        mapDcField(tikaMetadata, DublinCore.RELATION, builder::setRelation, consumedKeys);
        mapDcField(tikaMetadata, DublinCore.COVERAGE, builder::setCoverage, consumedKeys);
        mapDcField(tikaMetadata, DublinCore.RIGHTS, builder::setRights, consumedKeys);

        // Date fields
        try {
            java.util.Date created = tikaMetadata.getDate(DublinCore.CREATED);
            if (created != null) {
                builder.setCreated(toTimestamp(created));
                consumedKeys.add(DublinCore.CREATED.getName());
            }
        } catch (Exception e) {
            LOG.debugf("Could not parse Dublin Core created date: %s", e.getMessage());
        }

        try {
            java.util.Date modified = tikaMetadata.getDate(DublinCore.MODIFIED);
            if (modified != null) {
                builder.setModified(toTimestamp(modified));
                consumedKeys.add(DublinCore.MODIFIED.getName());
            }
        } catch (Exception e) {
            LOG.debugf("Could not parse Dublin Core modified date: %s", e.getMessage());
        }

        try {
            java.util.Date date = tikaMetadata.getDate(DublinCore.DATE);
            if (date != null) {
                builder.setDate(toTimestamp(date));
                consumedKeys.add(DublinCore.DATE.getName());
            }
        } catch (Exception e) {
            LOG.debugf("Could not parse Dublin Core date: %s", e.getMessage());
        }

        return builder.build();
    }

    /**
     * Collects all Dublin Core related keys from Tika metadata, including
     * XMP-sourced duplicates (xmp:dc:*) and x-default locale variants (*:x-default).
     * These are all representations of the same Dublin Core data and should be
     * excluded from document-type builder additional_metadata.
     */
    private static void collectDublinCoreRelatedKeys(Metadata tikaMetadata, Set<String> keys) {
        for (String name : tikaMetadata.names()) {
            // dc:* and dcterms:* (normalized Dublin Core)
            if (name.startsWith("dc:") || name.startsWith("dcterms:")) {
                keys.add(name);
            }
            // xmp:dc:* (XMP-sourced Dublin Core duplicates)
            else if (name.startsWith("xmp:dc:")) {
                keys.add(name);
            }
            // meta:keyword is a Tika alias for dc:subject
            else if (name.equals("meta:keyword")) {
                keys.add(name);
            }
        }
    }

    /**
     * Collects Tika internal processing metadata keys that are common to ALL document types.
     * These are not document metadata — they describe Tika's parsing behavior.
     * They're already captured in TikaBaseFields.raw_metadata, so excluding them from
     * document-type additional_metadata prevents duplication.
     */
    private static void collectTikaInternalKeys(Metadata tikaMetadata, Set<String> keys) {
        for (String name : tikaMetadata.names()) {
            // X-TIKA:* fields (Parsed-By, Parsed-By-Full-Set, versionCount, etc.)
            if (name.startsWith("X-TIKA:")) {
                keys.add(name);
            }
            // Content-Type-Magic-Detected — Tika's magic detection
            else if (name.equals("Content-Type-Magic-Detected")) {
                keys.add(name);
            }
            // resourceName — the original filename passed to Tika
            else if (name.equals("resourceName")) {
                keys.add(name);
            }
            // zip:detectorZipFileOpened — Tika ZIP detector internal
            else if (name.startsWith("zip:")) {
                keys.add(name);
            }
        }
    }

    private static void mapDcField(Metadata metadata, ai.pipestream.shaded.tika.metadata.Property prop,
                                    java.util.function.Consumer<String> setter, Set<String> consumedKeys) {
        String key = prop.getName();
        String value = metadata.get(key);
        if (value != null && !value.trim().isEmpty()) {
            setter.accept(value.trim());
            consumedKeys.add(key);
        }
    }

    private static void mapDcRepeatedField(Metadata metadata, ai.pipestream.shaded.tika.metadata.Property prop,
                                            java.util.function.Consumer<String> adder, Set<String> consumedKeys) {
        String key = prop.getName();
        String[] values = metadata.getValues(key);
        if (values != null) {
            for (String v : values) {
                if (v != null && !v.trim().isEmpty()) {
                    adder.accept(v.trim());
                }
            }
            if (values.length > 0) {
                consumedKeys.add(key);
            }
        }
    }

    private static com.google.protobuf.Timestamp toTimestamp(java.util.Date date) {
        java.time.Instant instant = date.toInstant();
        return com.google.protobuf.Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }
    
    /**
     * Builds generic metadata as fallback.
     * TODO: Replace with proper GenericMetadataBuilder when implemented.
     */
    private static ai.pipestream.parsed.data.generic.v1.GenericMetadata buildGenericMetadata(
            Metadata tikaMetadata, String parserClass, String tikaVersion) {
        
        ai.pipestream.parsed.data.generic.v1.GenericMetadata.Builder builder = 
                ai.pipestream.parsed.data.generic.v1.GenericMetadata.newBuilder();
        
        // Basic identification
        String mimeType = tikaMetadata.get("Content-Type");
        if (mimeType != null && !mimeType.trim().isEmpty()) {
            builder.setDetectedMimeType(mimeType.trim());
        }
        
        String resourceName = tikaMetadata.get("resourceName");
        if (resourceName != null && !resourceName.trim().isEmpty()) {
            // Extract file extension
            int lastDot = resourceName.lastIndexOf('.');
            if (lastDot > 0 && lastDot < resourceName.length() - 1) {
                builder.setFileExtension(resourceName.substring(lastDot + 1));
            }
        }
        
        if (parserClass != null && !parserClass.trim().isEmpty()) {
            builder.setTikaParserClass(parserClass.trim());
        }
        
        // Put all metadata in the flexible struct
        com.google.protobuf.Struct allMetadata = MetadataUtils.buildAdditionalMetadata(tikaMetadata, new java.util.HashSet<>());
        builder.setAllMetadata(allMetadata);
        
        // Build base fields
        ai.pipestream.parsed.data.tika.base.v1.TikaBaseFields baseFields = 
                MetadataUtils.buildBaseFields(parserClass, tikaVersion, tikaMetadata);
        builder.setBaseFields(baseFields);
        
        return builder.build();
    }
}
