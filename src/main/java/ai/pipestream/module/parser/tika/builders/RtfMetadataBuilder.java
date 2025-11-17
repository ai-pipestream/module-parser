package ai.pipestream.module.parser.tika.builders;

import com.google.protobuf.Struct;
import ai.pipestream.parsed.data.rtf.v1.RtfMetadata;
import ai.pipestream.parsed.data.tika.base.v1.TikaBaseFields;
import ai.pipestream.shaded.tika.metadata.Metadata;
import ai.pipestream.shaded.tika.metadata.Office;
import ai.pipestream.shaded.tika.metadata.OfficeOpenXMLCore;
import ai.pipestream.shaded.tika.metadata.OfficeOpenXMLExtended;
import ai.pipestream.shaded.tika.metadata.RTFMetadata;
import ai.pipestream.shaded.tika.metadata.TikaCoreProperties;

import java.util.HashSet;
import java.util.Set;

/**
 * Builds RtfMetadata protobuf from Tika Metadata using verified mappings.
 */
public class RtfMetadataBuilder {

    public static RtfMetadata build(Metadata tikaMetadata, String parserClass, String tikaVersion) {
        RtfMetadata.Builder builder = RtfMetadata.newBuilder();
        Set<String> mapped = new HashSet<>();

        mapOfficeLikeFields(tikaMetadata, builder, mapped);
        mapRtfSpecificFields(tikaMetadata, builder, mapped);
        mapContentAndResourceFields(tikaMetadata, builder, mapped);
        mapCountsAndStats(tikaMetadata, builder, mapped);
        mapSecurityAndRevision(tikaMetadata, builder, mapped);

        // Additional metadata for anything unmapped
        Struct additional = MetadataUtils.buildAdditionalMetadata(tikaMetadata, mapped);
        builder.setAdditionalMetadata(additional);

        // Base fields
        TikaBaseFields base = MetadataUtils.buildBaseFields(parserClass, tikaVersion, tikaMetadata);
        builder.setBaseFields(base);

        return builder.build();
    }

    private static void mapOfficeLikeFields(Metadata md, RtfMetadata.Builder b, Set<String> mapped) {
        // Keywords in RTF proto is a single string; Office.KEYWORDS is a bag. Join with commas.
        String[] kws = md.getValues(Office.KEYWORDS.getName());
        if (kws != null && kws.length > 0) {
            String joined = String.join(", ", java.util.Arrays.stream(kws)
                    .filter(v -> v != null && !v.trim().isEmpty())
                    .map(String::trim)
                    .toArray(String[]::new));
            if (!joined.isEmpty()) {
                b.setKeywords(joined);
                mapped.add(Office.KEYWORDS.getName());
            }
        }

        MetadataUtils.mapStringField(md, OfficeOpenXMLCore.CATEGORY, b::setCategory, mapped);
        // Manager is a bag in OOXML extended; RTF proto has single string
        String[] mgrs = md.getValues(OfficeOpenXMLExtended.MANAGER.getName());
        if (mgrs != null && mgrs.length > 0) {
            String joined = String.join(", ", java.util.Arrays.stream(mgrs)
                    .filter(v -> v != null && !v.trim().isEmpty())
                    .map(String::trim)
                    .toArray(String[]::new));
            if (!joined.isEmpty()) {
                b.setManager(joined);
                mapped.add(OfficeOpenXMLExtended.MANAGER.getName());
            }
        }
        MetadataUtils.mapStringField(md, OfficeOpenXMLExtended.COMPANY, b::setCompany, mapped);
        MetadataUtils.mapStringField(md, OfficeOpenXMLExtended.TEMPLATE, b::setTemplate, mapped);
    }

    private static void mapCountsAndStats(Metadata md, RtfMetadata.Builder b, Set<String> mapped) {
        MetadataUtils.mapIntField(md, Office.PAGE_COUNT, b::setPageCount, mapped);
        MetadataUtils.mapIntField(md, Office.WORD_COUNT, b::setWordCount, mapped);
        MetadataUtils.mapIntField(md, Office.CHARACTER_COUNT, b::setCharacterCount, mapped);
    }

    private static void mapRtfSpecificFields(Metadata md, RtfMetadata.Builder b, Set<String> mapped) {
        MetadataUtils.mapBooleanField(md, RTFMetadata.CONTAINS_ENCAPSULATED_HTML, b::setContainsEncapsulatedHtml, mapped);
        MetadataUtils.mapBooleanField(md, RTFMetadata.THUMBNAIL, b::setThumbnail, mapped);
        MetadataUtils.mapStringField(md, RTFMetadata.EMB_APP_VERSION, b::setEmbAppVersion, mapped);
        MetadataUtils.mapStringField(md, RTFMetadata.EMB_CLASS, b::setEmbClass, mapped);
        MetadataUtils.mapStringField(md, RTFMetadata.EMB_TOPIC, b::setEmbTopic, mapped);
        MetadataUtils.mapStringField(md, RTFMetadata.EMB_ITEM, b::setEmbItem, mapped);

        // Picture metadata prefix is a literal key in some cases; capture prefix if set
        String pictPrefix = md.get(RTFMetadata.RTF_PICT_META_PREFIX);
        if (pictPrefix != null && !pictPrefix.trim().isEmpty()) {
            b.setRtfPictMetaPrefix(pictPrefix.trim());
            mapped.add(RTFMetadata.RTF_PICT_META_PREFIX);
        }
    }

    private static void mapContentAndResourceFields(Metadata md, RtfMetadata.Builder b, Set<String> mapped) {
        MetadataUtils.mapStringField(md, "Content-Type", b::setContentType, mapped);
        MetadataUtils.mapStringField(md, "Content-Encoding", b::setContentEncoding, mapped);
        MetadataUtils.mapStringField(md, "Content-Length", b::setContentLength, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.RESOURCE_NAME_KEY, b::setResourceName, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.ORIGINAL_RESOURCE_NAME, b::setOriginalResourceName, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.EMBEDDED_RELATIONSHIP_ID, b::setEmbeddedRelationshipId, mapped);
    }

    private static void mapSecurityAndRevision(Metadata md, RtfMetadata.Builder b, Set<String> mapped) {
        // Use existing print date if present as print_time
        MetadataUtils.mapTimestampField(md, TikaCoreProperties.PRINT_DATE, b::setPrintTime, mapped);
        // Optional simple version/revision counts from generic fields if present
        MetadataUtils.mapIntField(md, TikaCoreProperties.VERSION_NUMBER, b::setVersion, mapped);
        MetadataUtils.mapIntField(md, TikaCoreProperties.VERSION_COUNT, b::setRevisionCount, mapped);
        // Encryption flag
        MetadataUtils.mapBooleanField(md, TikaCoreProperties.IS_ENCRYPTED, val -> {
            if (val) b.setPasswordProtected(true);
        }, mapped);
    }
}
