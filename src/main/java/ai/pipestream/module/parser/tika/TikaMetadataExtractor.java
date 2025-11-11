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
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.DublinCore;
import org.jboss.logging.Logger;

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
        DublinCoreMetadata dublinCore = buildDublinCoreMetadata(tikaMetadata);
        responseBuilder.setDublinCore(dublinCore);
        
        // Get Tika version
        String tikaVersion = MetadataUtils.getTikaVersion();
        
        // Route to appropriate metadata builder based on document type
        switch (docType) {
            case PDF:
                responseBuilder.setPdf(PdfMetadataBuilder.build(tikaMetadata, parserClass, tikaVersion));
                break;
                
            case OFFICE:
                responseBuilder.setOffice(OfficeMetadataBuilder.build(tikaMetadata, parserClass, tikaVersion));
                break;
                
            case IMAGE:
                responseBuilder.setImage(ImageMetadataBuilder.build(tikaMetadata, parserClass, tikaVersion));
                break;
                
            case EMAIL:
                responseBuilder.setEmail(EmailMetadataBuilder.build(tikaMetadata, parserClass, tikaVersion));
                break;
                
            case MEDIA:
                responseBuilder.setMedia(MediaMetadataBuilder.build(tikaMetadata, parserClass, tikaVersion));
                break;
                
            case HTML:
                responseBuilder.setHtml(HtmlMetadataBuilder.build(tikaMetadata, parserClass, tikaVersion));
                break;
                
            case RTF:
                responseBuilder.setRtf(RtfMetadataBuilder.build(tikaMetadata, parserClass, tikaVersion));
                break;
                
            case DATABASE:
                responseBuilder.setDatabase(ai.pipestream.module.parser.tika.builders.DatabaseMetadataBuilder
                        .build(tikaMetadata, parserClass, tikaVersion));
                break;
                
            case FONT:
                responseBuilder.setFont(FontMetadataBuilder.build(tikaMetadata, parserClass, tikaVersion));
                break;
                
            case EPUB:
                responseBuilder.setEpub(EpubMetadataBuilder.build(tikaMetadata, parserClass, tikaVersion));
                break;
                
            case WARC:
                responseBuilder.setWarc(WarcMetadataBuilder.build(tikaMetadata, parserClass, tikaVersion));
                break;
                
            case CLIMATE_FORECAST:
                responseBuilder.setClimateForecast(ai.pipestream.module.parser.tika.builders.ClimateForecastMetadataBuilder
                        .build(tikaMetadata, parserClass, tikaVersion));
                break;
                
            case CREATIVE_COMMONS:
                responseBuilder.setCreativeCommons(CreativeCommonsMetadataBuilder.build(tikaMetadata, parserClass, tikaVersion));
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
                    responseBuilder.setCreativeCommons(CreativeCommonsMetadataBuilder.build(tikaMetadata, parserClass, tikaVersion));
                }
            }
        } catch (Exception ignored) {}

        TikaResponse response = responseBuilder.build();
        LOG.debugf("Built comprehensive metadata response for document %s with %d total metadata fields", 
                  docId, tikaMetadata.names().length);
        
        return response;
    }

    private static boolean hasXmpRights(org.apache.tika.metadata.Metadata md) {
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
     */
    private static DublinCoreMetadata buildDublinCoreMetadata(Metadata tikaMetadata) {
        DublinCoreMetadata.Builder builder = DublinCoreMetadata.newBuilder();
        
        // Map Dublin Core fields
        String title = tikaMetadata.get(DublinCore.TITLE);
        if (title != null && !title.trim().isEmpty()) {
            builder.setTitle(title.trim());
        }
        
        String creator = tikaMetadata.get(DublinCore.CREATOR);
        if (creator != null && !creator.trim().isEmpty()) {
            builder.addCreators(creator.trim());
        }
        
        String subject = tikaMetadata.get(DublinCore.SUBJECT);
        if (subject != null && !subject.trim().isEmpty()) {
            builder.addSubjects(subject.trim());
        }
        
        String description = tikaMetadata.get(DublinCore.DESCRIPTION);
        if (description != null && !description.trim().isEmpty()) {
            builder.setDescription(description.trim());
        }
        
        String publisher = tikaMetadata.get(DublinCore.PUBLISHER);
        if (publisher != null && !publisher.trim().isEmpty()) {
            builder.setPublisher(publisher.trim());
        }
        
        String contributor = tikaMetadata.get(DublinCore.CONTRIBUTOR);
        if (contributor != null && !contributor.trim().isEmpty()) {
            builder.addContributors(contributor.trim());
        }
        
        String type = tikaMetadata.get(DublinCore.TYPE);
        if (type != null && !type.trim().isEmpty()) {
            builder.setType(type.trim());
        }
        
        String format = tikaMetadata.get(DublinCore.FORMAT);
        if (format != null && !format.trim().isEmpty()) {
            builder.setFormat(format.trim());
        }
        
        String identifier = tikaMetadata.get(DublinCore.IDENTIFIER);
        if (identifier != null && !identifier.trim().isEmpty()) {
            builder.setIdentifier(identifier.trim());
        }
        
        String source = tikaMetadata.get(DublinCore.SOURCE);
        if (source != null && !source.trim().isEmpty()) {
            builder.setSource(source.trim());
        }
        
        String language = tikaMetadata.get(DublinCore.LANGUAGE);
        if (language != null && !language.trim().isEmpty()) {
            builder.setLanguage(language.trim());
        }
        
        String relation = tikaMetadata.get(DublinCore.RELATION);
        if (relation != null && !relation.trim().isEmpty()) {
            builder.setRelation(relation.trim());
        }
        
        String coverage = tikaMetadata.get(DublinCore.COVERAGE);
        if (coverage != null && !coverage.trim().isEmpty()) {
            builder.setCoverage(coverage.trim());
        }
        
        String rights = tikaMetadata.get(DublinCore.RIGHTS);
        if (rights != null && !rights.trim().isEmpty()) {
            builder.setRights(rights.trim());
        }
        
        // Handle date fields
        try {
            java.util.Date date = tikaMetadata.getDate(DublinCore.DATE);
            if (date != null) {
                java.time.Instant instant = date.toInstant();
                com.google.protobuf.Timestamp timestamp = com.google.protobuf.Timestamp.newBuilder()
                        .setSeconds(instant.getEpochSecond())
                        .setNanos(instant.getNano())
                        .build();
                builder.setDate(timestamp);
            }
        } catch (Exception e) {
            LOG.debugf("Could not parse Dublin Core date: %s", e.getMessage());
        }
        
        return builder.build();
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
